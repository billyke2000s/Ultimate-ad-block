package main

import (
	"context"
	"crypto/ecdh"
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"os"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"golang.org/x/net/dns/dnsmessage"
	"golang.org/x/sys/unix"
	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/tun"
	"golang.zx2c4.com/wireguard/tun/netstack"
)

// Match the Android wrapper bind: Android handover replaces Linux netlink tracking.
type androidStyleBind struct{ conn.Bind }

func sockpair(t *testing.T) (*os.File, *os.File) {
	t.Helper()
	fds, e := unix.Socketpair(unix.AF_UNIX, unix.SOCK_DGRAM|unix.SOCK_CLOEXEC, 0)
	if e != nil {
		t.Fatal(e)
	}
	return packetFile(fds[0], "test-a"), packetFile(fds[1], "test-b")
}
func pumpStack(t *testing.T, dev tun.Device, fd *os.File) {
	t.Helper()
	go func() {
		p := make([]byte, 65535)
		for {
			n, e := fd.Read(p)
			if e != nil {
				return
			}
			if _, e = dev.Write([][]byte{p[:n]}, 0); e != nil {
				return
			}
		}
	}()
	go func() {
		p := [][]byte{make([]byte, 65535)}
		sizes := []int{0}
		for {
			_, e := dev.Read(p, sizes, 0)
			if e != nil {
				return
			}
			if _, e = fd.Write(p[0][:sizes[0]]); e != nil {
				return
			}
		}
	}()
}
func queryBytes(t *testing.T, host string) []byte {
	t.Helper()
	n, e := dnsmessage.NewName(host + ".")
	if e != nil {
		t.Fatal(e)
	}
	b := dnsmessage.NewBuilder(nil, dnsmessage.Header{ID: 42, RecursionDesired: true})
	b.StartQuestions()
	b.Question(dnsmessage.Question{Name: n, Type: dnsmessage.TypeA, Class: dnsmessage.ClassINET})
	q, e := b.Finish()
	if e != nil {
		t.Fatal(e)
	}
	return q
}
func TestWireGuardBridgeDNSAndTraffic(t *testing.T) {
	// Actual localhost WireGuard peers, no external credentials or Internet needed.
	serverTun, serverNet, e := netstack.CreateNetTUN(ips("10.7.0.1,fd07::1"), nil, 1280)
	if e != nil {
		t.Fatal(e)
	}
	server := device.NewDevice(serverTun, &androidStyleBind{conn.NewDefaultBind()}, device.NewLogger(device.LogLevelSilent, ""))
	defer server.Close()
	a, b := sockpair(t)
	client := device.NewDevice(&packetDevice{f: b, events: make(chan tun.Event, 2), mtu: 1280}, &androidStyleBind{conn.NewDefaultBind()}, device.NewLogger(device.LogLevelSilent, ""))
	defer client.Close()
	key1, _ := ecdh.X25519().GenerateKey(rand.Reader)
	key2, _ := ecdh.X25519().GenerateKey(rand.Reader)
	portSock, e := net.ListenPacket("udp4", "127.0.0.1:0")
	if e != nil {
		t.Fatal(e)
	}
	port := portSock.LocalAddr().(*net.UDPAddr).Port
	portSock.Close()
	config := fmt.Sprintf("private_key=%s\nlisten_port=%d\npublic_key=%s\nallowed_ip=10.7.0.2/32\nallowed_ip=fd07::2/128\n", hex.EncodeToString(key1.Bytes()), port, hex.EncodeToString(key2.PublicKey().Bytes()))
	if e = server.IpcSet(config); e != nil {
		t.Fatal(e)
	}
	if e = server.Up(); e != nil {
		t.Fatal(e)
	}
	config = fmt.Sprintf("private_key=%s\npublic_key=%s\nendpoint=127.0.0.1:%d\nallowed_ip=0.0.0.0/0\nallowed_ip=::/0\npersistent_keepalive_interval=1\n", hex.EncodeToString(key2.Bytes()), hex.EncodeToString(key1.PublicKey().Bytes()), port)
	if e = client.IpcSet(config); e != nil {
		t.Fatal(e)
	}
	if e = client.Up(); e != nil {
		t.Fatal(e)
	}
	android, application := sockpair(t)
	var enabled atomic.Bool
	enabled.Store(true)
	var blocked, allowed atomic.Int64
	br := &bridge{tun: android, engine: a, servers: ips("10.7.0.1"), stop: make(chan struct{}), jobs: make(chan struct{}, 32), ports: map[uint16]bool{}, nextPort: 61000, assess: func(host string) bool { return enabled.Load() && host == "ads.test" }, observe: func(_ string, result int) {
		if result == 1 {
			blocked.Add(1)
		} else if result == 0 {
			allowed.Add(1)
		}
	}}
	br.upstream, e = newStack(ips("10.7.0.2,fd07::2"), 1280, func(p []byte) { a.Write(p) })
	if e != nil {
		t.Fatal(e)
	}
	br.dns, e = newStack(ips("10.111.0.2,fd11:111::2"), 1280, func(p []byte) { android.Write(p) })
	if e != nil {
		t.Fatal(e)
	}
	br.dns.s.SetPromiscuousMode(1, true)
	br.dns.s.SetSpoofing(1, true)
	br.udpForwarder()
	for _, ip := range ips("::") {
		if e = br.listen(ip); e != nil {
			t.Fatal(e)
		}
	}
	br.launch(br.fromTun)
	br.launch(br.fromEngine)
	defer func() {
		br.close()
		application.Close()
		done := make(chan struct{})
		go func() { br.wait.Wait(); close(done) }()
		select {
		case <-done:
		case <-time.After(12 * time.Second):
			t.Error("bridge shutdown stranded workers")
		}
	}()
	appTun, appNet, e := netstack.CreateNetTUN(ips("10.7.0.2,fd07::2"), nil, 1280)
	if e != nil {
		t.Fatal(e)
	}
	defer appTun.Close()
	pumpStack(t, appTun, application)
	dnsListener, e := serverNet.ListenTCP(&net.TCPAddr{IP: net.ParseIP("10.7.0.1"), Port: 53})
	if e != nil {
		t.Fatal(e)
	}
	defer dnsListener.Close()
	go func() {
		for {
			c, e := dnsListener.Accept()
			if e != nil {
				return
			}
			go func() {
				defer c.Close()
				var size [2]byte
				if _, e := io.ReadFull(c, size[:]); e != nil {
					return
				}
				q := make([]byte, binary.BigEndian.Uint16(size[:]))
				if _, e := io.ReadFull(c, q); e != nil {
					return
				}
				reply := errorAnswer(q, dnsmessage.RCodeSuccess, false)
				binary.BigEndian.PutUint16(size[:], uint16(len(reply)))
				c.Write(append(size[:], reply...))
			}()
		}
	}()
	for _, family := range []string{"10.111.0.2", "fd11:111::2", "10.8.0.53"} {
		for _, transport := range []string{"udp", "tcp"} {
			for _, host := range []string{"ads.test", "allowed.test"} {
				t.Run(family+"/"+transport+"/"+host, func(t *testing.T) {
					ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
					defer cancel()
					c, e := appNet.DialContext(ctx, transport, net.JoinHostPort(family, "53"))
					if e != nil {
						t.Fatal(e)
					}
					defer c.Close()
					c.SetDeadline(time.Now().Add(8 * time.Second))
					q := queryBytes(t, host)
					if transport == "tcp" {
						var n [2]byte
						binary.BigEndian.PutUint16(n[:], uint16(len(q)))
						q = append(n[:], q...)
					}
					if _, e = c.Write(q); e != nil {
						t.Fatal(e)
					}
					reply := make([]byte, 65535)
					var n int
					if transport == "tcp" {
						var size [2]byte
						if _, e = io.ReadFull(c, size[:]); e != nil {
							t.Fatal(e)
						}
						n = int(binary.BigEndian.Uint16(size[:]))
						_, e = io.ReadFull(c, reply[:n])
					} else {
						n, e = c.Read(reply)
					}
					if e != nil {
						t.Fatal(e)
					}
					var p dnsmessage.Parser
					h, e := p.Start(reply[:n])
					if e != nil {
						t.Fatal(e)
					}
					want := dnsmessage.RCodeSuccess
					if host == "ads.test" {
						want = dnsmessage.RCodeNameError
					}
					if h.RCode != want {
						t.Fatalf("rcode=%v want=%v", h.RCode, want)
					}
				})
			}
		}
	}
	enabled.Store(false)
	c, e := appNet.DialContext(context.Background(), "udp", "10.111.0.2:53")
	if e != nil {
		t.Fatal(e)
	}
	c.SetDeadline(time.Now().Add(8 * time.Second))
	c.Write(queryBytes(t, "ads.test"))
	reply := make([]byte, 512)
	n, e := c.Read(reply)
	c.Close()
	if e != nil {
		t.Fatal(e)
	}
	var p dnsmessage.Parser
	h, e := p.Start(reply[:n])
	if e != nil || h.RCode != 0 {
		t.Fatalf("disabled blocking failed: %v %v", h, e)
	}
	// Arbitrary TCP data traverses the encrypted peer in both address families.
	for _, ip := range []string{"10.7.0.1", "fd07::1"} {
		ln, e := serverNet.ListenTCP(&net.TCPAddr{IP: net.ParseIP(ip), Port: 8080})
		if e != nil {
			t.Fatal(e)
		}
		go func() {
			c, e := ln.Accept()
			if e == nil {
				defer c.Close()
				io.Copy(c, c)
			}
		}()
		ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
		c, e := appNet.DialContext(ctx, "tcp", net.JoinHostPort(ip, "8080"))
		cancel()
		if e != nil {
			t.Fatal(e)
		}
		c.SetDeadline(time.Now().Add(5 * time.Second))
		c.Write([]byte("encrypted round trip"))
		got := make([]byte, 20)
		_, e = io.ReadFull(c, got)
		c.Close()
		ln.Close()
		if e != nil || string(got) != "encrypted round trip" {
			t.Fatalf("ordinary TCP: %q %v", got, e)
		}
	}
	if blocked.Load() != 6 || allowed.Load() != 7 {
		t.Fatalf("counters blocked=%d allowed=%d", blocked.Load(), allowed.Load())
	}
	cfg, e := client.IpcGet()
	if e != nil || !stringsContainsHandshake(cfg) {
		t.Fatal("no real WireGuard handshake")
	}
	if br.rx.Load() == 0 || br.tx.Load() == 0 {
		t.Fatal("no actual forwarded traffic counters")
	}
}
func stringsContainsHandshake(s string) bool {
	for _, line := range strings.Split(s, "\n") {
		if strings.HasPrefix(line, "last_handshake_time_sec=") && line != "last_handshake_time_sec=0" {
			return true
		}
	}
	return false
}
func TestMalformedDNS(t *testing.T) {
	for n := 0; n < 32; n++ {
		data := make([]byte, n)
		if a := errorAnswer(data, dnsmessage.RCodeNameError, false); a != nil {
			t.Fatalf("accepted invalid query of size %d", n)
		}
	}
}
