#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-results
adb install -r apks/systemblock-debug.apk
adb install -r apks/systemblock-debug-androidTest.apk
adb shell appops set com.obskyra.systemwideblockbrowser ACTIVATE_VPN allow
adb shell am instrument -w -r com.obskyra.systemwideblockbrowser.test/com.example.systemblock.SmokeInstrumentation | tee smoke-results/instrumentation.txt
adb shell screencap -p /sdcard/systemblock.png
adb pull /sdcard/systemblock.png smoke-results/dashboard.png
adb logcat -d -s AndroidRuntime System.out > smoke-results/logcat.txt
python3 - <<'CHECK'
from pathlib import Path
s=Path('smoke-results/instrumentation.txt').read_text()
assert 'PASS: blocked DNS' in s and not any(x in s for x in ['FAIL:', 'INSTRUMENTATION_FAILED', 'Process crashed']), s
CHECK
