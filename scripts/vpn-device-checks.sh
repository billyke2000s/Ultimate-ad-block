#!/usr/bin/env bash
set -euo pipefail
fixtures="${1:-/tmp/adblock-vpn-fixtures}"
mkdir -p smoke-results
adb shell mkdir -p /sdcard/Android/data/com.obskyra.systemwideblockbrowser/files
adb push "$fixtures/test-auth.txt" /sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/test-auth.txt >/dev/null
for spec in 'wireguard.conf WireGuard 10.89.0.1' 'openvpn-88.ovpn OpenVPN 10.88.0.1' 'openvpn-87.ovpn OpenVPN 10.87.0.1'; do
 read -r profile protocol ip <<< "$spec"
 adb push "$fixtures/$profile" "/sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/$profile" >/dev/null
 adb shell am instrument -w -r -e vpn_profile "$profile" -e protocol "$protocol" -e server_ip "$ip" com.obskyra.systemwideblockbrowser.test/com.example.systemblock.SmokeInstrumentation | tee "smoke-results/vpn-$profile.txt"
 python3 - "$profile" <<'PY'
import pathlib,sys
s=pathlib.Path('smoke-results/vpn-'+sys.argv[1]+'.txt').read_text()
assert 'PASS: controlled VPN endpoint checks' in s and 'FAIL:' not in s and 'Process crashed' not in s,s
PY
done
adb shell am instrument -w -r -e vpn_profile openvpn-88.ovpn -e protocol OpenVPN -e server_ip 10.88.0.1 -e bad_auth true com.obskyra.systemwideblockbrowser.test/com.example.systemblock.SmokeInstrumentation | tee smoke-results/vpn-bad-auth.txt
python3 - <<'PY'
from pathlib import Path
s=Path('smoke-results/vpn-bad-auth.txt').read_text();assert 'PASS: controlled VPN endpoint checks' in s and 'FAIL:' not in s,s
PY
# Delete runtime fixture secrets from the emulator after testing.
adb shell rm -f '/sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/*.conf' '/sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/*.ovpn' /sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/test-auth.txt
