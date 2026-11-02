#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-results
collect_evidence() {
  adb logcat -d > smoke-results/logcat.txt || true
  adb shell dumpsys connectivity > smoke-results/connectivity.txt || true
  adb shell dumpsys netpolicy > smoke-results/netpolicy.txt || true
  adb shell ip rule > smoke-results/ip-rule.txt || true
  adb shell ip route show table all > smoke-results/ip-route.txt || true
}
trap collect_evidence EXIT
adb install -r -t apks/dnsprobe-debug.apk
adb install -r apks/systemblock-debug.apk
adb install -r apks/systemblock-debug-androidTest.apk
adb shell settings put global private_dns_mode off
adb shell appops set com.obskyra.systemwideblockbrowser ACTIVATE_VPN allow
adb shell am instrument -w -r com.obskyra.systemwideblockbrowser.test/com.example.systemblock.SmokeInstrumentation | tee smoke-results/instrumentation.txt
adb pull /sdcard/Android/data/com.obskyra.systemwideblockbrowser/files/dashboard.png smoke-results/dashboard.png || true
adb logcat -d -s AndroidRuntime System.out SystemBlock > smoke-results/logcat.txt
python3 - <<'CHECK'
from pathlib import Path
s=Path('smoke-results/instrumentation.txt').read_text()
assert 'PASS: blocked DNS' in s and not any(x in s for x in ['FAIL:', 'INSTRUMENTATION_FAILED', 'Process crashed']), s
CHECK
