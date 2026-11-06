package ai.cardiguard.app.core

import ai.cardiguard.app.car.CarGateway
import ai.cardiguard.app.car.PleosCarGateway
import ai.cardiguard.app.car.PleosVoiceGateway
import ai.cardiguard.app.car.PrefsIncidentLog
import ai.cardiguard.app.car.VoiceGateway
import ai.cardiguard.app.domain.Cause
import ai.cardiguard.app.domain.CoachEngine
import ai.cardiguard.app.domain.DriveSession
import ai.cardiguard.app.domain.Effect
import ai.cardiguard.app.domain.IncidentLog
import ai.cardiguard.app.domain.IncidentRecord
import ai.cardiguard.app.domain.Signal
import ai.cardiguard.app.domain.Trigger
import android.app.Application
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class CoachUi(
    val engine: CoachEngine.State = CoachEngine.State(),
    val lastLine: String? = null,
    val history: List<IncidentRecord> = emptyList(),
    val lastSession: DriveSession? = null,
    val speaking: Boolean = false,
    val issues: List<String> = emptyList(),
)

/**
 * 감시는 화면보다 오래 산다.
 *
 * 급제동과 에어백은 운전자가 우리 앱을 보고 있을 때만 일어나지 않는다.
 * 그래서 엔진과 차량 연결을 화면(ViewModel)이 아니라 프로세스에 둔다.
 * 프로세스를 깨워 두는 일은 GuardService 가 맡는다.
 */
class CoachCore private constructor(app: Application) {

    private val car: CarGateway = PleosCarGateway(app)
    private val voice: VoiceGateway = PleosVoiceGateway(app)
    private val log: IncidentLog = PrefsIncidentLog(app)
    private val engine = CoachEngine()
    private val main = Handler(Looper.getMainLooper())

    private val mutableUi = MutableStateFlow(CoachUi(history = log.all(), lastSession = log.sessions().lastOrNull()))
    val ui: StateFlow<CoachUi> = mutableUi

    private val dialRequests = Channel<Unit>(Channel.CONFLATED)
    val dialPolice = dialRequests.receiveAsFlow()

    private var started = false
    private var demoDriving = false
    private var demoSpeed = 0f
    private val demoTick = object : Runnable {
        override fun run() {
            handle(Signal.Speed(demoSpeed, System.currentTimeMillis()))
            main.postDelayed(this, DEMO_TICK_MS)
        }
    }

    /** SDK 콜백은 Binder 스레드에서 올 수 있어 메인으로 모은다. */
    fun startSources() {
        if (started) return
        started = true
        val issue = { text: String -> main.post { addIssue(text) }; Unit }
        // 서비스 연결은 느릴 수 있어 메인 스레드를 막지 않는다. 막으면 시작 직후 ANR로 죽는다.
        thread(name = "cardiguard-start") {
            car.start({ signal -> main.post { if (!demoDriving || signal !is Signal.Speed) handle(signal) } }, issue)
            val speaking = { on: Boolean -> main.post { mutableUi.value = mutableUi.value.copy(speaking = on) }; Unit }
            runCatching { voice.start({ text -> main.post { hear(text) } }, issue, speaking) }
                .onFailure { e -> issue("음성: ${e.message}") }
        }
    }

    /** Virtual Console에 속도 입력이 없어, 시연 주행 중에는 일정 속도를 계속 넣어 거리를 쌓는다. */
    fun demoDrive(kmh: Float) {
        main.removeCallbacks(demoTick)
        demoDriving = true
        demoSpeed = kmh
        demoTick.run()
    }

    /** adb 시연용. 화면에서 가장 오래된 미표시 급제동에 원인을 붙인다. */
    fun demoLabel(cause: Cause) {
        val session = mutableUi.value.lastSession?.id ?: return
        val record = mutableUi.value.history.firstOrNull {
            it.sessionId == session && it.trigger == Trigger.HARD_BRAKE && it.cause == null
        }
        record?.let { label(it.id, cause) }
    }

    fun demoStop() {
        main.removeCallbacks(demoTick)
        demoDriving = false
        handle(Signal.Speed(0f, System.currentTimeMillis()))
    }

