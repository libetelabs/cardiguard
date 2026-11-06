package ai.cardiguard.app.ui

import ai.cardiguard.app.core.CoachCore
import ai.cardiguard.app.core.CoachUi
import ai.cardiguard.app.domain.Cause
import ai.cardiguard.app.domain.Signal
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 화면이 코어를 들여다보는 창. 상태도 수명도 여기 있지 않다.
 * 화면이 닫혀도 감시는 계속되어야 하므로 onCleared 에서 아무것도 멈추지 않는다.
 */
class CoachViewModel(app: Application) : AndroidViewModel(app) {
    private val core = CoachCore.get(app)

    val ui: StateFlow<CoachUi> get() = core.ui
    val dialPolice: Flow<Unit> get() = core.dialPolice

    fun startSources() = core.startSources()
    fun handle(signal: Signal) = core.handle(signal)
    fun hear(text: String) = core.hear(text)
    fun listen() = core.listen()
    fun label(recordId: Long, cause: Cause) = core.label(recordId, cause)
    fun dismiss() = core.dismiss()
    fun unlockDoors() = core.unlockDoors()
    fun demoDrive(kmh: Float) = core.demoDrive(kmh)
    fun demoStop() = core.demoStop()
    fun demoBrake(from: Float, to: Float) = core.demoBrake(from, to)
    fun demoLabel(cause: Cause) = core.demoLabel(cause)
}
