package ai.cardiguard.app.domain

enum class VoiceIntent { YES, NO, RECORD, THREAT, COLLISION, BREAKDOWN, CALL_POLICE, UNKNOWN }

/** STT는 받아쓰기만 하므로 명령 해석은 앱이 한다. 없는 말을 안내로 지어내지 않도록 키워드만 쓴다. */
object VoiceIntentParser {
    private val denied = listOf("아니", "괜찮", "됐어", "필요없")
    private val notOkay = listOf("안괜찮", "못괜찮", "안돼", "살려")
    private val yes = listOf("네", "응", "예", "어", "맞아", "그래", "해줘", "연결")

    fun parse(raw: String): VoiceIntent {
        val text = raw.replace(" ", "").trim('.', '?', '!')
        return when {
            notOkay.any(text::contains) -> VoiceIntent.THREAT
            denied.any(text::contains) -> VoiceIntent.NO
            listOf("112", "경찰", "신고").any(text::contains) -> VoiceIntent.CALL_POLICE
            listOf("박았", "받았", "부딪", "접촉", "사고", "충돌").any(text::contains) -> VoiceIntent.COLLISION
            listOf("도와", "위협", "따라와", "따라오", "쫓아", "보복", "무서").any(text::contains) -> VoiceIntent.THREAT
            listOf("고장", "퍼졌", "시동이꺼", "멈췄", "안움직").any(text::contains) -> VoiceIntent.BREAKDOWN
            listOf("저장", "기록").any(text::contains) -> VoiceIntent.RECORD
            yes.any(text::startsWith) -> VoiceIntent.YES
            else -> VoiceIntent.UNKNOWN
        }
    }
}
