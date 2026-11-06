#!/usr/bin/env bash
# 대기 화면 장면만 다시 찍는다.
#
# 완성본(demo/카디가드_시연영상.mp4)은 자막이 박혀 있어 통째로 다시 자를 수 없다.
# 대기 화면이 나오는 구간은 세 군데뿐이라(프레임 0–195 / 2489–2672 / 2673–2907)
# 그 세 토막만 새로 찍어 갈아 끼운다. 자르고 붙이는 일은 replace_idle_scenes.py 가 한다.
#
# 사용: bash tools/record_idle_scenes.sh [출력디렉터리]
#       에뮬레이터는 미리 켜 둘 것. 설치되는 APK 는 release: 제출본과 같아야 한다.
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
OUT="${1:-$ROOT/tools/out/idle-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"

sig()  { "$ADB" shell am broadcast -a "$PKG.DEMO" --es signal "$1" >/dev/null 2>&1; sleep "${2:-4}"; }
dump() { "$ADB" shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/u.xml 2>/dev/null; }
tap()  {
  local b; b=$(dump | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"') || return 1
  [ -z "$b" ] && return 1
  set -- $(echo "$b" | sed 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1 \2 \3 \4/')
  "$ADB" shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 1
}
wait_text() {
  local n=0; while [ $n -lt "${2:-20}" ]; do dump | grep -qF "text=\"$1\"" && return 0; sleep 1; n=$((n+1)); done; return 1
}
# 녹화는 따로 띄워 두고 그 사이에 화면을 만지면 안 된다. 멈춘 화면을 그대로 담는다.
rec() {   # rec <이름> <초>
  "$ADB" shell screenrecord --time-limit "$2" --bit-rate 16000000 "/sdcard/$1.mp4" &
  local p=$!; trap 'kill '"$p"' 2>/dev/null || true' EXIT
  wait "$p"; trap - EXIT
  "$ADB" pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null
  echo "  찍음: $OUT/$1.mp4"
}

echo "대상 $ANDROID_SERIAL / APK $(basename "$APK")"
"$ADB" uninstall "$PKG" >/dev/null 2>&1 || true
"$ADB" install "$APK" >/dev/null
for p in android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS \
         android.car.permission.CAR_SPEED pleos.car.permission.FUSED_LOCATION; do
  "$ADB" shell pm grant --user 10 "$PKG" "$p" >/dev/null 2>&1 || true
done
"$ADB" shell cmd uimode night no >/dev/null

echo "[A] 첫 대기: 기록이 비어 있다"
"$ADB" shell am force-stop "$PKG"; sleep 1
"$ADB" shell am start -n "$ACT" >/dev/null; wait_text "말하기" 25
sleep 3                      # 권한 창이 뜨면 여기서 지나간다
rec idle-empty 12

echo "[B] 사고 뒤 대기: 방금 생긴 기록이 남아 있다"
sig drive 5; sig brake 7; sig airbag 8; sig park 6
wait_text "STEP 01 / 07" 20 || true
for i in 1 2 3 4 5 6 7; do tap "했어요" >/dev/null 2>&1 || true; done
tap "닫기" >/dev/null 2>&1 || true
wait_text "최근 기록" 20 || true
sleep 2
rec idle-record 12

echo "[C] 주야간: 차가 정한다"
"$ADB" shell screenrecord --time-limit 14 --bit-rate 16000000 /sdcard/idle-night.mp4 &
P=$!; trap 'kill $P 2>/dev/null || true' EXIT
sleep 4
"$ADB" shell cmd uimode night yes >/dev/null     # 녹화 도중에 차가 밤으로 넘어간다
wait "$P"; trap - EXIT
"$ADB" pull /sdcard/idle-night.mp4 "$OUT/idle-night.mp4" >/dev/null
echo "  찍음: $OUT/idle-night.mp4"
"$ADB" shell cmd uimode night no >/dev/null

echo "끝. $OUT"
