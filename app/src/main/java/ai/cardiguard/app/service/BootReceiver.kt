package ai.cardiguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 운전자가 앱을 열어야 감시가 시작된다면 그건 감시가 아니다.
 * 차가 깨어나면 화면과 무관하게 서비스부터 올린다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            GuardService.start(context)
        }
    }
}
