package ai.cardiguard.app.domain

/**
 * 짧은 구간의 속도 감소로 급제동을 찾는다. 브레이크 페달은 on/off만 제공되어 세기를 알 수 없다.
 * 급제동은 "기록할 계기"일 뿐 상대 잘못의 증거가 아니다.
 */
class HardBrakeDetector(
    private val windowMs: Long = 1_500,
    // 1.5초에 25km/h 는 약 4.6m/s^2 다. 일반 제동은 2~3, 급제동은 5~6 쯤에서 시작한다.
    // 20km/h(3.7)로 두면 시내 신호와 정체에서 걸려 기록이 의미를 잃는다.
    private val minDropKmh: Float = 25f,
    private val minStartKmh: Float = 30f,
    // 같은 제동을 두 번 세지 않기 위한 간격이다. 늘리면 안 된다 —
    // 보복운전은 짧은 간격의 반복 급제동이라 간격을 조이면 위협 승급이 죽는다.
    private val cooldownMs: Long = 10_000,
) {
    private val samples = ArrayDeque<Signal.Speed>()
    private var lastHitMs = Long.MIN_VALUE / 2
    var brakePressed: Boolean? = null

    /** 급제동이면 감속이 시작된 시점의 속도를 돌려준다. */
    fun onSpeed(sample: Signal.Speed): Float? {
        samples.addLast(sample)
        while (samples.first().atMs < sample.atMs - windowMs) samples.removeFirst()

        val peak = samples.maxOf { it.kmh }
        val isHard = peak >= minStartKmh &&
            peak - sample.kmh >= minDropKmh &&
            brakePressed != false &&
            sample.atMs - lastHitMs >= cooldownMs
        if (!isHard) return null

        lastHitMs = sample.atMs
        return peak
    }
}
