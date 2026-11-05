// SPDX-License-Identifier: MIT
// One Android TUN, an in-process protocol engine and bounded DNS proxy.
package main

/*
#include "callbacks.h"
*/
import "C"
import (
	"context"
	"encoding/binary"
	"errors"
	"io"
	"net/netip"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	"unsafe"

	"golang.org/x/net/dns/dnsmessage"
	"golang.org/x/sys/unix"
	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/tun"
	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv6"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
)

// Datagram socket adapter; unlike an OS TUN this never performs TUN ioctls.
type packetDevice struct {
	f      *os.File
	events chan tun.Event
	mtu    int
	once   sync.Once
}

func (p *packetDevice) File() *os.File           { return p.f }
func (p *packetDevice) Name() (string, error)    { return "adblock", nil }
func (p *packetDevice) MTU() (int, error)        { return p.mtu, nil }
func (p *packetDevice) BatchSize() int           { return 1 }
func (p *packetDevice) Events() <-chan tun.Event { return p.events }
func (p *packetDevice) Read(b [][]byte, s []int, o int) (int, error) {
	n, e := p.f.Read(b[0][o:])
	s[0] = n
	if e != nil {
		return 0, e
	}
	return 1, nil
}
func (p *packetDevice) Write(b [][]byte, o int) (int, error) {
	for i, v := range b {
		if _, e := p.f.Write(v[o:]); e != nil {
			return i, e
		}
	}
	return len(b), nil
}
func (p *packetDevice) Close() error {
	p.once.Do(func() { unix.Shutdown(int(p.f.Fd()), unix.SHUT_RDWR); p.f.Close(); close(p.events) })
	return nil
}

// Protect every bind, including binds recreated by network changes, before Up sends.
type safeBind struct {
	conn.Bind
	protect C.uintptr_t
	ctx     C.uintptr_t
}

func (b *safeBind) Open(port uint16) ([]conn.ReceiveFunc, uint16, error) {
	f, p, e := b.Bind.Open(port)
	if e != nil {
		return f, p, e
	}
	peek, ok := b.Bind.(conn.PeekLookAtSocketFd)
	if !ok {
		b.Bind.Close()
		return nil, 0, errors.New("unsupported socket protection")
	}
	for _, fn := range []func() (int, error){peek.PeekLookAtSocketFd4, peek.PeekLookAtSocketFd6} {
		fd, err := fn()
		if err == nil && fd >= 0 && C.call_protect(b.protect, b.ctx, C.int(fd)) == 0 {
			b.Bind.Close()
			return nil, 0, errors.New("socket protection failed")
		}
	}
	return f, p, nil
}

var mu sync.Mutex
var next int64
var wgs = map[int64]*device.Device{}
var bridges = map[int64]*bridge{}

//export UBWgStart
func UBWgStart(fd C.int, mtu C.int, config *C.char, protect, ctx C.uintptr_t) C.longlong {
	p := &packetDevice{f: os.NewFile(uintptr(fd), "wg-packets"), events: make(chan tun.Event, 2), mtu: int(mtu)}
	b := &safeBind{Bind: conn.NewDefaultBind(), protect: protect, ctx: ctx}
	d := device.NewDevice(p, b, device.NewLogger(device.LogLevelSilent, ""))
	if e := d.IpcSet(C.GoString(config)); e != nil {
		d.Close()
		return -1
	}
	if e := d.Up(); e != nil {
		d.Close()
		return -2
	}
	p.events <- tun.EventUp
	id := atomic.AddInt64(&next, 1)
	mu.Lock()
	wgs[id] = d
	mu.Unlock()
	return C.longlong(id)
}

//export UBWgConfig
func UBWgConfig(id C.longlong) *C.char {
	mu.Lock()
	d := wgs[int64(id)]
	mu.Unlock()
	if d == nil {
		return C.CString("")
	}
	s, e := d.IpcGet()
	if e != nil {
		return C.CString("")
	}
	return C.CString(s)
}

