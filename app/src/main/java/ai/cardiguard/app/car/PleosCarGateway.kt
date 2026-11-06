package ai.cardiguard.app.car

import ai.cardiguard.app.domain.Signal
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

private const val TAG = "Cardiguard"
private const val LOCK_CHECK_DELAY_MS = 3_000L
private const val LOCATION_ATTEMPTS = 10
private const val LOCATION_RETRY_MS = 1_000L
private val CABIN_DOORS = listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)

class PleosCarGateway(context: Context) : CarGateway {
    private val appContext = context.applicationContext
    // SDK 호출이 서비스 연결을 기다리며 막힐 수 있어 메인 스레드가 아닌 전용 스레드에서 한다.
    private val workerThread = HandlerThread("cardiguard-car").apply { start() }
    private val worker = Handler(workerThread.looper)
    private val vehicle = Vehicle(appContext)
    private val location = FusedLocation(appContext)
    private val cabinDoors = CABIN_DOORS.map { DoorArea(it) }
    private val frontSeats = listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT).map { SeatArea(it) }

    private var car: Car? = null
    private var speedCallback: CarPropertyManager.CarPropertyEventCallback? = null
    private var place: Place? = null
    private var doorLocked: Boolean? = null
    private var issue: (String) -> Unit = {}
    private val doorListeners = mutableMapOf<DoorArea, DoorPositionListener>()
    private val airbagListeners = mutableMapOf<SeatArea, SeatAirbagDeployedListener>()
    private var gearListener: GearSelectionListener? = null
    private var brakeListener: BrakePedalStatusListener? = null
    private var locationListener: FusedLocationListener? = null

    override fun start(onSignal: (Signal) -> Unit, onIssue: (String) -> Unit) {
        issue = onIssue
        guarded("차량 서비스") { vehicle.initialize() }
        guarded("기어") { watchGear(onSignal) }
        guarded("브레이크") { watchBrake(onSignal) }
        guarded("도어") { watchDoors(onSignal) }
        guarded("에어백") { watchAirbags(onSignal) }
        guarded("속도") { watchSpeed(onSignal) }
        worker.post { guarded("위치") { watchLocation() } }
    }

    private fun watchGear(onSignal: (Signal) -> Unit) {
        val listener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                if (gear != null && gear != VehicleGear.GEAR_UNKNOWN) {
                    onSignal(Signal.Gear(gear == VehicleGear.GEAR_PARK, now()))
                }
            }

            override fun onFailed(exception: Exception) = issue("기어: ${exception.message}")
        }
        vehicle.getGear().registerGearSelection(listener)
        gearListener = listener
        readInitialGear(onSignal, attemptsLeft = 5)
    }

    /** 리스너는 값이 바뀔 때만 오므로 주행 중 앱을 켜면 첫 값을 직접 읽는다. 서비스 연결이 늦으면 재시도한다. */
    private fun readInitialGear(onSignal: (Signal) -> Unit, attemptsLeft: Int) {
        val retry = {
            if (attemptsLeft > 1) {
                worker.postDelayed({ readInitialGear(onSignal, attemptsLeft - 1) }, 1_000)
            } else {
                issue("기어 초기값을 읽지 못했어요")
            }
            Unit
        }
        guarded("기어") {
            vehicle.getGear().getCurrentGearSelectionState(
                onComplete = { gear ->
                    if (gear == null || gear == VehicleGear.GEAR_UNKNOWN) retry()
                    else {
                        onSignal(Signal.Gear(gear == VehicleGear.GEAR_PARK, now()))
                        readDoorLock()
                    }
                },
                onFailed = { retry() },
            )
        }
    }

    private fun readDoorLock() {
        guarded("문 잠금") {
            vehicle.getDoor().checkDoorLockCapabilities(
                { areas ->
                    areas.firstOrNull { area -> area.values.any { it in CABIN_DOORS } }?.let { area ->
                        vehicle.getDoor().getDoorLockState(area, onComplete = { doorLocked = it }, onFailed = {})
                    }
                },
                {},
            )
        }
    }

    private fun watchBrake(onSignal: (Signal) -> Unit) {
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) {
                if (status != null) onSignal(Signal.BrakePedal(status, now()))
            }

            override fun onFailed(exception: Exception) = issue("브레이크: ${exception.message}")
        }
        vehicle.getBrake().registerBrakePedalStatus(listener)
        brakeListener = listener
    }

    private fun watchDoors(onSignal: (Signal) -> Unit) {
        cabinDoors.forEach { area ->
            var wasOpen = false
            val listener = object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) {
                    val open = (pos ?: 0) > 0
                    if (open && !wasOpen) onSignal(Signal.DoorOpened(now()))
                    wasOpen = open
                }

                override fun onFailed(exception: Exception) = issue("도어: ${exception.message}")
            }
            vehicle.getDoor().registerDoorPosition(area, listener)
            doorListeners[area] = listener
        }
    }

    private fun watchAirbags(onSignal: (Signal) -> Unit) {
        frontSeats.forEach { area ->
            val listener = object : SeatAirbagDeployedListener {
                override fun onSeatAirbagDeployUpdated(deployed: Boolean?) {
                    if (deployed == true) onSignal(Signal.Airbag(true, now()))
                }

                override fun onFailed(exception: Exception) {
                    Log.w(TAG, "airbag", exception)
                    issue("에어백 신호를 쓸 수 없어요")
                }
            }
            vehicle.getSafety().registerSeatAirbagDeployed(area, listener)
            airbagListeners[area] = listener
        }
    }

    private fun watchSpeed(onSignal: (Signal) -> Unit) {
        val connected = Car.createCar(appContext) ?: error("Car 서비스 없음")
        car = connected
        val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val metersPerSecond = value.value as? Float ?: return
                onSignal(Signal.Speed(metersPerSecond * 3.6f, now()))
            }

            override fun onErrorEvent(propId: Int, zone: Int) = issue("속도 신호 오류")
        }
        manager.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST)
        speedCallback = callback
    }

    private fun watchLocation() {
        Log.i(TAG, "location supported=${location.isSupported()}")
        if (!location.isSupported()) error("미지원")
        location.initialize()
        registerLocation(attemptsLeft = LOCATION_ATTEMPTS)
    }

    /** initialize 직후에는 서비스 연결 전이라 등록이 실패할 수 있어, 연결될 때까지 다시 시도한다. */
    private fun registerLocation(attemptsLeft: Int) {
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                location?.let { place = Place(it.latitude, it.longitude) }
            }

            override fun onError(exception: Exception) = issue("위치: ${exception.message}")
        }
        try {
            location.registerFusedLocationCallback(listener)
            locationListener = listener
            location.getLastKnownLocation(listener)
            Log.i(TAG, "location registered")
        } catch (e: Exception) {
            Log.w(TAG, "location retry $attemptsLeft: ${e.message}")
            if (attemptsLeft > 1) {
                worker.postDelayed({ registerLocation(attemptsLeft - 1) }, LOCATION_RETRY_MS)
            } else {
                Log.w(TAG, "location failed", e)
                issue("위치 신호를 쓸 수 없어요")
            }
        }
    }

    /** 차종마다 잠금 구역이 달라 지원 구역을 조회해서 잠근다. 문 단위 구역이 없는 차가 있다. */
    override fun lockDoors(locked: Boolean) {
        guarded("문 잠금") {
            vehicle.getDoor().checkDoorLockCapabilities(
                { areas ->
                    val cabin = areas.filter { area -> area.values.any { it in CABIN_DOORS } }
                    Log.i(TAG, "lock areas ${cabin.map { it.label() }}")
                    if (cabin.isEmpty()) issue("문 잠금을 지원하지 않는 차량이에요")
                    cabin.forEach { lock(it, locked) }
                },
                { issue("문 잠금: ${it.message}") },
            )
        }
    }

    private fun lock(area: DoorArea, locked: Boolean) {
        guarded("문 잠금") {
            vehicle.getDoor().setDoorLock(area, locked, onFailed = { issue("문 잠금: ${it.message}") })
            worker.postDelayed({ confirmLocked(area, locked) }, LOCK_CHECK_DELAY_MS)
        }
    }

    /** 완료 콜백이 오지 않는 경우가 있어 일정 시간 뒤 상태를 직접 읽는다. */
    private fun confirmLocked(area: DoorArea, expected: Boolean) {
        vehicle.getDoor().getDoorLockState(
            area,
            onComplete = { state ->
                Log.i(TAG, "door ${area.label()} locked=$state")
                doorLocked = state
                if (state != expected) issue("문 잠금 상태가 달라요")
            },
            onFailed = { issue("문 잠금 확인: ${it.message}") },
        )
    }

    private fun DoorArea.label() = values.joinToString("+")

    override fun lastPlace(): Place? = place

    override fun doorsLocked(): Boolean? = doorLocked

    override fun stop() {
        worker.removeCallbacksAndMessages(null)
        guarded("해제") {
            gearListener?.let { vehicle.getGear().unregisterGearSelection(it) }
            brakeListener?.let { vehicle.getBrake().unregisterBrakePedalStatus(it) }
            doorListeners.forEach { (area, listener) -> vehicle.getDoor().unregisterDoorPosition(area, listener) }
            airbagListeners.forEach { (area, listener) -> vehicle.getSafety().unregisterSeatAirbagDeployed(area, listener) }
            locationListener?.let { location.unregisterFusedLocationCallback(it) }
            speedCallback?.let { (car?.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).unregisterCallback(it) }
        }
        guarded("해제") { vehicle.release() }
        guarded("해제") { location.release() }
        car?.disconnect()
        workerThread.quitSafely()
    }

    private inline fun guarded(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, label, e)
            issue("$label 신호를 쓸 수 없어요")
        }
    }

    private fun now() = System.currentTimeMillis()
}
