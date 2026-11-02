#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-results
adb install -r apks/systemblock-debug.apk
adb install -r apks/systemblock-debug-androidTest.apk
adb shell appops set com.obskyra.systemwideblockbrowser ACTIVATE_VPN allow
adb shell am instrument -w -r com.obskyra.systemwideblockbrowser.test/com.example.systemblock.SmokeInstrumentation | tee smoke-results/instrumentation.txt
adb shell screencap -p /sdcard/systemblock.png
adb pull /sdcard/systemblock.png smoke-results/dashboard.png
rg 'PASS: blocked DNS' smoke-results/instrumentation.txt
if rg 'FAIL:|INSTRUMENTATION_FAILED|Process crashed' smoke-results/instrumentation.txt; then exit 1; fi