//export UBWgRebind
func UBWgRebind(id C.longlong) C.int {
	mu.Lock()
	d := wgs[int64(id)]
	mu.Unlock()
	if d == nil {
		return 0
	}
	if d.BindUpdate() != nil {
		return 0
	}
	d.SendKeepalivesToPeersWithCurrentKeypair()
	return 1
}

//export UBWgStop
func UBWgStop(id C.longlong) {
	mu.Lock()
	d := wgs[int64(id)]
	delete(wgs, int64(id))
	mu.Unlock()
	if d != nil {
		d.Close()
	}
}

// Each stack has a bounded channel; packets are copied and reference counts released.
type ipStack struct {
	s      *stack.Stack
	ep     *channel.Endpoint
	cancel context.CancelFunc
}

func newStack(ips []netip.Addr, mtu int, output func([]byte)) (*ipStack, error) {
	s := stack.New(stack.Options{NetworkProtocols: []stack.NetworkProtocolFactory{ipv4.NewProtocol, ipv6.NewProtocol}, TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol, udp.NewProtocol}})
	ep := channel.New(128, uint32(mtu), "")
	if e := s.CreateNIC(1, ep); e != nil {
		s.Close()
		return nil, errors.New(e.String())
	}
	for _, ip := range ips {
		proto := ipv4.ProtocolNumber
		if ip.Is6() {
			proto = ipv6.ProtocolNumber
		}
		if e := s.AddProtocolAddress(1, tcpip.ProtocolAddress{Protocol: proto, AddressWithPrefix: tcpip.AddrFromSlice(ip.AsSlice()).WithPrefix()}, stack.AddressProperties{}); e != nil {
			s.Close()
			return nil, errors.New(e.String())
		}
	}
	s.SetRouteTable([]tcpip.Route{{Destination: header.IPv4EmptySubnet, NIC: 1}, {Destination: header.IPv6EmptySubnet, NIC: 1}})
	ctx, cancel := context.WithCancel(context.Background())
	v := &ipStack{s, ep, cancel}
	go func() {
		for {
			p := ep.ReadContext(ctx)
			if p == nil {
				return
			}
			view := p.ToView()
			data := append([]byte(nil), view.AsSlice()...)
			view.Release()
			p.DecRef()
			output(data)
		}
	}()
	return v, nil
}
func (s *ipStack) inject(p []byte) {
	proto := ipv4.ProtocolNumber
	if p[0]>>4 == 6 {
		proto = ipv6.ProtocolNumber
	}
	b := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(p)})
	defer b.DecRef()
	s.ep.InjectInbound(proto, b)
}
func (s *ipStack) close() { s.cancel(); s.s.Close(); s.ep.Close() }
func addr(ip netip.Addr, port uint16) (tcpip.FullAddress, tcpip.NetworkProtocolNumber) {
	proto := ipv4.ProtocolNumber
	if ip.Is6() {
		proto = ipv6.ProtocolNumber
	}
	return tcpip.FullAddress{NIC: 1, Addr: tcpip.AddrFromSlice(ip.AsSlice()), Port: port}, proto
}

var virtual4 = netip.MustParseAddr("10.111.0.2")
var virtual6 = netip.MustParseAddr("fd11:111::2")

type bridge struct {
	tun, engine    *os.File
	dns, upstream  *ipStack
	servers        []netip.Addr
	ctx            C.uintptr_t
	filter, record C.uintptr_t
	stop           chan struct{}
	once           sync.Once
	jobs           chan struct{}
	portsMu        sync.Mutex
	ports          map[uint16]bool
	nextPort       uint16
	rx, tx         atomic.Int64
}

func ips(s string) []netip.Addr {
	var out []netip.Addr
	for _, v := range strings.Split(s, ",") {
		a, e := netip.ParseAddr(strings.TrimSpace(strings.Split(v, "/")[0]))
		if e == nil {
			out = append(out, a)
		}
	}
	return out
}

