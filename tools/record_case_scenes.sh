#!/usr/bin/env bash
# 사건 유형별 정차 화면을 찍는다.
#
# ParkedCard 에 「이번 주행」 요약이 붙으면서 정차 화면이 모두 바뀌었다.
# 급제동 정차(원인 고르기)는 아예 없던 장면이다.
#
# 사용: bash tools/record_case_scenes.sh [출력디렉터리]
#       에뮬레이터는 미리 켜 둘 것. APK 는 release — 제출본과 같아야 한다.
set -eu

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
if [ -z "${ANDROID_SERIAL:-}" ]; then
  ANDROID_SERIAL="$("$ADB" devices | awk '/^emulator-[0-9]+\tdevice$/{print $1; exit}')"
  export ANDROID_SERIAL
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG="ai.cardiguard.app"
ACT="$PKG/.ui.MainActivity"
APK="${APK:-$ROOT/app/build/outputs/apk/release/app-release.apk}"
OUT="${1:-$ROOT/tools/out/case-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"

sig()  { "$ADB" shell am broadcast -a "$PKG.DEMO" --es signal "$1" >/dev/null 2>&1; sleep "${2:-4}"; }
# 고장은 문 열림이 아니라 말로 들어온다. 문 열림은 위협 중에만 경고로 쓴다.
#
# 따옴표를 한 겹 더 씌운다. adb shell 은 우리 따옴표를 먹고 원격 sh 에 넘기므로
# "차가 고장났어" 는 두 낱말로 쪼개져 뒷말이 pkg 인자로 붙는다(pkg=고장났어).
say()  { "$ADB" shell am broadcast -a "$PKG.DEMO" --es signal voice --es text "'$1'" >/dev/null 2>&1; sleep "${2:-5}"; }
dump() { "$ADB" shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/u.xml 2>/dev/null; }
tap()  {
  local b; b=$(dump | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"') || true
  [ -z "${b:-}" ] && return 1
  set -- $(echo "$b" | sed 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1 \2 \3 \4/')
  "$ADB" shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
wait_text() {
  local n=0; while [ $n -lt "${2:-20}" ]; do dump | grep -qF "text=\"$1\"" && return 0; sleep 1; n=$((n+1)); done; return 1
}
reset_app() {
  "$ADB" shell am force-stop "$PKG"; sleep 1
  # 기록을 비우고 시작한다. 지난 주행이 섞이면 화면의 숫자가 자막과 어긋난다.
  # 이 에뮬레이터에서 앱은 user 10 으로 돈다. --user 를 빼면 조용히 아무것도 안 지운다.
  "$ADB" shell pm clear --user 10 "$PKG" >/dev/null
  for p in android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS \
           android.car.permission.CAR_SPEED pleos.car.permission.FUSED_LOCATION; do
    "$ADB" shell pm grant --user 10 "$PKG" "$p" >/dev/null 2>&1 || true
  done
  "$ADB" shell am start -n "$ACT" >/dev/null
  wait_text "말하기" 25; sleep 3
}
start_rec() { "$ADB" shell screenrecord --time-limit "$2" --bit-rate 16000000 "/sdcard/$1.mp4" & REC=$!; }
end_rec()   { wait "$REC"; "$ADB" pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null; echo "  찍음: $1.mp4"; }
trap 'kill ${REC:-0} 2>/dev/null || true' EXIT

ONLY="${ONLY:-all}"      # all | brake | collision | breakdown
want() { [ "$ONLY" = all ] || [ "$ONLY" = "$1" ]; }

echo "대상 $ANDROID_SERIAL / APK $(basename "$APK") / 장면 $ONLY"
"$ADB" uninstall "$PKG" >/dev/null 2>&1 || true
"$ADB" install "$APK" >/dev/null
"$ADB" shell cmd uimode night no >/dev/null

# ── 1. 급제동 정차 — 원인을 고른다 (새 장면) ──────────────────────────────
if want brake; then
echo "[1] 급제동 정차 — 원인 고르기"
reset_app
sig drive 5; sig brake 7; sig park 7
wait_text "상대 때문" 20 || echo "  ! 원인 버튼이 안 보인다"
start_rec brake-parked 18
sleep 6            # 요약과 세 버튼을 먼저 읽을 시간을 준다
tap "상대 때문"
end_rec brake-parked
fi

# ── 2. 접촉 사고 — 단계 안내와 완료 ───────────────────────────────────────
if want collision; then
echo "[2] 접촉 사고 — 단계 안내 · 완료"
reset_app
sig drive 5; sig brake 7; sig airbag 8; sig park 7
wait_text "STEP 01 / 07" 20 || echo "  ! 단계 안내가 안 뜬다"
start_rec collision 52
sleep 5
for choice in 했어요 했어요 "해당 없음" 했어요 했어요 했어요 했어요; do
  sleep 2.5; tap "$choice" || true
done
end_rec collision
fi

# ── 3. 고장 정차 — 단계 안내 ──────────────────────────────────────────────
if want breakdown; then
echo "[3] 고장 정차 — 단계 안내"
reset_app
sig drive 5; say "차가 고장났어" 8; sig park 7
wait_text "STEP 01 / 03" 20 || echo "  ! 고장 단계가 안 뜬다"
start_rec breakdown-parked 22
sleep 5
for choice in 했어요 했어요 했어요; do sleep 3; tap "$choice" || true; done
end_rec breakdown-parked
fi

echo "끝. $OUT"
