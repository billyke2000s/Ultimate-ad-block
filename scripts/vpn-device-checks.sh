#!/usr/bin/env bash
set -euo pipefail
fixtures="${1:-/tmp/adblock-vpn-fixtures}"
mkdir -p smoke-results
failed=0
collect_vpn_evidence(){
 adb logcat -d > smoke-results/vpn-logcat.txt || true
 adb shell dumpsys connectivity > smoke-results/vpn-connectivity.txt || true
 sudo iptables -nvL FORWARD > smoke-results/server-forward.txt || true
 sudo iptables -t nat -nvL POSTROUTING > smoke-results/server-nat.txt || true
 sudo wg show ubwg transfer > smoke-results/server-wireguard-transfer.txt || true
}
trap collect_vpn_evidence EXIT
assert_report(){
 if python3 - "$1" <<'VERIFY'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
assert 'PASS: controlled VPN endpoint checks' in s and not any(x in s for x in ['FAIL:', 'INSTRUMENTATION_FAILED', 'Process crashed']),s
VERIFY
 then :;else failed=$((failed+1));fi
}
adb shell mkdir -p /sdcard/Android/data/com.obskyra.systemwideblockbrowser/files
adb push "$fixtures/test-auth.txt" /sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/test-auth.txt >/dev/null
for spec in 'wireguard.conf WireGuard 10.89.0.1' 'openvpn-88.ovpn OpenVPN 10.88.0.1' 'openvpn-87.ovpn OpenVPN 10.87.0.1' 'openvpn-86.ovpn OpenVPN 10.86.0.1'; do
 read -r profile protocol ip <<< "$spec"
 adb push "$fixtures/$profile" "/sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/$profile" >/dev/null
 report="smoke-results/vpn-$profile.txt"
 adb shell am instrument -w -r -e vpn_profile "$profile" -e protocol "$protocol" -e server_ip "$ip" com.obskyra.systemwideblockbrowser.test/com.example.systemblock.SmokeInstrumentation | tee "$report"
 assert_report "$report"
done
for spec in 'openvpn-88.ovpn bad_auth' 'openvpn-bad-cert.ovpn bad_cert'; do
 read -r profile flag <<< "$spec"
 adb push "$fixtures/$profile" "/sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/$profile" >/dev/null
 report="smoke-results/vpn-$flag.txt"
 adb shell am instrument -w -r -e vpn_profile "$profile" -e protocol OpenVPN -e server_ip 10.88.0.1 -e "$flag" true com.obskyra.systemwideblockbrowser.test/com.example.systemblock.SmokeInstrumentation | tee "$report"
 assert_report "$report"
done
# Delete runtime fixture secrets from the emulator after testing.
adb shell rm -f '/sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/*.conf' '/sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/*.ovpn' /sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/test-auth.txt
if [ "$failed" -ne 0 ]; then printf '%s controlled VPN cases failed\n' "$failed";exit 1;fi
