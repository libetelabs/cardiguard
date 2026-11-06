package ai.cardiguard.app.domain

/** 차량과 음성에서 들어오는 입력. SDK 타입을 domain 밖에 둬서 JVM 테스트가 가능하다. */
sealed interface Signal {
    val atMs: Long

    data class Speed(val kmh: Float, override val atMs: Long) : Signal
    data class BrakePedal(val pressed: Boolean, override val atMs: Long) : Signal
    data class Gear(val parked: Boolean, override val atMs: Long) : Signal
    data class Airbag(val deployed: Boolean, override val atMs: Long) : Signal
    data class DoorOpened(override val atMs: Long) : Signal
    data class Voice(val text: String, override val atMs: Long) : Signal
}

enum class IncidentType { MANUAL_RECORD, SUDDEN_BRAKE, BREAKDOWN, THREAT, COLLISION }

enum class Trigger { HARD_BRAKE, AIRBAG, VOICE }

/** 급제동의 원인. 운전자가 확인한 값이며 앱이 추정하지 않는다. */
enum class Cause { OTHER, MINE, SLOWDOWN, THREAT }

/** 기록 순간의 차량 신호. 값을 읽지 못하면 null로 둔다. */
data class VehicleSnapshot(
    val speedBefore: Float?,
    val speedAfter: Float?,
    val gear: String?,
    val brakePressed: Boolean?,
    val airbag: Boolean,
    val doorLocked: Boolean?,
)

/** 사건 한 건. 급제동은 안내가 겹쳐도 한 번마다 별도 기록으로 남는다. */
data class IncidentRecord(
    val id: Long,
    val sessionId: Long?,
    val atMs: Long,
    val type: IncidentType,
    val trigger: Trigger,
    val latitude: Double?,
    val longitude: Double?,
    val cause: Cause?,
    val labeledAtMs: Long?,
    val vehicle: VehicleSnapshot,
) {
    fun labeled(cause: Cause, atMs: Long) = copy(cause = cause, labeledAtMs = atMs)
}

/** 기어 D 진입부터 P 전환까지의 주행 한 번. */
data class DriveSession(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val distanceKm: Double,
    val hardBrakes: Int,
    val events: List<Long>,
)

enum class Question { WAS_COLLISION, UNDER_THREAT, CALL_POLICE }

/** 엔진의 판단 결과. 실제 실행은 앱 계층이 맡는다. */
sealed interface Effect {
    data class Speak(val line: String, val thenListen: Boolean = false) : Effect
    data object LockDoors : Effect
    data object DialPolice : Effect
    data class Save(val record: IncidentRecord) : Effect
    data class SessionEnded(val session: DriveSession) : Effect
}
