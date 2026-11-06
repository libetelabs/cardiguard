package ai.cardiguard.app.ui

import ai.cardiguard.app.car.PleosLlm
import ai.cardiguard.app.core.CoachCore
import ai.cardiguard.app.domain.Cause
import ai.cardiguard.app.domain.Signal
import ai.cardiguard.app.service.GuardService
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: CoachViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // 권한이 늦게 들어와도 이미 떠 있는 서비스가 받아 간다.
        GuardService.start(this)
    }

    /** 에뮬레이터 전용 시연 입력. `adb shell am broadcast -a ai.cardiguard.app.DEMO --es signal brake` */
    private val demoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            when (intent.getStringExtra("signal")) {
                "drive" -> {
                    viewModel.handle(Signal.Gear(false, now))
                    viewModel.demoDrive(intent.getIntExtra("kmh", 80).toFloat())
                }
                "park" -> {
                    viewModel.demoStop()
                    viewModel.handle(Signal.Gear(true, now))
                }
                "airbag" -> viewModel.handle(Signal.Airbag(true, now))
                "door" -> viewModel.handle(Signal.DoorOpened(now))
                "unlock" -> viewModel.unlockDoors()
                "label" -> intent.getStringExtra("cause")?.let { viewModel.demoLabel(Cause.valueOf(it)) }
                "brake" -> viewModel.demoBrake(80f, 40f)
                "voice" -> intent.getStringExtra("text")?.let(viewModel::hear)
                // 차 안의 모델이 실제로 답하는지 확인하는 자리. 시연 빌드에서만 열린다.
                "llm" -> probeLlm(intent.getStringExtra("text") ?: "안녕하세요. 한 문장으로 자기소개 해 주세요.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (demoAllowed()) {
            val filter = IntentFilter("ai.cardiguard.app.DEMO")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(demoReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(demoReceiver, filter)
            }
        }
        // 감시는 권한 대화상자를 기다리지 않는다. 대화상자가 떠 있는 동안에도 신호는 온다.
        GuardService.start(this)

        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO, "android.car.permission.CAR_SPEED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        permissions.launch(needed.toTypedArray())

        setContent {
            val ui by viewModel.ui.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { viewModel.dialPolice.collect { dialPolice() } }
            CoachScreen(
                ui = ui,
                onListen = viewModel::listen,
                onDial = ::dialPolice,
                onDismiss = viewModel::dismiss,
                onSignal = viewModel::handle,
                onDemoDrive = { viewModel.demoDrive(80f) },
                onDemoPark = viewModel::demoStop,
                onDemoBrake = { viewModel.demoBrake(80f, 40f) },
                onLabel = viewModel::label,
                onHeard = viewModel::hear,
            )
        }
    }

    /** 화면이 앞에 있는 동안에는 알림으로 부르지 않는다. 이미 보고 있기 때문이다. */
    override fun onStart() {
        super.onStart()
        CoachCore.get(application).uiVisible = true
    }

    override fun onStop() {
        CoachCore.get(application).uiVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        if (demoAllowed()) unregisterReceiver(demoReceiver)
        super.onDestroy()
    }

    private fun probeLlm(prompt: String) {
        val t0 = System.currentTimeMillis()
        android.util.Log.i("CardiguardLLM", "probe 시작: $prompt")
        val llm = PleosLlm(applicationContext)
        llm.start()
        llm.ask(
            prompt,
            onDone = {
                android.util.Log.i("CardiguardLLM", "응답(${System.currentTimeMillis() - t0}ms): $it")
                llm.stop()
            },
            onFail = {
                android.util.Log.w("CardiguardLLM", "실패(${System.currentTimeMillis() - t0}ms): $it")
                llm.stop()
            },
        )
    }

    /** 자동 발신하지 않는다. 다이얼러를 열어 운전자가 확인 후 건다. */
    private fun dialPolice() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "전화 앱을 열 수 없어요. 112로 직접 연락하세요.", Toast.LENGTH_LONG).show()
        }
    }
}
