# Pleos SDK 와 AAOS car 라이브러리는 AIDL·리플렉션 경로가 있어 통째로 남긴다.
# 줄여서 얻는 몇 MB 보다 차량 신호가 조용히 끊기는 쪽이 훨씬 비싸다.
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-keep class android.car.** { *; }
-dontwarn ai.pleos.**
-dontwarn android.car.**

# 우리 도메인 모델은 이름이 로그와 저장 포맷에 쓰인다.
-keep class ai.cardiguard.app.domain.** { *; }
