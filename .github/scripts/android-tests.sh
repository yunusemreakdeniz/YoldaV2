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
for c in YoldaWakeTest YoldaFunctionalTest YoldaAtoZTest YoldaPlaybackTest YoldaUiUxTest; do
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
# ---- Dayanıklılık: binlerce rastgele dokunuş, kaydırma, tuş; çökme ya da donma (ANR) aranır ----
PKG=me.aap.fermata.auto.yoldav2.debug
# Tüm eklentiler içinde tek APK (kullanıcıya verilenle aynı yapı); yalnız ana parça eklentisiz çöker
APK=$(ls fermata/build/outputs/apk_from_bundle/autoDebug/*.apk 2>/dev/null | head -1)
echo "Dayanıklılık APK: $APK"
if [ -n "$APK" ]; then
  echo "=== Dayanıklılık testi başlıyor $(date -u +%H:%M:%S)"
  adb uninstall $PKG >/dev/null 2>&1 || true
  adb install -r -g "$APK" || true
  adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 6
  adb logcat -c || true
  for seed in 11 29 47; do
    timeout 10m adb shell monkey -p $PKG -s $seed --throttle 120 --pct-syskeys 0 --pct-appswitch 3 \
      --ignore-crashes --ignore-timeouts --ignore-security-exceptions \
      --pct-touch 45 --pct-motion 25 --pct-nav 10 --pct-majornav 10 -v 2500 > "$OUT/dayaniklilik-$seed.txt" 2>&1
    echo "tohum $seed: çıkış $? · $(grep -c 'CRASH' "$OUT/dayaniklilik-$seed.txt") çökme · $(grep -c 'NOT RESPONDING' "$OUT/dayaniklilik-$seed.txt") donma"
    adb shell am force-stop $PKG || true
  done
  adb logcat -d -v time > "$OUT/dayaniklilik-gunluk.txt" 2>&1 || true
  # Yalnız Yolda'nın kendi çökmeleri ve donmaları sayılır
  awk '/FATAL EXCEPTION/{f=1} f{print} /^$/{f=0}' "$OUT/dayaniklilik-gunluk.txt" | head -300 > "$OUT/dayaniklilik-cokmeler.txt"
  crashes=$(grep -c "Process: $PKG" "$OUT/dayaniklilik-cokmeler.txt" || true)
  anrs=$(grep -hc "NOT RESPONDING: $PKG\|ANR in $PKG" "$OUT"/dayaniklilik-*.txt | awk '{s+=$1} END {print s+0}')
  echo "=== Dayanıklılık bitti: Yolda çökmesi $crashes, donma $anrs"
  if [ "${crashes:-0}" -gt 0 ] || [ "${anrs:-0}" -gt 0 ]; then
    code=1
    echo "--- Dayanıklılık çökme yığınları"
    grep -B2 -A40 "Process: $PKG" "$OUT/dayaniklilik-cokmeler.txt" | head -150
  fi
fi
kill $LOGPID 2>/dev/null || true
mkdir -p "$OUT/ekranlar"
adb pull /data/local/tmp/yolda-ui/. "$OUT/ekranlar/" || true
echo "--- Yolda günlük satırları"
grep -E "YoldaTest|YoldaUiUx|AndroidRuntime" "$OUT/canli-gunluk.txt" | tail -150
ls "$OUT/ekranlar" | head -80
exit $code
