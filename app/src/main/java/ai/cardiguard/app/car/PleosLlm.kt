package ai.cardiguard.app.car

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode
import ai.pleos.playground.llm.listener.ResultListener
import android.content.Context
import android.util.Log

/**
 * 차 안의 모델. 우리가 모델을 들고 오지 않는다. Pleos 가 온디바이스로 제공한다.
 *
 * 쓰는 자리는 정차 중 두 곳뿐이다. 주행 중 안내 문구는 문구집에서만 나온다.
 * 사고 직후에 모델이 즉흥으로 지어낸 문장을 읽어 줄 수는 없다.
 */
class PleosLlm(context: Context) {
    private val llm = LLM(context, Mode.ON_DEVICE)
    private var ready = false

    fun start() {
        runCatching { llm.initialize(); ready = true }
            .onFailure { Log.w(TAG, "llm init 실패: ${it.message}") }
    }

    fun stop() {
        runCatching { llm.release() }
        ready = false
    }

    /** 한 번 묻고 한 번 받는다. 스트리밍은 화면이 필요할 때만 쓴다. */
    fun ask(prompt: String, onDone: (String) -> Unit, onFail: (String) -> Unit) {
        if (!ready) {
            onFail("llm 준비 안 됨")
            return
        }
        val buffer = StringBuilder()
        runCatching {
            llm.generateContent(prompt, object : ResultListener {
                override fun onResponse(text: String, finished: Boolean) {
                    buffer.append(text)
                    if (finished) onDone(buffer.toString())
                }

                override fun onError(message: String) = onFail(message)
            })
        }.onFailure { onFail(it.message ?: "알 수 없는 오류") }
    }

    private companion object {
        const val TAG = "Cardiguard"
    }
}
