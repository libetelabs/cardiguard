package ai.cardiguard.app.domain

/**
 * 신호를 받아 안내를 결정하는 상태 기계.
 * 주행 중에는 한 문장만 말하고, 상세 절차는 P 전환 후 화면으로 넘긴다.
 * 문 잠금은 위협 상황에서만 한다. 사고 때 잠그면 구조와 대피를 막을 수 있다.
 */
class CoachEngine(
    private val detector: HardBrakeDetector = HardBrakeDetector(),
    private val repeatWindowMs: Long = 180_000,
    private val repeatCount: Int = 3,
    private val askCollisionWindowMs: Long = 60_000,
) {
    data class State(
        val parked: Boolean = true,
        val speedKmh: Float? = null,
        val active: IncidentRecord? = null,
        val pending: Question? = null,
    )

    var state = State()
        private set

    private val hardBrakes = ArrayDeque<IncidentRecord>()
    private var session: DriveSession? = null
    private var lastSpeed: Signal.Speed? = null
    private var airbag = false
    private var lastId = 0L
    private var reasked = false

    fun onSignal(signal: Signal): List<Effect> = when (signal) {
        is Signal.Speed -> onSpeed(signal)
        is Signal.BrakePedal -> {
            detector.brakePressed = signal.pressed
            emptyList()
        }
        is Signal.Gear -> onGear(signal)
        is Signal.Airbag ->
            if (signal.deployed) {
                airbag = true
                open(IncidentType.COLLISION, Trigger.AIRBAG, signal.atMs)
            } else emptyList()
        is Signal.DoorOpened ->
            if (state.active?.type == IncidentType.THREAT) listOf(Effect.Speak(Playbook.DOOR_WARNING)) else emptyList()
        is Signal.Voice -> onVoice(signal)
    }

    /** 화면의 안내 종료 버튼과 음성 "괜찮아"가 함께 쓴다. */
    fun dismiss() {
        state = state.copy(active = null, pending = null)
    }

    /** 앱 재시작이나 화면 라벨링으로 바뀐 기록을 진행 중인 사건에도 반영한다. */
    fun onLabeled(record: IncidentRecord) {
        if (state.active?.id == record.id) state = state.copy(active = record)
        hardBrakes.replaceAll { if (it.id == record.id) record else it }
    }

    private fun onSpeed(signal: Signal.Speed): List<Effect> {
        addDistance(signal)
        state = state.copy(speedKmh = signal.kmh)
        val peak = detector.onSpeed(signal) ?: return emptyList()

        // 안내는 겹쳐도 원인을 따로 물을 수 있도록 급제동마다 기록한다.
        val record = newRecord(IncidentType.SUDDEN_BRAKE, Trigger.HARD_BRAKE, signal.atMs, peak, signal.kmh)
        hardBrakes.addLast(record)
        while (hardBrakes.first().atMs < signal.atMs - repeatWindowMs) hardBrakes.removeFirst()
        session = session?.let { it.copy(hardBrakes = it.hardBrakes + 1) }

        val effects = listOf(Effect.Save(record)) + open(record)
        if (hardBrakes.size < repeatCount || state.active?.type != IncidentType.SUDDEN_BRAKE) return effects
        state = state.copy(pending = Question.UNDER_THREAT)
        return effects.filterNot { it is Effect.Speak } + Effect.Speak(Playbook.ASK_THREAT, thenListen = true)
    }

    private fun addDistance(signal: Signal.Speed) {
        val previous = lastSpeed
        lastSpeed = signal
        if (previous == null) return
        val elapsedMs = (signal.atMs - previous.atMs).coerceIn(0, MAX_SPEED_GAP_MS)
        session = session?.let { it.copy(distanceKm = it.distanceKm + previous.kmh * elapsedMs / MS_PER_HOUR) }
    }

    private fun newRecord(type: IncidentType, trigger: Trigger, atMs: Long, before: Float?, after: Float?): IncidentRecord {
        val current = session
        val record = IncidentRecord(
            id = nextId(atMs),
            sessionId = current?.id,
            atMs = atMs,
            type = type,
            trigger = trigger,
            latitude = null,
            longitude = null,
            cause = null,
            labeledAtMs = null,
            vehicle = VehicleSnapshot(before, after, if (state.parked) "P" else "D", detector.brakePressed, airbag, null),
        )
        session = current?.copy(events = current.events + record.id)
        return record
    }

    /** 앱을 다시 켜도 저장된 기록과 겹치지 않도록 시각에서 만든다. */
    private fun nextId(atMs: Long): Long {
        lastId = maxOf(lastId + 1, atMs)
        return lastId
    }

    private fun onGear(signal: Signal.Gear): List<Effect> {
        val wasParked = state.parked
        state = state.copy(parked = signal.parked)
        if (!signal.parked) {
            if (wasParked) startSession(signal.atMs)
            return emptyList()
        }
        if (wasParked) return emptyList()

        val ended = session?.copy(endedAtMs = signal.atMs)
        session = null
        val summary = listOfNotNull(ended?.let(Effect::SessionEnded))
        val active = state.active ?: return summary
        val justBraked = active.type == IncidentType.SUDDEN_BRAKE &&
            signal.atMs - active.atMs <= askCollisionWindowMs
        if (!justBraked) return summary + Effect.Speak(Playbook.PARKED_READY)

        state = state.copy(pending = Question.WAS_COLLISION)
        return summary + Effect.Speak(Playbook.ASK_COLLISION, thenListen = true)
    }

    /** 반복 급제동은 한 주행 안에서만 센다. 위협과 사고 안내는 다시 출발해도 이어 간다. */
    private fun startSession(atMs: Long) {
        session = DriveSession(nextId(atMs), atMs, null, 0.0, 0, emptyList())
        hardBrakes.clear()
        val type = state.active?.type
        if (type != IncidentType.THREAT && type != IncidentType.COLLISION) state = state.copy(active = null, pending = null)
    }

    private fun onVoice(signal: Signal.Voice): List<Effect> {
        val intent = VoiceIntentParser.parse(signal.text)
        val pending = state.pending
        if (pending != null && (intent == VoiceIntent.YES || intent == VoiceIntent.NO)) {
            state = state.copy(pending = null)
            reasked = false
            return answer(pending, intent == VoiceIntent.YES, signal.atMs)
        }
        return when (intent) {
            VoiceIntent.RECORD -> open(IncidentType.MANUAL_RECORD, Trigger.VOICE, signal.atMs)
            VoiceIntent.THREAT -> open(IncidentType.THREAT, Trigger.VOICE, signal.atMs)
            VoiceIntent.COLLISION -> open(IncidentType.COLLISION, Trigger.VOICE, signal.atMs)
            VoiceIntent.BREAKDOWN -> open(IncidentType.BREAKDOWN, Trigger.VOICE, signal.atMs)
            VoiceIntent.CALL_POLICE -> listOf(Effect.DialPolice)
            VoiceIntent.NO -> if (state.active != null) {
                dismiss()
                listOf(Effect.Speak(Playbook.DISMISSED))
            } else emptyList()
            // 질문의 답을 못 알아들으면 한 번만 더 듣는다. 소음 속에서 계속 말을 걸지 않기 위해서다.
            VoiceIntent.YES, VoiceIntent.UNKNOWN -> {
                val listenAgain = pending != null && !reasked
                reasked = listenAgain
                listOf(Effect.Speak(Playbook.NOT_UNDERSTOOD, thenListen = listenAgain))
            }
        }
    }

    private fun answer(question: Question, yes: Boolean, atMs: Long): List<Effect> = when (question) {
        Question.WAS_COLLISION ->
            if (yes) open(IncidentType.COLLISION, Trigger.VOICE, atMs) else listOf(Effect.Speak(Playbook.PARKED_READY))
        Question.UNDER_THREAT ->
            if (yes) labelRecentBrakes(atMs) + open(IncidentType.THREAT, Trigger.VOICE, atMs)
            else listOf(Effect.Speak(Playbook.DISMISSED))
        Question.CALL_POLICE ->
            if (yes) listOf(Effect.DialPolice) else listOf(Effect.Speak(Playbook.NO_CALL))
    }

    /** 위협이라는 운전자의 답을 버리지 않는다. 아니라는 답은 원인을 밝힌 것이 아니라서 비워 둔다. */
    private fun labelRecentBrakes(atMs: Long): List<Effect> {
        val labeled = hardBrakes.filter { it.cause == null }.map { it.labeled(Cause.THREAT, atMs) }
        labeled.forEach(::onLabeled)
        return labeled.map(Effect::Save)
    }

    /** 위기 중 같은 도움 요청에 침묵하지 않도록 안내를 다시 말한다. */
    private fun repeatGuide(type: IncidentType): List<Effect> = buildList {
        if (type == IncidentType.THREAT) add(Effect.LockDoors)
        add(Effect.Speak(Playbook.guide(type).drivingLine))
    }

    private fun open(type: IncidentType, trigger: Trigger, atMs: Long): List<Effect> {
        val current = state.active
        if (current?.type == type && trigger == Trigger.VOICE) return repeatGuide(type)
        if (current != null && type.ordinal <= current.type.ordinal) return emptyList()

        val speed = state.speedKmh
        // 격상해도 처음 감지한 계기는 남겨야 급제동 목록에서 빠지지 않는다.
        val base = current?.copy(type = type) ?: newRecord(type, trigger, atMs, speed, speed)
        val record = if (type == IncidentType.THREAT && base.cause == null) base.labeled(Cause.THREAT, atMs) else base
        return listOf(Effect.Save(record)) + guide(record)
    }

    /** 진행 중인 사건보다 심각도가 같거나 낮으면 새로 열지 않고, 높으면 같은 사건을 격상한다. */
    private fun open(record: IncidentRecord): List<Effect> {
        val current = state.active
        if (current != null && record.type.ordinal <= current.type.ordinal) return emptyList()
        return guide(record)
    }

    private fun guide(record: IncidentRecord): List<Effect> {
        val asksPolice = record.type == IncidentType.THREAT
        state = state.copy(active = record, pending = if (asksPolice) Question.CALL_POLICE else null)

        val line = Playbook.guide(record.type).drivingLine
        return buildList {
            if (asksPolice) add(Effect.LockDoors)
            add(Effect.Speak(if (asksPolice) "$line ${Playbook.ASK_CALL}" else line, thenListen = asksPolice))
        }
    }

    private companion object {
        const val MS_PER_HOUR = 3_600_000.0
        const val MAX_SPEED_GAP_MS = 5_000L
    }
}
