package com.yadea.smartmoto.server

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import com.yadea.smartmoto.R
import com.yadea.smartmoto.maps.MapPreferences
import com.yadea.smartmoto.ui.MapEntryActivity
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.*
import com.baidu.mapapi.model.LatLng
import com.blankj.utilcode.util.ToastUtils
import com.yadea.smartmoto.manager.FloatingViewManger
import com.yadea.smartmoto.manager.SearchManager
import com.yadea.smartmoto.model.MockMessageModel
import com.yadea.smartmoto.model.NaviType
import com.yadea.smartmoto.model.PoiInfoModel
import com.yadea.smartmoto.model.PoiInfoType
import com.yadea.smartmoto.utils.CalculationLogLatDistance
import com.yadea.smartmoto.utils.LocationUtils
import com.yadea.smartmoto.utils.MMKVUtils
import com.yadea.smartmoto.utils.RouteBindingUtils
import com.yadea.smartmoto.utils.Utils
import kotlin.random.Random

/**
 * @author jiayu.liu
 */
class GpsService : Service() {
    companion object {
        private val _location = androidx.lifecycle.MutableLiveData<LatLng?>()
        val currentLocation: androidx.lifecycle.LiveData<LatLng?> = _location
        @Volatile var isRunning = false
            private set
        const val ACTION_STOP = "com.yadea.smartmoto.STOP"
        const val ACTION_PAUSE = "com.yadea.smartmoto.PAUSE"
        const val ACTION_RESUME = "com.yadea.smartmoto.RESUME"
        private const val CHANNEL = "mock_location"
        private const val NOTIFICATION = 258
        fun start(context: Context, model: MockMessageModel) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, GpsService::class.java).putExtra("info", model))
            } catch (e: Exception) { ToastUtils.showLong("无法从后台启动定位，请返回雅迪智行后重试") }
        }
        private const val START_MOCK_LOCATION = 1001
        private const val START_MOCK_NAVI = 1002
        private const val START_MOCK_FILE_NAVI = 1003
    }

    private var locationManager: LocationManager? = null
    private var isStart = false
    private var destroying = false
    private lateinit var handle: Handler
    private var model: MockMessageModel? = null
    private var index = 0
    private val providerStr: String = LocationManager.GPS_PROVIDER
    private val networkStr: String = LocationManager.NETWORK_PROVIDER
    private var bearing: Float = 1.0f

    /**
     * 米/S
     */
    private var mSpeed: Float = 60 / 3.6f
    private var mCurrentLocation: LatLng? = null
    private var naviType: Int = NaviType.NONE
    private var isLocationAdjust = false

    /**
     * 模拟导航点更新间隔  单位：ms  小于等于1000ms
     */
    private var mNaviUpdateValue = 1000L

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        handle = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                //重置为默认值
                mNaviUpdateValue = 1000L
                when (msg.what) {
                    START_MOCK_LOCATION -> {
                        if (isStart) {
                            (msg.obj as PoiInfoModel?)?.latLng?.let {
                                if (mCurrentLocation == it && isLocationQuiver()) {
                                    CalculationLogLatDistance.getRandomLatLng(
                                        it,
                                        MMKVUtils.getLocationVibrationValue().toDouble()
                                    ).apply {
                                        //仅震动模式时，可以配置震动的频率
                                        mNaviUpdateValue =
                                            MMKVUtils.getLocationFrequencyValue().coerceIn(1, 3600) * 1000L
                                        startSimulateLocation(this, true)
                                    }
                                } else {
                                    mCurrentLocation = it
                                    startSimulateLocation(it, true)
                                }
                                handle.sendMessageDelayed(Message.obtain(msg), mNaviUpdateValue)
                            }
                        }
                    }

                    START_MOCK_NAVI, START_MOCK_FILE_NAVI -> {
                        if (isStart) {
                            (msg.obj as ArrayList<*>?)?.let {
                                if (it.isEmpty()) {
                                    return
                                }
                                if (index == 0) {
                                    mCurrentLocation = it[index] as LatLng
                                    index++
                                } else if (index < it.size) {
                                    mCurrentLocation = getLatLngNext(it)
                                }
                                FloatingViewManger.INSTANCE.updateNaviInfo(index, it.size)
                                startSimulateLocation(mCurrentLocation!!, false)
                                handle.sendMessageDelayed(Message.obtain(msg), mNaviUpdateValue)
                            }
                        }
                    }

                    else -> {
                    }
                }

            }
        }
        initLocationManager()
    }

    private fun initLocationManager() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    private fun initFloatingView() {
        //浮动窗
        FloatingViewManger.INSTANCE.run {
            if (isAddFloatingView) {
                return
            }
            addFloatViewToWindow()
            listener = object : FloatingViewManger.FloatingViewListener {
                override fun pause() {
                    removeGps()
                    handle.removeCallbacksAndMessages(null)
                }

                override fun reStart(isRest: Boolean) {
                    model?.run {
                        if (naviType == NaviType.LOCATION) {
                            mockLocation()
                        } else {
                            SearchManager.INSTANCE.polylineList.let {
                                if (it.isEmpty()) {
                                    FloatingViewManger.INSTANCE.stopMock()
                                    return
                                }
                                if (isRest || index >= it.size) {
                                    index = 0
                                }
                                sendHandler(
                                    START_MOCK_NAVI,
                                    it
                                )
                            }
                        }
                    }
                }

                override fun getNaviType(): Int {
                    return naviType
                }

                override fun changeLocation(latLng: LatLng, angle: Double) {
                    if (model == null) {
                        return
                    }
                    isLocationAdjust = true
                    bearing = angle.toFloat()
                    sendMockLocation(latLng)
                }

                override fun locationAdjustFinish() {
                    isLocationAdjust = false
                }

                override fun changeNaviInfo(index: Int, speed: Int) {
                    val safeSpeed = speed.coerceIn(1, 300)
                    MMKVUtils.setSpeed(safeSpeed)
                    model?.speed = safeSpeed
                    mSpeed = safeSpeed / 3.6f
                    //index==-1 仅调整速度
                    if (index >= 0) {
                        SearchManager.INSTANCE.polylineList.let {
                            if (index < it.size) {
                                mCurrentLocation = it[index]
                                this@GpsService.index = index
                            }
                        }
                    }
                    if (!isStart) {
                        reStart(false)
                    }
                }

                override fun switchLocation() {
                    if (model == null) {
                        return
                    }
                    //将模拟导航切换成模拟定位
                    model?.naviType = NaviType.LOCATION
                    sendMockLocation(mCurrentLocation)
                }

                /**
                 * 发送模拟定位Handler
                 */
                private fun sendMockLocation(location: LatLng?) {
                    if (location == null) {
                        ToastUtils.showShort("mock数据异常，此次模拟触发失败！")
                        return
                    }
                    PoiInfoModel(
                        latLng = location,
                        poiInfoType = PoiInfoType.LOCATION,
                    ).also {
                        model?.locationModel = it
                        sendHandler(
                            START_MOCK_LOCATION,
                            it
                        )
                    }
                }

            }
        }
    }

    fun getLatLngNext(polyline: ArrayList<*>): LatLng {
        require(polyline.isNotEmpty())
        val step = mSpeed.coerceIn(0.01f, 300 / 3.6f) * mNaviUpdateValue / 1000.0
        // Skip duplicates iteratively: imported files must never overflow the call stack.
        while (index < polyline.size) {
            val target = polyline[index] as LatLng
            val current = mCurrentLocation ?: target
            val distance = CalculationLogLatDistance.getDistance(current, target)
            val yaw = CalculationLogLatDistance.getYaw(current, target)
            if (yaw.isFinite()) bearing = yaw.toFloat()
            if (distance > step) {
                var next = CalculationLogLatDistance.getNextLonLat(current, yaw, step)
                if (MMKVUtils.isNaviRouteBindingSwitch()) {
                    val route = SearchManager.INSTANCE.polylineList
                    if (route.size > 1) {
                        // Restrict snapping to the current segment neighbourhood, not the entire track every tick.
                        val from = (index - 32).coerceAtLeast(0)
                        val to = (index + 65).coerceAtMost(route.size)
                        next = RouteBindingUtils.snapToPath(next, route.subList(from, to))
                    }
                }
                if (CalculationLogLatDistance.isCheckNaN(next)) { index++; return target }
                return next
            }
            index++
            if (distance > 0 || index == polyline.size) return target
        }
        return polyline.last() as LatLng
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (!MapPreferences.consented(this) || !Utils.isAllowMockLocation(this) ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            !Utils.checkFloatWindow(this)) {
            ToastUtils.showLong("无法启动：请检查隐私同意、精确定位、Root 模块和悬浮窗权限")
            stopSelf(); return START_NOT_STICKY
        }
        try {
            MapPreferences.requireProvider(this)
            showNotification()
            when (intent.action) {
                ACTION_PAUSE -> { handle.removeCallbacksAndMessages(null); removeGps() }
                ACTION_RESUME -> {
                    val data = model ?: run { stopSelf(); return START_NOT_STICKY }
                    if (data.naviType == NaviType.LOCATION) sendHandler(START_MOCK_LOCATION, data.locationModel)
                    else if (SearchManager.INSTANCE.polylineList.isNotEmpty()) sendHandler(START_MOCK_NAVI, SearchManager.INSTANCE.polylineList)
                }
                else -> {
                    model = intent.getParcelableExtra("info") ?: run { stopSelf(); return START_NOT_STICKY }
                    require(model!!.speed in 1..300) { "速度必须为 1–300 km/h" }
                    initFloatingView()
                    mockLocation()
                }
            }
            showNotification()
        } catch (e: Exception) {
            ToastUtils.showLong("定位服务启动失败，请检查权限和地图配置")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showNotification() {
        if (destroying) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "模拟定位运行状态", NotificationManager.IMPORTANCE_LOW))
        fun action(name: String, code: Int) = PendingIntent.getService(this, code,
            Intent(this, GpsService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MapEntryActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle("雅迪智行 · ${MapPreferences.selected?.title ?: "模拟定位"}")
            .setContentText(if (isStart) "模拟定位运行中" else "暂停保持当前位置，可继续或停止")
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(0, if (isStart) "暂停" else "继续", action(if (isStart) ACTION_PAUSE else ACTION_RESUME, 1))
            .addAction(0, "停止", action(ACTION_STOP, 2)).build()
        startForeground(NOTIFICATION, notification)
    }

    private fun mockLocation() {
        prepareRootEngine()
        model?.run {
            when (naviType) {
                NaviType.LOCATION -> {
                    sendHandler(START_MOCK_LOCATION, locationModel)
                }

                NaviType.NAVI, NaviType.NAVI_FILE -> {
                    mSpeed = speed / 3.6f
                    //算路成功后 startService
                    index = 0
                    SearchManager.INSTANCE.polylineList.let {
                        sendHandler(
                            START_MOCK_NAVI,
                            it
                        )
                    }
                }

                else -> {
                }
            }
        } ?: run {
            isStart = false
        }
    }

    private fun sendHandler(code: Int, model: Any?) {
        val msg: Message = Message.obtain().apply {
            what = code
            obj = model
        }
        when (code) {
            START_MOCK_LOCATION -> this.naviType = NaviType.LOCATION
            START_MOCK_NAVI -> this.naviType = NaviType.NAVI
            START_MOCK_FILE_NAVI -> this.naviType = NaviType.NAVI_FILE
            else -> {
                this.naviType = NaviType.NONE
            }
        }

        msg.let {
            prepareRootEngine()
            isStart = true
            handle.removeCallbacksAndMessages(null)
            handle.sendMessage(it)
            FloatingViewManger.INSTANCE.startMock()
            showNotification()
        }
    }


    override fun onDestroy() {
        isRunning = false
        _location.value = null
        destroying = true
        super.onDestroy()
        FloatingViewManger.INSTANCE.onDestroy()
        handle.removeCallbacksAndMessages(null)
        removeGps()
        stopForeground(true)
    }


    fun startSimulateLocation(latLng: LatLng, isSingle: Boolean) {
        val pointType = model?.pointType ?: LocationUtils.gcj02
        var gps84 = doubleArrayOf(latLng.longitude, latLng.latitude)
        when (pointType) {
            LocationUtils.gcj02 -> {
                gps84 = LocationUtils.gcj02ToWGS84(latLng.longitude, latLng.latitude)
            }

            LocationUtils.bd09 -> {
                gps84 = LocationUtils.bd09ToWGS84(latLng.longitude, latLng.latitude)
            }

            else -> {}
        }

        val options = com.yadea.smartmoto.root.RootEngine.prefs(this)
        com.yadea.smartmoto.root.RootEngine.offer(com.yadea.smartmoto.root.RootFrame(
            gps84[1], gps84[0], if (isSingle) 0.0 else mSpeed.toDouble(), bearing.toDouble(),
            options.getString("altitude", "80.0")!!.toDouble(),
            options.getString("accuracy", "1.0")!!.toFloat()))
        FloatingViewManger.INSTANCE.setCurLocation(latLng)
        _location.value = latLng
    }

    private fun removeGps() {
        if (destroying) com.yadea.smartmoto.root.RootEngine.stop()
        else com.yadea.smartmoto.root.RootEngine.hold()
        isStart = false
        FloatingViewManger.INSTANCE.stopMock()
        naviType = if (destroying) NaviType.NONE else model?.naviType ?: NaviType.NONE
        if (!destroying && model != null) showNotification()
    }

    /**
     * 是否处于定位震动状态
     */
    private fun isLocationQuiver(): Boolean {
        return MMKVUtils.isLocationVibrationSwitch() && !isLocationAdjust
    }

    private fun prepareRootEngine() {
        com.yadea.smartmoto.root.RootEngine.start(this) { message ->
            ToastUtils.showLong(message)
            stopSelf()
        }
        com.yadea.smartmoto.root.RootEngine.resume()
    }
}
