#!/usr/bin/env bash
# Ephemeral controlled VPN servers for GitHub-hosted Linux runners only.
set -euo pipefail
out="$(realpath "${1:-/tmp/adblock-vpn-fixtures}")"
mkdir -p "$out"
chmod 700 "$out"
sudo apt-get update -qq
sudo apt-get install -y -qq openvpn wireguard-tools dnsmasq-base
cd "$out"
openssl req -x509 -newkey rsa:2048 -nodes -keyout ca.key -out ca.crt -days 2 -subj /CN=AdBlock-CI-CA 2>/dev/null
for name in server client; do
 openssl req -new -newkey rsa:2048 -nodes -keyout "$name.key" -out "$name.csr" -subj "/CN=adblock-ci-$name" 2>/dev/null
 printf 'extendedKeyUsage=%sAuth\n' "$name" > "$name.ext"
 openssl x509 -req -in "$name.csr" -CA ca.crt -CAkey ca.key -CAcreateserial -out "$name.crt" -days 2 -extfile "$name.ext" 2>/dev/null
done
user="test-$(openssl rand -hex 8)"
password="$(openssl rand -hex 24)"
printf '%s\n%s\n' "$user" "$password" > test-auth.txt
cat > auth.sh <<EOF_AUTH
#!/usr/bin/env bash
mapfile -t supplied < "\$1"
[[ "\${supplied[0]}" = '$user' && "\${supplied[1]}" = '$password' ]]
EOF_AUTH
chmod 700 auth.sh
sudo sysctl -w net.ipv4.ip_forward=1 >/dev/null
sudo sysctl -w net.ipv6.conf.all.forwarding=1 >/dev/null
for spec in 'udp 1194 88' 'tcp-server 1195 87'; do
 read -r proto port subnet <<< "$spec"
 cat > "server-$subnet.conf" <<EOF_CONF
local 127.0.0.1
port $port
proto $proto
dev ovpn$subnet
dev-type tun
topology subnet
server 10.$subnet.0.0 255.255.255.0
server-ipv6 fd$subnet::/64
ca $out/ca.crt
cert $out/server.crt
key $out/server.key
dh none
tls-version-min 1.2
data-ciphers AES-256-GCM:AES-128-GCM
keepalive 2 10
push "redirect-gateway def1"
push "route-ipv6 ::/0"
push "dhcp-option DNS 10.$subnet.0.1"
script-security 2
auth-user-pass-verify $out/auth.sh via-file
verb 3
log $out/server-$subnet.log
EOF_CONF
 if [ "$subnet" = 88 ]; then printf 'verify-client-cert none\n' >> "server-$subnet.conf"; fi
 sudo openvpn --config "$out/server-$subnet.conf" --daemon
 {
  printf 'client\ndev tun\nproto %s\nremote 10.0.2.2 %s\nnobind\nauth-user-pass\nremote-cert-tls server\nverify-x509-name adblock-ci-server name\n' "${proto/server/client}" "$port"
  for item in ca cert key; do
   case "$item" in ca) file=ca.crt;; cert) file=client.crt;; key) file=client.key;; esac
   printf '<%s>\n' "$item";cat "$file";printf '</%s>\n' "$item"
  done
 } > "openvpn-$subnet.ovpn"
 sudo iptables -t nat -A POSTROUTING -s "10.$subnet.0.0/24" ! -d "10.$subnet.0.0/24" -j MASQUERADE
done
# A separate endpoint exercises certificate-only authentication.
sed -e 's/1194/1196/g' -e 's/88/86/g' -e '/auth-user-pass-verify/d' -e '/script-security/d' -e '/verify-client-cert/d' server-88.conf > server-86.conf
sudo openvpn --config "$out/server-86.conf" --daemon
sed -e 's/1194/1196/g' -e '/^auth-user-pass$/d' openvpn-88.ovpn > openvpn-86.ovpn
sudo iptables -t nat -A POSTROUTING -s 10.86.0.0/24 ! -d 10.86.0.0/24 -j MASQUERADE
python3 - <<'PY_CONFIG'
from pathlib import Path
import re
p=Path('openvpn-88.ovpn')
p.write_text(re.sub(r'<(cert|key)>.*?</\1>\s*','',p.read_text(),flags=re.S))
Path('openvpn-bad-cert.ovpn').write_text(p.read_text().replace('verify-x509-name adblock-ci-server name','verify-x509-name deliberately-wrong-ci-name name'))
PY_CONFIG
wg genkey > wg-server.key
wg genkey > wg-client.key
wg pubkey < wg-server.key > wg-server.pub
wg pubkey < wg-client.key > wg-client.pub
sudo ip link add ubwg type wireguard
sudo ip address add 10.89.0.1/24 dev ubwg
sudo ip address add fd89::1/64 dev ubwg
sudo wg set ubwg listen-port 51820 private-key "$out/wg-server.key" peer "$(cat wg-client.pub)" allowed-ips 10.89.0.2/32,fd89::2/128
sudo ip link set ubwg up
sudo iptables -t nat -A POSTROUTING -s 10.89.0.0/24 ! -d 10.89.0.0/24 -j MASQUERADE
cat > wireguard.conf <<EOF_WG
[Interface]
PrivateKey = $(cat wg-client.key)
Address = 10.89.0.2/32, fd89::2/128
DNS = 10.89.0.1
MTU = 1280
[Peer]
PublicKey = $(cat wg-server.pub)
Endpoint = 10.0.2.2:51820
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 2
EOF_WG
# Restrict the DNS fixture to VPN interfaces. Real Internet lookups still forward normally.
for i in $(seq 1 50); do if ip link show ovpn87 >/dev/null 2>&1 && ip link show ovpn88 >/dev/null 2>&1 && ip link show ovpn86 >/dev/null 2>&1; then break; fi;sleep .1;done
sudo dnsmasq --no-daemon --bind-dynamic --interface=ubwg --interface=ovpn87 --interface=ovpn88 --interface=ovpn86 --except-interface=lo --no-resolv --server=1.1.1.1 --address=/doubleclick.net/10.89.0.1 --log-facility="$out/dnsmasq.log" &
printf '%s\n' "$!" > dnsmasq.pid
cat > endpoint.py <<'PY'
import http.server,socketserver,threading,socket
class Handler(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  body=b'VPN-ENDPOINT '+self.client_address[0].encode();self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*a): pass
for ip in ['10.86.0.1','10.87.0.1','10.88.0.1','10.89.0.1']:
 threading.Thread(target=http.server.ThreadingHTTPServer((ip,8080),Handler).serve_forever,daemon=True).start()
class IPv6Server(http.server.ThreadingHTTPServer):
 address_family=socket.AF_INET6
for ip in ['fd86::1','fd87::1','fd88::1','fd89::1']:
 def serve(ip=ip):
  import time
  for attempt in range(50):
   try: server=IPv6Server((ip,8080),Handler);break
   except OSError: time.sleep(.1)
  else: raise RuntimeError('IPv6 fixture did not bind')
  server.serve_forever()
 threading.Thread(target=serve,daemon=True).start()
threading.Event().wait()
PY
sudo python3 endpoint.py > endpoint.log 2>&1 &
printf '%s\n' "$!" > endpoint.pid
# Never upload this directory as an artifact; it contains ephemeral keys.
