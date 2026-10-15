#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am start -W -n br.com.pmcn.transcritor/.MainActivity
adb push /tmp/ggml-base.bin /data/local/tmp/ggml-base.bin
adb shell run-as br.com.pmcn.transcritor mkdir -p files
adb shell run-as br.com.pmcn.transcritor cp /data/local/tmp/ggml-base.bin files/ggml-base.bin
adb shell svc wifi disable
adb shell svc data disable
adb shell am instrument -w br.com.pmcn.transcritor.test/android.test.InstrumentationTestRunner | tee test-result.txt
adb logcat -d > logcat.txt
grep -q 'OK (2 tests)' test-result.txt
! grep -q 'FATAL EXCEPTION' logcat.txt