//export UBBridgeStart
func UBBridgeStart(tfd, efd, mtu C.int, addresses, dns *C.char, filter, record, ctx C.uintptr_t) C.longlong {
	b := &bridge{tun: os.NewFile(uintptr(tfd), "android-tun"), engine: os.NewFile(uintptr(efd), "engine-packets"), servers: ips(C.GoString(dns)), ctx: ctx, filter: filter, record: record, stop: make(chan struct{}), jobs: make(chan struct{}, 32), ports: map[uint16]bool{}, nextPort: 61000}
	var e error
	b.upstream, e = newStack(ips(C.GoString(addresses)), int(mtu), func(p []byte) { b.engine.Write(p) })
	if e != nil {
		b.close()
		return -1
	}
	b.dns, e = newStack([]netip.Addr{virtual4, virtual6}, int(mtu), func(p []byte) { b.tun.Write(p) })
	if e != nil {
		b.close()
		return -1
	}
	b.dns.s.SetPromiscuousMode(1, true)
	b.dns.s.SetSpoofing(1, true)
	for _, ip := range []netip.Addr{netip.IPv4Unspecified(), netip.IPv6Unspecified()} {
		if e = b.listen(ip); e != nil {
			b.close()
			return -1
		}
	}
	id := atomic.AddInt64(&next, 1)
	mu.Lock()
	bridges[id] = b
	mu.Unlock()
	go b.fromTun()
	go b.fromEngine()
	return C.longlong(id)
}
func (b *bridge) close() {
	b.once.Do(func() {
		close(b.stop)
		for _, f := range []*os.File{b.tun, b.engine} {
			if f != nil {
				unix.Shutdown(int(f.Fd()), unix.SHUT_RDWR)
				f.Close()
			}
		}
		if b.dns != nil {
			b.dns.close()
		}
		if b.upstream != nil {
			b.upstream.close()
		}
	})
}

//export UBBridgeStop
func UBBridgeStop(id C.longlong) {
	mu.Lock()
	b := bridges[int64(id)]
	delete(bridges, int64(id))
	mu.Unlock()
	if b != nil {
		b.close()
	}
}

//export UBBridgeBytes
func UBBridgeBytes(id C.longlong, receive C.int) C.longlong {
	mu.Lock()
	b := bridges[int64(id)]
	mu.Unlock()
	if b == nil {
		return 0
	}
	if receive != 0 {
		return C.longlong(b.rx.Load())
	}
	return C.longlong(b.tx.Load())
}