    fun demoBrake(from: Float, to: Float) {
        main.removeCallbacks(demoTick)
        demoDriving = true
        val now = System.currentTimeMillis()
        handle(Signal.BrakePedal(true, now))
        handle(Signal.Speed(from, now))
        main.postDelayed({
            // 느린 환경에서도 감지 구간을 벗어나지 않도록 시각을 고정한다.
            handle(Signal.Speed(to, now + DEMO_BRAKE_MS))
            handle(Signal.BrakePedal(false, now + DEMO_BRAKE_MS))
            main.postDelayed({ if (!engine.state.parked) demoDrive(from) }, DEMO_RECOVER_MS)
        }, DEMO_BRAKE_MS)
    }

    fun handle(signal: Signal) {
        val effects = engine.onSignal(signal)
        effects.forEach(::run)
        mutableUi.value = mutableUi.value.copy(engine = engine.state)
    }

    fun hear(text: String) = handle(Signal.Voice(text, System.currentTimeMillis()))

    /** 디버그 시연 전용. 잠금 동작을 확인하려면 먼저 풀어야 한다. */
    fun unlockDoors() = car.lockDoors(locked = false)

    fun listen() {
        runCatching { voice.listen() }.onFailure { addIssue("음성 인식: ${it.message}") }
    }

    /** 운전자가 정차 후 고른 원인을 저장한다. 주행 중에는 화면에 나오지 않는다. */
    fun label(recordId: Long, cause: Cause) {
        val record = log.all().firstOrNull { it.id == recordId } ?: return
        val labeled = record.labeled(cause, System.currentTimeMillis())
        engine.onLabeled(labeled)
        save(labeled)
    }

    fun dismiss() {
        engine.dismiss()
        mutableUi.value = mutableUi.value.copy(engine = engine.state, lastLine = null)
    }

    private fun run(effect: Effect) {
        when (effect) {
            is Effect.Speak -> {
                mutableUi.value = mutableUi.value.copy(lastLine = effect.line)
                runCatching { voice.speak(effect.line, effect.thenListen) }
                    .onFailure { addIssue("음성 안내: ${it.message}") }
            }
            Effect.LockDoors -> car.lockDoors()
            Effect.DialPolice -> dialRequests.trySend(Unit)
            is Effect.Save -> save(effect.record)
            is Effect.SessionEnded -> {
                log.save(effect.session)
                mutableUi.value = mutableUi.value.copy(lastSession = effect.session)
            }
        }
    }

    /** 격상이나 라벨링으로 다시 저장해도 처음 남긴 위치와 문 상태는 유지한다. */
    private fun save(record: IncidentRecord) {
        val saved = log.all().firstOrNull { it.id == record.id }
        val place = car.lastPlace()
        log.save(
            record.copy(
                latitude = saved?.latitude ?: place?.latitude,
                longitude = saved?.longitude ?: place?.longitude,
                vehicle = record.vehicle.copy(doorLocked = saved?.vehicle?.doorLocked ?: car.doorsLocked()),
            ),
        )
        mutableUi.value = mutableUi.value.copy(history = log.all())
    }

    private fun addIssue(text: String) {
        mutableUi.value = mutableUi.value.copy(issues = (mutableUi.value.issues + text).takeLast(5))
    }

    /** 서비스가 내려갈 때만 부른다. 화면이 닫히는 것과는 무관하다. */
    fun stopSources() {
        if (!started) return
        started = false
        main.removeCallbacksAndMessages(null)
        car.stop()
        runCatching { voice.stop() }
    }

    /** 화면이 앞에 있는지. 뒤에 있을 때만 알림으로 끌어온다. */
    @Volatile var uiVisible: Boolean = false

    companion object {
        private const val DEMO_TICK_MS = 1_000L
        private const val DEMO_BRAKE_MS = 1_000L
        private const val DEMO_RECOVER_MS = 3_000L

        @Volatile private var instance: CoachCore? = null

        /** 프로세스에 하나뿐이다. 화면과 서비스가 같은 것을 본다. */
        fun get(app: Application): CoachCore =
            instance ?: synchronized(this) { instance ?: CoachCore(app).also { instance = it } }
    }
}