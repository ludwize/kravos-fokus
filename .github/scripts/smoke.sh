#!/usr/bin/env bash
# Installs the APK on the emulator, grants permissions, walks every screen, triggers a block, records screenshots + crashes.
set -u
OUT=smoke
mkdir -p "$OUT"
APK=$(ls out/*.apk | head -1)
PKG=net.kravos.fokus

adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
adb logcat -c

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1" >> "$OUT/steps.txt"; }
dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
}
# tap_text "<exact text or content-desc>" [index]
tap_text() {
  dump
  python3 - "$1" "${2:-0}" "$OUT/ui.xml" <<'PY' > "$OUT/tap.txt"
import re, sys, xml.etree.ElementTree as ET
want, idx, path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
hits = []
for n in root.iter('node'):
    if n.get('text') == want or n.get('content-desc') == want:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        hits.append(((x1 + x2) // 2, (y1 + y2) // 2))
if len(hits) > idx:
    print(*hits[idx])
PY
  if [ ! -s "$OUT/tap.txt" ] && [ "${RETRY:-1}" = 1 ]; then
    sleep 2; RETRY=0 tap_text "$1" "${2:-0}"; return
  fi
  if [ -s "$OUT/tap.txt" ]; then
    adb shell input tap $(cat "$OUT/tap.txt"); echo "tapped $1" >> "$OUT/steps.txt"
  else
    echo "not found: $1" >> "$OUT/steps.txt"
  fi
  sleep 3
}
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
scroll() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 400; sleep 2; }
home() { adb shell input keyevent KEYCODE_HOME; sleep 2; }
open_app() { adb shell monkey -p "$1" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep "${2:-5}"; }

# a little real usage so the stats have something in them
open_app com.android.settings 6; home
open_app com.google.android.contacts 5; home
open_app com.google.android.deskclock 5; home
open_app com.android.settings 4; home
adb shell pm list packages > "$OUT/packages.txt"

adb shell am start -W -n $PKG/.ui.MainActivity --ez smoke_seed true > "$OUT/start.txt" 2>&1
sleep 10
shot 01-today-setup

# turn on the guard like the user would in Accessibility settings
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.block.GuardService
adb shell settings put secure accessibility_enabled 1
sleep 6
adb shell am start -n $PKG/.ui.MainActivity >/dev/null; sleep 6
shot 02-today
scroll; shot 03-today-scroll
scroll; shot 04-today-scroll2

tap_text "Statistika"; sleep 4; shot 10-stats-day
scroll; shot 11-stats-day-scroll
tap_text "Nedelja"; sleep 6; shot 12-stats-week
scroll; shot 13-stats-week-scroll
scroll; shot 14-stats-week-scroll2
tap_text "Dan"; sleep 2

tap_text "Pravila"; sleep 3; shot 20-rules
scroll; shot 21-rules-shorts
adb shell input swipe $((W / 2)) $((H / 4)) $((W / 2)) $((H * 3 / 4)) 400; sleep 2
tap_text "Društvene"; sleep 4; shot 22-rule-edit
scroll; shot 23-rule-edit-scroll
scroll; shot 24-rule-edit-scroll2
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_text "Nova grupa"; sleep 4; shot 25-new-rule
tap_text "Izmeni"; sleep 5; shot 26-app-picker
adb shell input keyevent KEYCODE_BACK; sleep 2

# the blocked app: schedule "Test blokada" covers the whole day
open_app com.google.android.deskclock 6
shot 30-block-schedule
tap_text "Zatvori"; sleep 3; shot 31-after-close

adb shell am start -n $PKG/.ui.MainActivity --es tab FOCUS >/dev/null; sleep 4
shot 40-focus
scroll; tap_text "Započni 25 min"; sleep 4; shot 41-focus-running
open_app com.google.android.contacts 6
shot 42-block-session
home
adb shell am start -n $PKG/.ui.MainActivity --es tab FOCUS >/dev/null; sleep 3
tap_text "Završi ranije"; sleep 3; shot 43-focus-ended

adb shell am start -n $PKG/.ui.MainActivity --es tab TODAY >/dev/null; sleep 3
tap_text "Podešavanja"; sleep 4; shot 50-settings
scroll; shot 51-settings-scroll
scroll; shot 52-settings-scroll2
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_text "Statistika"; sleep 4
tap_text "Kontakti" ; tap_text "Contacts"; sleep 4; shot 60-app-detail

PID=$(adb shell pidof $PKG | tr -d '\r')
echo "pid=$PID" > "$OUT/status.txt"
adb logcat -d > "$OUT/logcat.txt"
grep -n -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/crash.txt" || true
grep -n -E "KravosFokus|AndroidRuntime|net.kravos" "$OUT/logcat.txt" | tail -500 > "$OUT/app-log.txt" || true
if [ -s "$OUT/crash.txt" ]; then echo "CRASH" >> "$OUT/status.txt"; fi
rm -f "$OUT/logcat.txt" "$OUT/ui.xml"
cat "$OUT/status.txt" "$OUT/steps.txt"
exit 0
