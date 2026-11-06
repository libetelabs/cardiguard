# 카디가드

위험한 순간에 무엇을 해야 하는지 차가 먼저 안내하고, 그 순간을 기록으로 남기는 Pleos Connect 차량용 앱.

## 시연 영상

[`demo/카디가드_시연영상.mp4`](demo/) (2분 55초, 1920×1080) — Pleos Connect 에뮬레이터 실제 화면 녹화.
앞에 차례를 두고 사건 유형이 바뀔 때마다 구분 화면을 넣어 여섯 토막으로 끊었다.
① 평상시(대기·주행 감시) → ② 급제동(주행 중 안내 → **정차 후 원인 고르기**) →
③ 접촉 사고(**내비를 앞으로 보낸 뒤 에어백** — 화면이 뒤에 있어도 감지한다 → 복귀 →
단계 안내 7단계와 이번 주행 요약 → 안내 완료 → 대기 화면에 남는 기록) →
④ 위협(「위협받고 계신가요?」 → 문 잠금 + 112) → ⑤ 고장(비상등, 가드레일 밖 대피 —
위협 때와 정반대로 안내한다) → ⑥ 그 밖에(음성 「저장해」, 주야간 전환).
앱이 말하는 문장은 「(음성)」 자막으로 넣었다(adb screenrecord 는 소리를 녹음하지 않는다).
다시 만들기 — 바뀐 화면만 다시 찍어 끼운다. 완성본은 자막이 박혀 있어 통째로 다시 자르면
자막이 두 겹이 되기 때문이다. 장면 경계는 자막 띠의 프레임 간 차이로 찾는다.

| 도구 | 하는 일 |
|---|---|
| `tools/record_idle_scenes.sh` | 대기 화면 세 장면을 에뮬레이터에서 다시 찍는다 |
| `tools/record_case_scenes.sh` | 정차 화면(급제동·접촉 사고·고장)을 다시 찍는다 |
| `tools/make_demo_v3.py` | 타이틀·차례·구분 화면을 만들고 전체를 다시 조립한다 |
| `tools/make_demo_video.py` | 첫 완성본을 만들 때 쓴 조립기(원본 녹화가 남아 있지 않아 지금은 쓰지 않는다) |

## 다른 앱이 앞에 있어도 감지한다

운전자는 내비나 음악을 띄워 두고 달리고, 급제동과 사고는 바로 그때 일어난다.
차량 신호 처리는 화면이 아니라 `GuardService`(foreground, `specialUse`)가 들고 있어서
액티비티가 뒤로 가거나 닫혀도 계속 받는다. 사고를 잡으면 화면을 가로채지 않고
음성으로 안내하고 알림을 띄운다 — 주행 중에 다른 앱 위로 올라타는 것 자체가 위험이기 때문이다.

에뮬레이터 실측: `isForeground=true types=0x40000000`, 내비가 전면인 상태에서 에어백 신호 →
엔진이 사고로 처리하고 TTS 가 안내를 읽음 → 앱으로 돌아오면 그 기록이 남아 있다.

⚠️ 알려진 한계
- Pleos 셸에 서드파티 알림을 그리는 UI 가 없어 알림이 화면에 보이지 않는다. 전달 경로는 음성이다.
- `BOOT_COMPLETED` 자동 시작이 에뮬레이터에서 동작하지 않는다(리시버 등록·브로드캐스트 모두 확인했으나
  호출되지 않음, 원인 미파악). 앱을 한 번 열면 그 뒤로는 화면과 무관하게 동작한다.

## 주야간

색을 앱이 정하지 않고 시스템 `uiMode` 를 따라간다. Pleos 는 주야간을 플랫폼이 쥐고 있다
(`mNightModeLocked=true`). 확인: `adb shell cmd uimode night yes|no`.

## 실행 방법

