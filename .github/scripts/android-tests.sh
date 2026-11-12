#!/usr/bin/env bash
# Yolda emülatör testleri: her test sınıfı ayrı ve süre sınırlı çalışır, sonuçları hemen kaydedilir.
# Bir sınıf takılırsa yalnız o sınıf kesilir; diğerlerinin raporu, günlüğü ve ekran görüntüleri yine toplanır.
set -u
OUT=test-sonuclari
mkdir -p "$OUT"
code=0
adb logcat -c || true
adb logcat -v time -s YoldaTest:I YoldaUiUx:I AndroidRuntime:E Yolda:I > "$OUT/canli-gunluk.txt" 2>&1 &
LOGPID=$!
for c in YoldaWakeTest YoldaFunctionalTest YoldaPlaybackTest YoldaUiUxTest; do
  echo "=== $c başlıyor $(date -u +%H:%M:%S)"
  timeout 18m ./gradlew fermata:connectedAutoDebugAndroidTest $GRADLE_ARGS \
    -Pandroid.testInstrumentationRunnerArguments.class=me.aap.fermata.ui.fragment.$c
  rc=$?
  echo "=== $c bitti: çıkış $rc $(date -u +%H:%M:%S)"
  [ $rc -ne 0 ] && code=1
  mkdir -p "$OUT/$c"
  cp -r fermata/build/reports/androidTests/. "$OUT/$c/" 2>/dev/null || true
  if [ $rc -eq 124 ]; then
    echo "!!! $c süre sınırında kesildi: ana iş parçacığı yığını"
    adb shell "kill -3 \$(pidof me.aap.fermata.auto.yoldav2.debug)" || true
    sleep 3
    adb shell "cat /data/anr/traces.txt 2>/dev/null; ls /data/anr" > "$OUT/$c/takilma.txt" 2>&1 || true
    adb logcat -d -b main | grep -A40 '"main"' | head -80 >> "$OUT/$c/takilma.txt" || true
  fi
done
kill $LOGPID 2>/dev/null || true
mkdir -p "$OUT/ekranlar"
adb pull /data/local/tmp/yolda-ui/. "$OUT/ekranlar/" || true
echo "--- Yolda günlük satırları"
grep -E "YoldaTest|YoldaUiUx|AndroidRuntime" "$OUT/canli-gunluk.txt" | tail -150
ls "$OUT/ekranlar" | head -80
exit $code
