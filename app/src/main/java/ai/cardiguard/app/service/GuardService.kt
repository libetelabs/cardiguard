package ai.cardiguard.app.service

import ai.cardiguard.app.core.CoachCore
import ai.cardiguard.app.domain.IncidentRecord
import ai.cardiguard.app.domain.Playbook
import ai.cardiguard.app.ui.MainActivity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 감시를 화면에서 떼어내는 자리.
 *
 * 운전자는 내비나 음악을 띄워 두고 달린다. 그동안 카디가드 화면은 뒤로 가 있고,
 * 급제동과 사고는 바로 그때 일어난다. 액티비티에 붙어 있던 엔진을 이 서비스가
 * 대신 들고 있어서, 화면이 닫혀도 신호를 계속 받는다.
 *
 * 사고가 나면 화면을 스스로 끌어올리지 않고 알림을 띄운다. 주행 중에 다른 앱 위로
 * 화면을 가로채는 것은 그 자체가 위험이고, 운전자가 누를지 말지 정할 일이다.
 */
class GuardService : LifecycleService() {

    private lateinit var core: CoachCore
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var lastAlerted: Long? = null

    override fun onCreate() {
        super.onCreate()
        core = CoachCore.get(application)
        createChannels()
        startForeground(ONGOING_ID, ongoing(null, parked = true))
        core.startSources()

        // 상태가 바뀔 때만 알림을 고쳐 쓴다. 매 속도 신호마다 쓰면 알림이 깜빡인다.
        lifecycleScope.launch {
            core.ui.map { it.engine.active?.id to it.engine.parked }
                .distinctUntilChanged()
                .collect { (activeId, parked) ->
                    val active = core.ui.value.engine.active
                    notifications.notify(ONGOING_ID, ongoing(active, parked))
                    if (active != null && activeId != lastAlerted && !core.uiVisible) {
                        lastAlerted = activeId
                        notifications.notify(ALERT_ID, alert(active))
                    }
                    if (active == null) {
                        lastAlerted = null
                        notifications.cancel(ALERT_ID)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // 차가 메모리를 회수해도 다시 띄운다. 감시가 조용히 꺼져 있는 것이 가장 나쁘다.
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        core.stopSources()
        super.onDestroy()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "주행 감시", NotificationManager.IMPORTANCE_LOW).apply {
                description = "주행 중 급제동·사고 신호를 받는 동안 떠 있습니다."
                setShowBadge(false)
            },
        )
        notifications.createNotificationChannel(
            // 사고 알림은 소리와 함께 화면 위로 떠야 한다. 낮추면 놓친다.
            NotificationChannel(CHANNEL_ALERT, "사고 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "급제동이나 사고를 감지하면 알립니다."
            },
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ongoing(active: IncidentRecord?, parked: Boolean): Notification =
        Notification.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("카디가드")
            .setContentText(
                when {
                    active != null -> Playbook.guide(active.type).title
                    parked -> "대기 중"
                    else -> "주행 감시 중"
                },
            )
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()

    private fun alert(record: IncidentRecord): Notification {
        val guide = Playbook.guide(record.type)
        return Notification.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(guide.title)
            .setContentText(guide.parkedSteps.firstOrNull() ?: guide.drivingLine)
            .setStyle(Notification.BigTextStyle().bigText(guide.drivingLine))
            .setContentIntent(openApp())
            // AAOS 는 서드파티 알림의 헤즈업을 카테고리로 거른다. 차량 경고로 올려야 운전 중 눈에 든다.
            .setCategory(CATEGORY_CAR_WARNING)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        /** AAOS 전용 카테고리. 프레임워크에 상수로 열려 있지 않아 문자열로 쓴다. */
        private const val CATEGORY_CAR_WARNING = "car_warning"
        private const val CHANNEL_ONGOING = "guard.ongoing"
        private const val CHANNEL_ALERT = "guard.alert"
        private const val ONGOING_ID = 1
        private const val ALERT_ID = 2

        fun start(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