1. Pleos Connect 에뮬레이터 v2.1.0(Android 14, API 34)을 실행한다.
2. `adb install -g apk/libetelabs_cardiguard_release.apk`로 설치하고 앱 목록에서 카디가드를 연다.
3. Virtual Console에 속도 입력이 없어, 에뮬레이터에서는 맨 위 상태 띠 오른쪽 끝의 `시연 모드` 버튼으로 주행·급제동·에어백·음성 문장을 넣는다. adb로도 넣을 수 있다.

   ```sh
   adb shell am broadcast --user 10 -a ai.cardiguard.app.DEMO --es signal drive    # park, brake, airbag, door
   adb shell am broadcast --user 10 -a ai.cardiguard.app.DEMO --es signal voice --es text 도와줘
   adb shell am broadcast --user 10 -a ai.cardiguard.app.DEMO --es signal label --es cause OTHER   # MINE, SLOWDOWN
   ```

   시연 입력은 에뮬레이터에서만 열리고 실제 차량에서는 동작하지 않는다.

## 테스트 환경 및 필요한 설정

- Pleos Connect 에뮬레이터 v2.1.0, 화면 2560×1440
- Android API 28 이상(minSdk 28, targetSdk 34), Gradle 8.11.1, Kotlin 2.0.21, JDK 21
- Pleos SDK: Vehicle 2.3.2, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3
- 음성 안내와 인식은 온디바이스 모드로 동작하며 별도 키가 필요 없다.
- 권한: 마이크, 차량 속도. 설치할 때 `-g`를 붙이면 자동으로 허용된다.
- 소스 빌드: `./gradlew assembleDebug`, 테스트: `./gradlew testDebugUnitTest`

## 시험

자동 스모크 시험: `APK=apk/libetelabs_cardiguard_release.apk bash tools/emu-test.sh`
(에뮬레이터는 미리 켜 둘 것. 스크립트가 켜지 않는다.)

설치·권한·상시 서비스·주행·급제동·**화면이 뒤에 있을 때의 감지**·단계 안내·주야간·크래시/ANR 까지
17항목을 확인하고 스크린샷 9장과 로그를 `tools/out/<시각>/` 에 남긴다.
눈으로 못 보는 것(서비스가 정말 포그라운드인지, 음성이 실제로 나갔는지, 야간에 화면이 어두운지)을
문자열과 픽셀값으로 직접 확인한다.

결과 기록: [docs/test-log-2026-11-03.md](docs/test-log-2026-11-03.md) — **17 통과 · 0 실패**(제출 APK 기준).
단위 테스트 `./gradlew testDebugUnitTest` — 27개 통과.

## 기획서

심사 자료인 **02 장 「앱 프로젝트 소개」 전문**이 이 저장소에 있다.

| 파일 | 쓰임 |
|---|---|
| [`docs/proposal/카디가드_기획서_02.md`](docs/proposal/카디가드_기획서_02.md) | **02 장 원본 글.** 제출한 접수양식의 02 장과 같은 내용이다 |
| `docs/proposal/contest-application-consent.docx` | 운영사무국 빈 양식 |

**채워 넣은 접수양식(.docx)은 이 저장소에 두지 않는다.** 01 참가신청서에 대표자·구성원의
성명·연락처·서명이 들어가기 때문이다. 양식이 「공유 링크와 첨부자료에도 개인정보·계정정보·
개발자 실명이 노출되지 않도록」 하라고 적고 있어, 제출본은 이메일로만 보냈다.

다시 만들기: `python3 tools/fill_form.py docs/proposal/contest-application-consent.docx docs/proposal/카디가드_기획서_02.md <출력>.docx`

## APK

`apk/libetelabs_cardiguard_release.apk` — 16.9MB, versionName 0.1.0
서명 `CN=Cardiguard`, SHA-256 `2211ed94acac78063cea88c388a0222740daa071b5c024f877cbb26547837437`

release 는 R8 과 리소스 축소를 켠다(끄면 아이콘 라이브러리 때문에 62MB). Pleos SDK 와
`android.car` 는 `app/proguard-rules.pro` 에서 통째로 남긴다 — 몇 MB 보다 차량 신호가
조용히 끊기는 쪽이 비싸다. 다시 만들기: `keystore.properties` 와 `.jks` 를 레포 루트에 두고
`./gradlew assembleRelease` (둘 다 `.gitignore` 에 있어 커밋되지 않는다).
