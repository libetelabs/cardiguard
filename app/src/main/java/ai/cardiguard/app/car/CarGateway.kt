package ai.cardiguard.app.car

import ai.cardiguard.app.domain.Signal

data class Place(val latitude: Double, val longitude: Double)

/** 차량 신호와 제어. 실패해도 앱이 멈추지 않도록 구현체가 예외를 삼키고 onIssue로 알린다. */
interface CarGateway {
    fun start(onSignal: (Signal) -> Unit, onIssue: (String) -> Unit)
    fun stop()
    fun lockDoors(locked: Boolean = true)
    fun lastPlace(): Place?
    fun doorsLocked(): Boolean?
}

interface VoiceGateway {
    fun start(onHeard: (String) -> Unit, onIssue: (String) -> Unit, onSpeaking: (Boolean) -> Unit)
    fun stop()
    fun speak(line: String, thenListen: Boolean)
    fun listen()
}
