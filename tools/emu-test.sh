#!/usr/bin/env bash
# 카디가드 에뮬레이터 스모크 시험 (Pleos Connect v2.1.0, Android 14 / API 34)
#
# 사용: bash tools/emu-test.sh        (에뮬레이터는 미리 켜 둘 것. 이 스크립트는 켜지 않는다)
#       APK=... bash tools/emu-test.sh
#
# 화면만 찍고 끝내지 않는다. 눈으로 못 보는 것: 서비스가 포그라운드인지,
# 화면이 뒤에 있을 때도 신호를 받는지, 음성이 실제로 나갔는지: 을 같이 확인한다.
set -u

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
# 실기기를 USB 로 꽂아 둔 채 돌리는 일이 잦다. 에뮬레이터를 골라 잡는다.
if [ -z "${ANDROID_SERIAL:-}" ]; then
  ANDROID_SERIAL="$("$ADB" devices 2>/dev/null | awk '/^emulator-[0-9]+\tdevice$/{print $1; exit}')"
  [ -n "$ANDROID_SERIAL" ] && export ANDROID_SERIAL
fi
PKG="ai.cardiguard.app"
ACT="$PKG/.ui.MainActivity"
SVC="$PKG/.service.GuardService"
NAVI="ai.umos.maps.android.navigation.app/.MainActivity"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
PERMS=(
  android.permission.RECORD_AUDIO
  android.permission.POST_NOTIFICATIONS
  android.car.permission.CAR_SPEED
  pleos.car.permission.FUSED_LOCATION
)

OUT="$ROOT/tools/out/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
LOG="$OUT/log.txt"
N=0
PASS=0
FAILS=()

say()  { echo "$*" | tee -a "$LOG"; }
ok()   { PASS=$((PASS+1)); say "  ✅ $*"; }
bad()  { FAILS+=("$1"); say "  ❌ $*"; }
dump() { "$ADB" shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/u.xml 2>/dev/null; }

shot() {
  N=$((N+1))
  local f; f="$(printf '%s/%02d-%s.png' "$OUT" "$N" "$1")"
  "$ADB" exec-out screencap -p > "$f" 2>/dev/null
  [ -s "$f" ] || bad "스크린샷 실패 $1"
}

# 화면에 이 글자가 뜰 때까지 기다린다. 기다리지 않고 찍으면 엉뚱한 화면이 남는다.
wait_text() {
  local n=0
  while [ $n -lt "${2:-15}" ]; do
    dump | grep -qF "text=\"$1\"" && return 0
    sleep 1; n=$((n+1))
  done
  return 1
}

