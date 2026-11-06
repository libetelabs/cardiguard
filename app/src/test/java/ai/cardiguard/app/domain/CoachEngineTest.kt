package ai.cardiguard.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachEngineTest {
    private val engine = CoachEngine().also { it.onSignal(Signal.Gear(parked = false, atMs = 0)) }

    private fun brake(startMs: Long, from: Float = 80f, to: Float = 50f): List<Effect> {
        engine.onSignal(Signal.Speed(from, startMs))
        return engine.onSignal(Signal.Speed(to, startMs + 1_000))
    }

    private fun say(text: String, atMs: Long) = engine.onSignal(Signal.Voice(text, atMs))
    private fun List<Effect>.lines() = filterIsInstance<Effect.Speak>().map { it.line }

    @Test
    fun `급제동은 사건으로 저장하고 짧게 말한다`() {
        val effects = brake(1_000)
        assertEquals(IncidentType.SUDDEN_BRAKE, engine.state.active?.type)
        assertEquals(80f, engine.state.active?.vehicle?.speedBefore)
        assertTrue(effects.any { it is Effect.Save })
        assertEquals(listOf(Playbook.guide(IncidentType.SUDDEN_BRAKE).drivingLine), effects.lines())
    }

    @Test
    fun `완만한 감속과 저속 감속은 무시한다`() {
        assertTrue(brake(1_000, 80f, 70f).isEmpty())
        assertTrue(brake(20_000, 25f, 0f).isEmpty())
        assertNull(engine.state.active)
    }

    @Test
    fun `페달을 안 밟은 감속은 급제동이 아니다`() {
        engine.onSignal(Signal.BrakePedal(false, 500))
        assertTrue(brake(1_000).isEmpty())
    }

    @Test
    fun `급제동 후 P 전환이면 접촉 여부를 묻고 네면 사고로 격상한다`() {
        val brakeId = brake(1_000).filterIsInstance<Effect.Save>().single().record.id
        val asked = engine.onSignal(Signal.Gear(parked = true, atMs = 5_000))
        assertEquals(Question.WAS_COLLISION, engine.state.pending)
        assertEquals(Effect.Speak(Playbook.ASK_COLLISION, thenListen = true), asked.last())

        val answered = say("네", 6_000)
        assertEquals(IncidentType.COLLISION, engine.state.active?.type)
        assertEquals(brakeId, engine.state.active?.id)
        assertTrue(answered.any { it is Effect.Save })
    }

    @Test
    fun `급제동을 세 번 반복하면 위협 여부를 묻는다`() {
        brake(1_000)
        brake(20_000)
        val third = brake(40_000)
        assertEquals(Question.UNDER_THREAT, engine.state.pending)
        assertEquals(listOf(Playbook.ASK_THREAT), third.lines())

        val answered = say("응", 45_000)
        assertEquals(IncidentType.THREAT, engine.state.active?.type)
        assertTrue(answered.contains(Effect.LockDoors))
    }

    @Test
    fun `위협이면 문을 잠그고 112는 확인받은 뒤에만 건다`() {
        val effects = say("쟤가 계속 따라와", 1_000)
        assertTrue(effects.contains(Effect.LockDoors))
        assertFalse(effects.contains(Effect.DialPolice))
        assertEquals(Question.CALL_POLICE, engine.state.pending)
        assertEquals(listOf(Effect.DialPolice), say("네 연결해줘", 2_000))
    }

    @Test
    fun `급제동은 안내가 겹쳐도 한 번마다 기록한다`() {
        brake(1_000)
        val second = brake(20_000)
        val saved = second.filterIsInstance<Effect.Save>().single().record
        assertEquals(IncidentType.SUDDEN_BRAKE, saved.type)
        assertNull(saved.cause)
        assertEquals(30f, brake(40_000, 90f, 30f).filterIsInstance<Effect.Save>().single().record.vehicle.speedAfter)
    }

    @Test
    fun `위협 질문에 네라고 하면 최근 급제동의 원인을 저장한다`() {
        brake(1_000)
        brake(20_000)
        brake(40_000)
        val effects = say("네", 45_000)
        val saved = effects.filterIsInstance<Effect.Save>().map { it.record }
        assertEquals(3, saved.filter { it.cause == Cause.THREAT && it.labeledAtMs == 45_000L }.distinctBy { it.id }.size)
        assertTrue(saved.last().type == IncidentType.THREAT)
        assertEquals(Trigger.HARD_BRAKE, saved.last().trigger)
    }

    @Test
    fun `위협 질문에 아니라고 하면 원인을 비워 정차 후에 묻는다`() {
        brake(1_000)
        brake(20_000)
        brake(40_000)
        assertTrue(say("아니", 45_000).none { it is Effect.Save })
    }

    @Test
    fun `새 주행에서는 지난 급제동을 세지 않고 가벼운 안내를 끝낸다`() {
        brake(1_000)
        brake(20_000)
        engine.onSignal(Signal.Gear(parked = true, atMs = 30_000))
        say("아니", 31_000)
        engine.onSignal(Signal.Gear(parked = false, atMs = 40_000))
        assertNull(engine.state.active)
        assertEquals(listOf(Playbook.guide(IncidentType.SUDDEN_BRAKE).drivingLine), brake(50_000).lines())
    }

    @Test
    fun `P 전환 때 주행 요약을 만든다`() {
        engine.onSignal(Signal.Speed(60f, 1_000))
        engine.onSignal(Signal.Speed(60f, 4_000))
        brake(10_000)
        val session = engine.onSignal(Signal.Gear(parked = true, atMs = 60_000))
            .filterIsInstance<Effect.SessionEnded>().single().session
        assertEquals(0L, session.startedAtMs)
        assertEquals(60_000L, session.endedAtMs)
        assertEquals(1, session.hardBrakes)
        assertEquals(1, session.events.size)
        assertTrue(session.distanceKm > 0.05)
    }

    @Test
    fun `정차 후 라벨을 진행 중 사건에도 반영한다`() {
        brake(1_000)
        val record = engine.state.active!!.labeled(Cause.OTHER, 2_000)
        engine.onLabeled(record)
        assertEquals(Cause.OTHER, engine.state.active?.cause)
    }

    @Test
    fun `질문의 답을 못 알아들으면 한 번만 더 듣는다`() {
        say("도와줘", 1_000)
        val first = say("그리고", 2_000).filterIsInstance<Effect.Speak>().single()
        assertEquals(Playbook.NOT_UNDERSTOOD, first.line)
        assertTrue(first.thenListen)
        assertFalse(say("그리고", 3_000).filterIsInstance<Effect.Speak>().single().thenListen)
        assertEquals(listOf(Playbook.NO_CALL), say("아니요", 4_000).lines())
    }

    @Test
    fun `112를 거절해도 위협 사건은 유지하고 다시 요청하면 안내한다`() {
        say("도와줘", 1_000)
        assertEquals(listOf(Playbook.NO_CALL), say("아니", 2_000).lines())
        assertEquals(IncidentType.THREAT, engine.state.active?.type)

        val again = say("도와줘", 3_000)
        assertTrue(again.contains(Effect.LockDoors))
        assertEquals(listOf(Playbook.guide(IncidentType.THREAT).drivingLine), again.lines())
        assertFalse(again.any { it is Effect.Save })
    }

    @Test
    fun `위협 중 문이 열리면 경고만 하고 다시 잠그지 않는다`() {
        say("도와줘", 1_000)
        val effects = engine.onSignal(Signal.DoorOpened(2_000))
        assertEquals(listOf(Effect.Speak(Playbook.DOOR_WARNING)), effects)
    }

    @Test
    fun `평상시 문 열림에는 말하지 않는다`() {
        assertTrue(engine.onSignal(Signal.DoorOpened(1_000)).isEmpty())
    }

    @Test
    fun `에어백이 터지면 사고로 열고 문은 잠그지 않는다`() {
        val effects = engine.onSignal(Signal.Airbag(true, 1_000))
        assertEquals(IncidentType.COLLISION, engine.state.active?.type)
        assertEquals(Trigger.AIRBAG, engine.state.active?.trigger)
        assertFalse(effects.contains(Effect.LockDoors))
    }

    @Test
    fun `사고가 진행 중이면 낮은 단계 사건으로 덮지 않는다`() {
        engine.onSignal(Signal.Airbag(true, 1_000))
        assertTrue(brake(2_000).none { it is Effect.Speak })
        assertEquals(IncidentType.COLLISION, engine.state.active?.type)
    }

    @Test
    fun `괜찮다고 하면 안내를 끝낸다`() {
        brake(1_000)
        assertEquals(listOf(Playbook.DISMISSED), say("괜찮아", 2_000).lines())
        assertNull(engine.state.active)
    }

    @Test
    fun `P 전환 시 진행 중인 사고가 있으면 화면 안내로 넘긴다`() {
        say("방금 박았어", 1_000)
        val effects = engine.onSignal(Signal.Gear(parked = true, atMs = 2_000))
        assertEquals(listOf(Playbook.PARKED_READY), effects.lines())
        assertNotNull(engine.state.active)
    }
}
