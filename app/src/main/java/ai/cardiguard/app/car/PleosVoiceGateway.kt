package ai.cardiguard.app.car

import ai.cardiguard.app.domain.Playbook
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode

private const val TAG = "Cardiguard"
private const val ENGINE_RETRY_DELAY_MS = 3_000L

/** 서버 자격증명 없이 쓰도록 ON_DEVICE로 고정한다. 안내 문구는 항상 화면에도 표시된다. */
class PleosVoiceGateway(context: Context) : VoiceGateway {
    private val tts = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    private val main = Handler(Looper.getMainLooper())
    private var listenAfterSpeech = false
    private var lastLine: String? = null
    private var retried = false
    private var onHeard: (String) -> Unit = {}
    private var onIssue: (String) -> Unit = {}
    private var onSpeaking: (Boolean) -> Unit = {}

    private val speechEvents = object : EventListener {
        override fun onStart() {
            Log.i(TAG, "tts start")
            onSpeaking(true)
        }

        override fun onReady() = Unit
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() {
            Log.i(TAG, "tts done")
            onSpeaking(false)
            if (listenAfterSpeech) {
                listenAfterSpeech = false
                listen()
            }
        }

        /** 온디바이스 엔진은 부팅 직후 준비가 늦어 첫 요청이 실패할 수 있다. 한 번만 다시 말한다. */
        override fun onError(error: TtsError) {
            Log.w(TAG, "tts error $error")
            onSpeaking(false)
            val line = lastLine
            if (error == TtsError.ON_DEVICE_ENGINE_ERROR && line != null && !retried) {
                retried = true
                main.postDelayed({ tts.speak(line, true) }, ENGINE_RETRY_DELAY_MS)
            } else {
                onIssue("음성 안내: $error")
            }
        }
    }

    private val recognition = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (isFinal && text.isNotBlank()) onHeard(text)
        }

        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(error: SttError) = onIssue("음성 인식: $error")
    }

    override fun start(onHeard: (String) -> Unit, onIssue: (String) -> Unit, onSpeaking: (Boolean) -> Unit) {
        this.onHeard = onHeard
        this.onIssue = onIssue
        this.onSpeaking = onSpeaking
        tts.initialize()
        tts.addEventListener(speechEvents)
        tts.setLocale(Locale.KOREA) // 국가 코드가 없으면 온디바이스 엔진이 거절한다
        stt.initialize()
        stt.addListener(recognition)
        stt.setLocale(Locale.KOREA)
        // 첫 합성 때 모델을 올리느라 수십 초 걸릴 수 있어, 위기 안내가 늦지 않도록 시작할 때 미리 말한다.
        tts.speak(Playbook.WARM_UP, true)
    }

    override fun speak(line: String, thenListen: Boolean) {
        Log.i(TAG, "tts request: $line")
        listenAfterSpeech = thenListen
        lastLine = line
        retried = false
        tts.speak(line, true)
    }

    override fun listen() = stt.request()

    override fun stop() {
        main.removeCallbacksAndMessages(null)
        tts.removeEventListener(speechEvents)
        stt.removeListener(recognition)
        tts.release()
        stt.release()
    }
}