// Only non-fragmented base IP TCP/UDP headers qualify for local DNS dispatch.
// All other packets remain raw protocol traffic. IPv6 extension headers never bypass the tunnel.
func packet(p []byte) (netip.Addr, uint8, int, bool) {
	if len(p) < 20 {
		return netip.Addr{}, 0, 0, false
	}
	switch p[0] >> 4 {
	case 4:
		n := int(p[0]&15) * 4
		if n < 20 || len(p) < n || int(binary.BigEndian.Uint16(p[2:])) != len(p) || binary.BigEndian.Uint16(p[6:])&0x3fff != 0 {
			return netip.Addr{}, 0, 0, false
		}
		return netip.AddrFrom4([4]byte(p[16:20])), p[9], n, true
	case 6:
		if len(p) < 40 || int(binary.BigEndian.Uint16(p[4:]))+40 != len(p) {
			return netip.Addr{}, 0, 0, false
		}
		return netip.AddrFrom16([16]byte(p[24:40])), p[6], 40, true
	}
	return netip.Addr{}, 0, 0, false
}
func (b *bridge) fromTun() {
	defer b.close()
	p := make([]byte, 65535)
	for {
		n, e := b.tun.Read(p)
		if e != nil {
			return
		}
		v := p[:n]
		ip, proto, o, ok := packet(v)
		if ok && (proto == 6 || proto == 17) && len(v) >= o+4 && binary.BigEndian.Uint16(v[o+2:]) == 53 {
			b.dns.inject(v)
			continue
		}
		if ok && (ip == virtual4 || ip == virtual6) {
			continue
		}
		// Internally reserved DNS source ports cannot collide with application flows.
		if ok && (proto == 6 || proto == 17) && len(v) >= o+4 {
			port := binary.BigEndian.Uint16(v[o:])
			if port >= 61000 && port < 62000 {
				continue
			}
		}
		b.tx.Add(int64(n))
		if _, e = b.engine.Write(v); e != nil {
			return
		}
	}
}
func (b *bridge) fromEngine() {
	defer b.close()
	p := make([]byte, 65535)
	for {
		n, e := b.engine.Read(p)
		if e != nil {
			return
		}
		v := p[:n]
		_, proto, o, ok := packet(v)
		if ok && (proto == 6 || proto == 17) && len(v) >= o+4 {
			port := binary.BigEndian.Uint16(v[o+2:])
			if port >= 61000 && port < 62000 {
				b.upstream.inject(v)
				continue
			}
		}
		b.rx.Add(int64(n))
		if _, e = b.tun.Write(v); e != nil {
			return
		}
	}
}
func (b *bridge) acquire() bool {
	select {
	case b.jobs <- struct{}{}:
		return true
	default:
		return false
	}
}
func (b *bridge) listen(ip netip.Addr) error {
	a, p := addr(ip, 53)
	u, e := gonet.DialUDP(b.dns.s, &a, nil, p)
	if e != nil {
		return e
	}
	go func() {
		defer u.Close()
		data := make([]byte, 65535)
		for {
			n, remote, e := u.ReadFrom(data)
			if e != nil {
				return
			}
			q := append([]byte(nil), data[:n]...)
			if !b.acquire() {
				continue
			}
			go func() {
				defer func() { <-b.jobs }()
				answer := b.answer(q, true)
				if answer != nil {
					u.WriteTo(answer, remote)
				}
			}()
		}
	}()
	l, e := gonet.ListenTCP(b.dns.s, a, p)
	if e != nil {
		return e
	}
	go func() {
		defer l.Close()
		for {
			c, e := l.Accept()
			if e != nil {
				return
			}
			if !b.acquire() {
				c.Close()
				continue
			}
			go func() {
				defer c.Close()
				defer func() { <-b.jobs }()
				for {
					c.SetDeadline(time.Now().Add(10 * time.Second))
					var size [2]byte
					if _, e := io.ReadFull(c, size[:]); e != nil {
						return
					}
					n := binary.BigEndian.Uint16(size[:])
					if n < 12 {
						return
					}
					q := make([]byte, int(n))
					if _, e := io.ReadFull(c, q); e != nil {
						return
					}
					a := b.answer(q, false)
					if a == nil {
						return
					}
					binary.BigEndian.PutUint16(size[:], uint16(len(a)))
					if _, e = c.Write(append(size[:], a...)); e != nil {
						return
					}
				}
			}()
		}
	}()
	return nil
}
func question(q []byte) (dnsmessage.Header, dnsmessage.Question, error) {
	var p dnsmessage.Parser
	h, e := p.Start(q)
	if e != nil || h.Response {
		return h, dnsmessage.Question{}, errors.New("query header")
	}
	v, e := p.Question()
	if e != nil {
		return h, v, e
	}
	if _, e = p.Question(); e != dnsmessage.ErrSectionDone {
		return h, v, errors.New("query count")
	}
	return h, v, nil
}
func errorAnswer(q []byte, rcode dnsmessage.RCode, truncated bool) []byte {
	h, v, e := question(q)
	if e != nil {
		return nil
	}
	h.Response = true
	h.Authoritative = false
	h.RecursionAvailable = true
	h.RCode = rcode
	h.Truncated = truncated
	b := dnsmessage.NewBuilder(nil, h)
	b.StartQuestions()
	b.Question(v)
	a, _ := b.Finish()
	return a
}
func (b *bridge) answer(q []byte, isUDP bool) []byte {
	h, v, e := question(q)
	if e != nil {
		return nil
	}
	host := strings.TrimSuffix(strings.ToLower(v.Name.String()), ".")
	ch := C.CString(host)
	defer C.free(unsafe.Pointer(ch))
	if C.call_filter(b.filter, b.ctx, ch) != 0 {
		C.call_record(b.record, b.ctx, ch, 1)
		return errorAnswer(q, dnsmessage.RCodeNameError, false)
	}
	answer, e := b.resolve(q)
	if e != nil {
		C.call_record(b.record, b.ctx, ch, -1)
		return errorAnswer(q, dnsmessage.RCodeServerFailure, false)
	}
	var parser dnsmessage.Parser
	ah, e := parser.Start(answer)
	av, qe := parser.Question()
	if e != nil || qe != nil || !ah.Response || ah.ID != h.ID || av != v {
		C.call_record(b.record, b.ctx, ch, -1)
		return errorAnswer(q, dnsmessage.RCodeServerFailure, false)
	}
	outcome := 0
	if ah.RCode == dnsmessage.RCodeServerFailure {
		outcome = -1
	}
	C.call_record(b.record, b.ctx, ch, C.int(outcome))
	if isUDP {
		limit := 512 // RFC 6891: parse OPT size, capped to avoid IP fragmentation.
		for {
			if _, e := parser.Question(); e != nil {
				break
			}
		}
		parser.SkipAllAnswers()
		parser.SkipAllAuthorities()
		if rs, e := parser.AllAdditionals(); e == nil {
			for _, r := range rs {
				if r.Header.Type == dnsmessage.TypeOPT {
					limit = int(r.Header.Class)
					if limit < 512 {
						limit = 512
					}
					if limit > 1232 {
						limit = 1232
					}
				}
			}
		}
		if len(answer) > limit {
			return errorAnswer(q, ah.RCode, true)
		}
	}
	return answer
}
func (b *bridge) resolve(q []byte) ([]byte, error) {
	// TCP upstream also handles responses that exceed the client's UDP budget.
	for _, server := range b.servers {
		b.portsMu.Lock()
		var port uint16
		for i := 0; i < 1000; i++ {
			candidate := b.nextPort
			b.nextPort++
			if b.nextPort >= 62000 {
				b.nextPort = 61000
			}
			if !b.ports[candidate] {
				port = candidate
				b.ports[port] = true
				break
			}
		}
		b.portsMu.Unlock()
		if port == 0 {
			return nil, errors.New("DNS busy")
		}
		a, p := addr(server, 53)
		local := tcpip.FullAddress{NIC: 1, Port: port}
		ctx, cancel := context.WithTimeout(context.Background(), 6*time.Second)
		c, e := gonet.DialTCPWithBind(ctx, b.upstream.s, local, a, p)
		var answer []byte
		if e == nil {
			c.SetDeadline(time.Now().Add(6 * time.Second))
			var size [2]byte
			binary.BigEndian.PutUint16(size[:], uint16(len(q)))
			_, e = c.Write(append(size[:], q...))
			if e == nil {
				_, e = io.ReadFull(c, size[:])
			}
			if e == nil {
				n := binary.BigEndian.Uint16(size[:])
				if n < 12 {
					e = errors.New("short DNS")
				} else {
					answer = make([]byte, int(n))
					_, e = io.ReadFull(c, answer)
				}
			}
			c.Close()
		}
		cancel()
		b.portsMu.Lock()
		delete(b.ports, port)
		b.portsMu.Unlock()
		if e == nil {
			return answer, nil
		}
	}
	return nil, errors.New("VPN DNS unavailable")
}
func main() {}