tap_text() {
  local b; b=$(dump | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"')
  [ -z "$b" ] && return 1
  set -- $(echo "$b" | sed 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1 \2 \3 \4/')
  "$ADB" shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 1
}

signal() { "$ADB" shell am broadcast -a "$PKG.DEMO" --es signal "$1" >/dev/null 2>&1; }

cleanup() { "$ADB" shell cmd uimode night no >/dev/null 2>&1; }
trap cleanup EXIT

say "카디가드 에뮬레이터 시험  $(date '+%Y-%m-%d %H:%M:%S')"
say "출력: ${OUT#$ROOT/}"
say ""

# ── 0. 환경 ────────────────────────────────────────────────────────────────
say "[0] 환경"
if [ -z "${ANDROID_SERIAL:-}" ]; then
  say "에뮬레이터가 안 보인다. 켜고 다시 실행할 것."
  say "연결된 기기: $("$ADB" devices | tail -n +2 | tr '\n' ' ')"
  exit 1
fi
say "  대상: $ANDROID_SERIAL"
say "  기기: $("$ADB" shell getprop ro.product.model | tr -d '\r') / API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
say "  화면: $("$ADB" shell wm size | tr -d '\r')"
say "  APK : $(basename "$APK")"

# ── 1. 설치 ────────────────────────────────────────────────────────────────
say "[1] 설치"
if "$ADB" install -r "$APK" 2>&1 | grep -q Success; then ok "설치"; else
  "$ADB" uninstall "$PKG" >/dev/null 2>&1
  "$ADB" install "$APK" 2>&1 | grep -q Success && ok "재설치(서명 불일치로 지우고 설치)" || bad "설치 실패"
fi
for p in "${PERMS[@]}"; do "$ADB" shell pm grant --user 10 "$PKG" "$p" >/dev/null 2>&1; done
ok "권한 부여 ${#PERMS[@]}건"

# ── 2. 실행과 상시 서비스 ──────────────────────────────────────────────────
say "[2] 실행과 상시 서비스"
"$ADB" shell am force-stop "$PKG"; sleep 1
"$ADB" shell cmd uimode night no >/dev/null 2>&1
"$ADB" shell am start -n "$ACT" >/dev/null 2>&1
wait_text "말하기" 25 && ok "첫 화면" || bad "첫 화면이 안 뜸"
shot "idle-day"
FG=$("$ADB" shell dumpsys activity services "$SVC" 2>/dev/null | grep -oE "isForeground=true.*types=[0-9a-f]+" | head -1 | grep -oE "isForeground=true|types=[0-9a-f]+" | tr '\n' ' ')
[ -n "$FG" ] && ok "포그라운드 서비스 ($FG)" || bad "서비스가 포그라운드가 아니다"

# ── 3. 주행과 급제동 ───────────────────────────────────────────────────────
say "[3] 주행과 급제동"
signal drive; wait_text "주행 중" 15 && ok "기어 D 인식" || bad "주행 상태로 안 넘어감"
shot "driving"
signal brake; wait_text "급제동" 20 && ok "급제동 감지" || bad "급제동이 안 잡힘"
shot "brake"

# ── 4. 화면이 뒤에 있을 때 ─────────────────────────────────────────────────
say "[4] 화면이 뒤에 있을 때 (이 앱의 핵심)"
"$ADB" shell am start -n "$NAVI" >/dev/null 2>&1; sleep 4
TOP=$("$ADB" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity)
echo "$TOP" | grep -q navigation && ok "내비가 전면" || bad "내비가 전면이 아니다"
shot "navi-front"
"$ADB" logcat -c
signal airbag; sleep 7
if "$ADB" logcat -d 2>/dev/null | grep -q "onDeviceTtsPlayer($PKG:ondevice) - complete"; then
  ok "뒤에 있는 채로 감지하고 음성 안내"
else
  bad "백그라운드에서 음성이 안 나갔다"
fi
"$ADB" shell dumpsys notification 2>/dev/null | grep -q "channel=guard.ongoing" \
  && ok "상시 알림 유지" || bad "상시 알림이 없다"

# ── 5. 단계 안내 ───────────────────────────────────────────────────────────
say "[5] 단계 안내"
"$ADB" shell am start -n "$ACT" >/dev/null 2>&1
wait_text "접촉 사고" 20 && ok "앱으로 돌아오면 사고가 올라와 있다" || bad "복귀 후 사고가 없다"
signal park
wait_text "STEP 01 / 07" 20 && ok "정차 후 단계 안내 시작" || bad "단계 안내가 안 뜸"
shot "step-01"
tap_text "했어요"; tap_text "해당 없음"; tap_text "했어요"
wait_text "STEP 04 / 07" 10 && ok "했어요·해당 없음 처리가 진행" || bad "단계가 안 넘어감"
shot "step-04"
for i in 1 2 3 4; do tap_text "했어요" >/dev/null 2>&1; done
wait_text "안내 완료" 15 && ok "끝까지 가면 완료 화면" || bad "완료 화면이 안 뜸"
shot "done"

# ── 6. 주야간 ──────────────────────────────────────────────────────────────
say "[6] 주야간 (차가 정한다)"
tap_text "닫기" >/dev/null 2>&1; sleep 2
"$ADB" shell cmd uimode night yes >/dev/null 2>&1; sleep 4
shot "idle-night"
DARK=$(python3 - "$OUT" <<'PY'
import sys, glob
from PIL import Image
f = sorted(glob.glob(sys.argv[1] + "/*idle-night.png"))[-1]
im = Image.open(f).convert("RGB"); w, h = im.size
print(1 if sum(im.getpixel((int(w*0.6), int(h*0.5)))) < 250 else 0)
PY
)
[ "$DARK" = "1" ] && ok "night=yes 에서 화면이 어두워짐" || bad "야간으로 안 바뀐다"
"$ADB" shell cmd uimode night no >/dev/null 2>&1; sleep 3
shot "idle-day-back"

# ── 7. 안정성 ──────────────────────────────────────────────────────────────
say "[7] 안정성"
CRASH=$("$ADB" logcat -d -b crash 2>/dev/null | grep -c "$PKG")
[ "$CRASH" = "0" ] && ok "크래시 0" || bad "크래시 로그 ${CRASH}줄"
ANR=$("$ADB" logcat -d 2>/dev/null | grep -c "ANR in $PKG")
[ "$ANR" = "0" ] && ok "ANR 0" || bad "ANR ${ANR}건"
"$ADB" shell pidof "$PKG" >/dev/null 2>&1 && ok "프로세스 생존" || bad "프로세스가 죽었다"

# ── 정리 ───────────────────────────────────────────────────────────────────
say ""
say "────────────────────────────────"
say "통과 $PASS · 실패 ${#FAILS[@]}"
if [ ${#FAILS[@]} -gt 0 ]; then
  for f in "${FAILS[@]}"; do say "  - $f"; done
fi
say "스크린샷 ${N}장: ${OUT#$ROOT/}"
[ ${#FAILS[@]} -eq 0 ] || exit 1
