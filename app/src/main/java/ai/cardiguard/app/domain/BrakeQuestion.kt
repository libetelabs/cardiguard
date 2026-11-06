package ai.cardiguard.app.domain

/**
 * 원인을 몇 번 물을지 정하는 규칙.
 *
 * 시내 주행이면 급제동은 하루에도 여러 번 나온다. 신호와 정체로 밟은 것까지 전부
 * 물으면 운전자는 곧 답하지 않게 되고, 답이 없으면 원인이 붙은 기록이라는 것 자체가
 * 빈 말이 된다. 화면에 "원인 미입력 열두 건"이 빨갛게 쌓이는 것도 부채로 읽힌다.
 *
 * 그래서 기록은 전부 남기되 묻는 것은 끊는다. 한 주행에서 가장 세게 밟은 쪽부터
 * [ASK_LIMIT] 건까지만 묻는다. 운전자도 센 것만 궁금하다.
 */
object BrakeQuestion {

    /** 한 주행에서 원인을 묻는 최대 건수. */
    const val ASK_LIMIT = 2

    /** 감속 폭. 셀 수 없으면 0으로 둬서 뒤로 민다. */
    fun dropKmh(record: IncidentRecord): Float {
        val before = record.vehicle.speedBefore ?: return 0f
        val after = record.vehicle.speedAfter ?: return 0f
        return (before - after).coerceAtLeast(0f)
    }

    /**
     * 이 주행에서 원인을 물을 급제동. 세게 밟은 순, 같으면 나중 것 먼저.
     * 이미 답한 것은 자리를 차지한 채로 남는다 — 답했다고 다음 것을 더 묻지 않는다.
     */
    fun asked(brakes: List<IncidentRecord>, limit: Int = ASK_LIMIT): List<IncidentRecord> =
        brakes.sortedWith(compareByDescending<IncidentRecord> { dropKmh(it) }.thenByDescending { it.atMs })
            .take(limit)

    /** 아직 답하지 않아 화면에 띄울 것. */
    fun pending(brakes: List<IncidentRecord>, limit: Int = ASK_LIMIT): List<IncidentRecord> =
        asked(brakes, limit).filter { it.cause == null }
}
