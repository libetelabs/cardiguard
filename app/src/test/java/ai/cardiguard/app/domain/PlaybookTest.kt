package ai.cardiguard.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybookTest {
    @Test
    fun `주행 중 문구는 세 문장 45자 이하다`() {
        IncidentType.entries.forEach { type ->
            val line = Playbook.guide(type).drivingLine
            assertTrue(line, line.count { it in ".?!" } <= 3)
            assertTrue(line, line.length <= 45)
        }
    }

    @Test
    fun `과실을 단정하는 문구가 없다`() {
        val all = IncidentType.entries.flatMap { Playbook.guide(it).parkedSteps + Playbook.guide(it).drivingLine }
        assertTrue(all.none { it.contains("상대 잘못") || it.contains("과실은 상대") })
    }

    @Test
    fun `음성 의도를 구분한다`() {
        val cases = mapOf(
            "방금 저장해" to VoiceIntent.RECORD,
            "박았어" to VoiceIntent.COLLISION,
            "사고 아니야" to VoiceIntent.NO,
            "안 괜찮아" to VoiceIntent.THREAT,
            "경찰 불러줘" to VoiceIntent.CALL_POLICE,
            "차가 고장났어" to VoiceIntent.BREAKDOWN,
            "오늘 날씨 어때" to VoiceIntent.UNKNOWN,
        )
        cases.forEach { (text, intent) -> assertEquals(text, intent, VoiceIntentParser.parse(text)) }
    }

    @Test
    fun `단계마다 그림이 하나씩 붙는다`() {
        IncidentType.entries.forEach { type ->
            val guide = Playbook.guide(type)
            assertEquals(type.name, guide.parkedSteps.size, guide.stepIcons.size)
        }
    }

    private fun brake(id: Long, before: Float, after: Float, cause: Cause? = null) = IncidentRecord(
        id = id, sessionId = 1, atMs = id, type = IncidentType.SUDDEN_BRAKE,
        trigger = Trigger.HARD_BRAKE, latitude = null, longitude = null,
        cause = cause, labeledAtMs = null,
        vehicle = VehicleSnapshot(before, after, "D", true, false, null),
    )

    @Test
    fun `원인은 그 주행에서 가장 세게 밟은 두 건만 묻는다`() {
        val brakes = listOf(
            brake(1, 60f, 50f),   // 10
            brake(2, 90f, 40f),   // 50
            brake(3, 80f, 45f),   // 35
            brake(4, 50f, 45f),   // 5
        )
        val asked = BrakeQuestion.asked(brakes).map { it.id }
        assertEquals(listOf(2L, 3L), asked)
    }

    @Test
    fun `답한 것이 자리를 비켜 주지 않는다`() {
        val brakes = listOf(
            brake(1, 90f, 40f, Cause.OTHER),
            brake(2, 80f, 45f),
            brake(3, 60f, 50f),
        )
        // 1번이 가장 세고 이미 답했다. 2번만 남고 3번까지 내려가지 않는다.
        assertEquals(listOf(2L), BrakeQuestion.pending(brakes).map { it.id })
    }

    @Test
    fun `속도를 모르면 뒤로 밀린다`() {
        val known = brake(1, 70f, 40f)
        val unknown = IncidentRecord(
            id = 2, sessionId = 1, atMs = 2, type = IncidentType.SUDDEN_BRAKE,
            trigger = Trigger.HARD_BRAKE, latitude = null, longitude = null,
            cause = null, labeledAtMs = null,
            vehicle = VehicleSnapshot(null, null, "D", true, false, null),
        )
        assertEquals(listOf(1L, 2L), BrakeQuestion.asked(listOf(unknown, known)).map { it.id })
    }
}
