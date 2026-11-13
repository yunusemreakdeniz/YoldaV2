#!/usr/bin/env bash
# Yolda emülatör testleri: her test sınıfı ayrı ve süre sınırlı çalışır, sonuçları hemen kaydedilir.
# Bir sınıf takılırsa yalnız o sınıf kesilir; diğerlerinin raporu, günlüğü ve ekran görüntüleri yine toplanır.
set -u
OUT=test-sonuclari
mkdir -p "$OUT"
code=0
adb logcat -c || true
adb logcat -v time -s YoldaTest:I YoldaUiUx:I AndroidRuntime:E Yolda:I chromium:E cr_AwContents:W DEBUG:F libc:F > "$OUT/canli-gunluk.txt" 2>&1 &
LOGPID=$!
for c in YoldaWakeTest YoldaFunctionalTest YoldaAtoZTest YoldaPlaybackTest YoldaMusicTest YoldaGestureTest YoldaPharmacyTest YoldaUiUxTest; do
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
  # Donma: Yolda'nınki (ActivityManager "ANR in"), her biri bir kez. Emülatörün ses sunucusu (AudioFlinger) son 15
  # sn içinde çöktüyse ortam kaynaklı sayılır (ses çağrıları sunucu yeniden kalkana kadar bekler); ayrıca raporlanır.
  python3 - "$OUT/dayaniklilik-gunluk.txt" "$PKG" > "$OUT/dayaniklilik-donmalar.txt" <<'PYX'
import re, sys
from datetime import datetime
log, pkg = sys.argv[1], sys.argv[2]
def ts(l):
    try: return datetime.strptime(l[:18], '%m-%d %H:%M:%S.%f')
    except Exception: return None
deaths, anrs = [], []
for l in open(log, encoding='utf-8', errors='replace'):
    if 'AudioFlinger server died' in l or 'AudioPolicyService server died' in l:
        t = ts(l)
        if t: deaths.append(t)
    if ('E/ActivityManager' in l) and ('ANR in ' + pkg) in l:
        t = ts(l)
        if t: anrs.append(t)
own = env = 0
for t in anrs:
    if any(0 <= (t - d).total_seconds() <= 15 for d in deaths):
        env += 1; print('ortam', t)
    else:
        own += 1; print('yolda', t)
print('SAYI', own, env)
PYX
  anrs=$(awk '/^SAYI/{print $2}' "$OUT/dayaniklilik-donmalar.txt")
  envanr=$(awk '/^SAYI/{print $3}' "$OUT/dayaniklilik-donmalar.txt")
  adb root >/dev/null 2>&1 && sleep 2 && adb pull /data/anr "$OUT/anr" >/dev/null 2>&1 || true
  echo "=== Dayanıklılık bitti: Yolda çökmesi $crashes, donma $anrs (ses sunucusu çökmesine bağlı: ${envanr:-0})"
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
