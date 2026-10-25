package com.huolala.mockgps.manager

import android.annotation.SuppressLint
import android.app.Service
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.constraintlayout.widget.ConstraintLayout
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.model.LatLng
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.Utils
import com.huolala.mockgps.R
import com.huolala.mockgps.listener.FloatingTouchListener
import com.huolala.mockgps.manager.utils.MapDrawUtils
import com.huolala.mockgps.model.NaviType
import com.huolala.mockgps.utils.CalculationLogLatDistance
import com.huolala.mockgps.utils.HandlerUtils
import com.huolala.mockgps.utils.MMKVUtils
import com.huolala.mockgps.widget.RockerView
import okhttp3.internal.format
import java.util.Locale

/**
 * @author jiayu.liu
 */
class FloatingViewManger private constructor() {
    private val floatingBinding get() = view?.let { com.huolala.mockgps.databinding.LayoutFloatingBinding.bind(it) }
    private val locationBinding get() = locationAdjust?.let { com.huolala.mockgps.databinding.LayoutFloatingLocationAdjustBinding.bind(it) }
    private val naviBinding get() = naviAdjust?.let { com.huolala.mockgps.databinding.LayoutFloatingNaviAdjustBinding.bind(it) }
    private var windowManager: WindowManager =
        Utils.getApp().getSystemService(Service.WINDOW_SERVICE) as WindowManager

    /**
     * 悬浮窗
     */
    private var view: View? = null

    /**
     * 定位控制悬浮窗
     */
    private var locationAdjust: View? = null

    /**
     * 导航控制悬浮窗
     */
    private var naviAdjust: View? = null
    private var angle: Double = 0.0
    private val mScreenWidth = ScreenUtils.getScreenWidth()
    private val mScreenHeight = ScreenUtils.getScreenHeight()
    private var curLocation: LatLng? = null

    /**
     * 是否已增加悬浮窗
     */
    var isAddFloatingView = false

    /**
     * 是否已添加调整悬浮窗
     */
    private var isAddAdjust = false
    private var curNaviType: Int = NaviType.NONE
    private var infoViewCurVisibility: Int = View.VISIBLE
    var listener: FloatingViewListener? = null
    private var mapLocationManager: MapLocationManager? = null

    /**
     * 隐藏设置按钮
     */
    private val settingViewRunnable: Runnable =
        Runnable { floatingBinding?.ivSetting?.visibility = View.INVISIBLE }

    /**
     * 摇杆不动 补发当前方向
     */
    private val rockerRunnable: Runnable = Runnable {
        changeLocation(angle)
    }

    companion object {
        val INSTANCE: FloatingViewManger by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            FloatingViewManger()
        }
    }

    /**
     * 悬浮窗添加
     */
    @SuppressLint("ClickableViewAccessibility")
    fun addFloatViewToWindow() {
        if (isAddFloatingView) {
            return
        }
        view = LayoutInflater.from(Utils.getApp()).inflate(R.layout.layout_floating, null)

        floatingBinding?.mapview?.onCreate(Utils.getApp(), null)
        floatingBinding?.mapview?.onResume()
        floatingBinding?.mapview?.showScaleControl(false)
        floatingBinding?.mapview?.showZoomControls(false)
        floatingBinding?.mapview?.map?.let {
            it.uiSettings?.isCompassEnabled = false
            it.uiSettings?.setAllGesturesEnabled(false)
            it.uiSettings?.isOverlookingGesturesEnabled = false
            it.setOnMapLoadedCallback {
                mapLocationManager = MapLocationManager(
                    Utils.getApp(),
                    it,
                    FollowMode.MODE_PERSISTENT,
                    Source.FLOATING
                )
                curLocation?.let { point -> mapLocationManager?.displayPoint(point) }
            }
        }

        //暂停播放
        ClickUtils.applySingleDebouncing(floatingBinding?.startAndPause) {
            listener?.let { listener ->
                if (!it.isSelected) {
                    listener.reStart(false)
                } else {
                    listener.pause()
                }
            }
        }


        ClickUtils.applySingleDebouncing(floatingBinding?.ivRest) {
            listener?.reStart(true)
        }

        //控制展示暂停面板
        ClickUtils.applySingleDebouncing(floatingBinding?.ivSetting) {
            infoViewCurVisibility =
                if (floatingBinding?.info?.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
            floatingBinding?.info?.visibility = infoViewCurVisibility
        }

        //微调控制面板
        ClickUtils.applySingleDebouncing(floatingBinding?.ivAdjust) {
            if (isAddAdjust) {
                isAddAdjust = false

                locationAdjust?.let {
                    removeFromWindow(it)
                }
                naviAdjust?.let {
                    removeFromWindow(it)
                }
            } else {
                isAddAdjust = true
                when (listener?.getNaviType()) {
                    NaviType.LOCATION -> addAdjustLocationToWindow()
                    NaviType.NAVI, NaviType.NAVI_FILE -> addAdjustNaviToWindow()
                    else -> {}
                }
            }
        }

        HandlerUtils.INSTANCE.postDelayed(settingViewRunnable, 5000)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || Settings.canDrawOverlays(Utils.getApp())
        ) {
            isAddFloatingView = true
            val layoutParams = getLayoutParams()
            addToWindow(view, layoutParams)
            layoutParams.let { params ->
                floatingBinding?.card?.setOnTouchListener(
                    FloatingTouchListener(
                        windowManager,
                        params,
                        true,
                        view
                    ).apply {
                        callBack = object : FloatingTouchListener.FloatingTouchCallBack {

                            override fun onActionDown() {
                                HandlerUtils.INSTANCE.removeCallbacks(settingViewRunnable)
                                floatingBinding?.ivSetting?.visibility = View.VISIBLE
                                floatingBinding?.info!!.visibility = View.INVISIBLE
                            }

                            override fun onActionUp(isLeft: Boolean) {
                                HandlerUtils.INSTANCE.postDelayed(settingViewRunnable, 5000)
                                (floatingBinding?.info?.layoutParams as ConstraintLayout.LayoutParams).let {
                                    if (isLeft) {
                                        floatingBinding?.info?.setBackgroundResource(R.drawable.shape_round_10_color_black_30_right)
                                        floatingBinding?.infoGuideline?.setGuidelinePercent(0.75f)
                                        it.leftToRight = R.id.spacer
                                        it.rightToLeft = ConstraintLayout.LayoutParams.UNSET
                                    } else {
                                        floatingBinding?.info?.setBackgroundResource(R.drawable.shape_round_10_color_black_30_left)
                                        floatingBinding?.infoGuideline?.setGuidelinePercent(0.25f)
                                        it.rightToLeft = R.id.spacer
                                        it.leftToRight = ConstraintLayout.LayoutParams.UNSET

                                    }
                                    floatingBinding?.info?.layoutParams = it
                                }

                                (floatingBinding?.card?.layoutParams as ConstraintLayout.LayoutParams).let {
                                    if (isLeft) {
                                        it.rightToRight = ConstraintLayout.LayoutParams.UNSET
                                        it.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                                    } else {
                                        it.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
                                        it.leftToLeft = ConstraintLayout.LayoutParams.UNSET

                                    }
                                    floatingBinding?.card?.layoutParams = it
                                }

                                floatingBinding?.info?.visibility = infoViewCurVisibility
                            }
                        }
                    })
            }
        }
    }

    /**
     * 在window中增加布局
     */
    private fun addToWindow(
        view: View?,
        layoutParams: WindowManager.LayoutParams
    ) {
        return try {
            windowManager.addView(view, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()

        }
    }

    private fun removeFromWindow(view: View) {
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 定位调整view
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun addAdjustLocationToWindow() {
        if (locationAdjust == null) {
            locationAdjust = LayoutInflater.from(Utils.getApp())
                .inflate(R.layout.layout_floating_location_adjust, null)
            locationBinding?.rockerView!!.setOnAngleChangeListener(object :
                RockerView.OnAngleChangeListener {
                private var currentTimeMillis: Long = 0L
                private val interval: Long = 500L

                override fun onStart() {}

                override fun angle(angle: Double) {
                    this@FloatingViewManger.angle = angle
                    System.currentTimeMillis().let {
                        //限制触发间隔
                        if (it - currentTimeMillis <= interval) {
                            return
                        }
                        currentTimeMillis = it
                        changeLocation(angle)
                        //1s后如果没有角度变化 则回调上次的数据
                        HandlerUtils.INSTANCE.removeCallbacks(rockerRunnable)
                        HandlerUtils.INSTANCE.postDelayed(rockerRunnable, interval * 2)
                    }
                }

                override fun onFinish() {
                    HandlerUtils.INSTANCE.removeCallbacks(rockerRunnable)
                    listener?.locationAdjustFinish()
                }

            })
            ClickUtils.applySingleDebouncing(locationBinding?.ivClose!!) {
                removeFromWindow(locationAdjust!!)
                isAddAdjust = false
            }
        }
        locationAdjust?.parent ?: kotlin.run {
            val layoutParams = getLayoutParams()
            layoutParams.x = 0
            addToWindow(locationAdjust, layoutParams)
            layoutParams.let {
                locationAdjust?.setOnTouchListener(
                    FloatingTouchListener(
                        windowManager,
                        it,
                        false,
                        locationAdjust
                    )
                )
            }
        }
    }

    /**
     * 增加导航微调悬浮窗
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun addAdjustNaviToWindow() {
        if (naviAdjust == null) {
            naviAdjust = LayoutInflater.from(Utils.getApp())
                .inflate(R.layout.layout_floating_navi_adjust, null)


            naviBinding?.speedNavView!!.updateCurValue(MMKVUtils.getSpeed())

            naviBinding?.roadNavSeekbar?.setOnSeekBarChangeListener(object :
                OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.let {
                        changeNaviInfo(
                            it.progress,
                            naviBinding?.speedNavView!!.getCurValue()
                        )
                    }
                }
            })

            //修改参数
            ClickUtils.applySingleDebouncing(naviBinding?.btnNavChange!!) {
                ToastUtils.showShort("修改成功")
                val speed = naviBinding?.speedNavView!!.getCurValue()
                changeNaviInfo(speed = speed)
            }

            //关闭微调悬浮窗
            ClickUtils.applySingleDebouncing(naviBinding?.ivNavClose!!) {
                removeFromWindow(naviAdjust!!)
                isAddAdjust = false
            }

            //切换成模拟定位微调
            ClickUtils.applySingleDebouncing(naviBinding?.btnChangeLocation!!) {
                listener?.switchLocation()
            }
        }
        naviAdjust?.parent ?: kotlin.run {
            val layoutParams = getLayoutParams()
            layoutParams.x = 0
            addToWindow(naviAdjust, layoutParams)
            layoutParams.let {
                naviAdjust?.setOnTouchListener(
                    FloatingTouchListener(
                        windowManager,
                        it,
                        false,
                        naviAdjust
                    )
                )
            }
        }

    }

    private fun changeNaviInfo(index: Int = -1, speed: Int = 0) {
        listener?.changeNaviInfo(index, speed)
    }

    private fun changeLocation(angle: Double) {
        curLocation?.let {
            val location =
                CalculationLogLatDistance.getNextLonLat(
                    curLocation,
                    angle,
                    locationBinding?.speedView!!.getCurValue().toDouble()
                )
            if (CalculationLogLatDistance.isCheckNaN(location)) {
                ToastUtils.showShort("计算经纬度失败,请重试！")
                return
            }
            listener?.changeLocation(location, angle)
        }
    }

    private fun getLayoutParams(): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams()
        // mock设置成功后，直接定位到当前的主包名下
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE
        }
        params.packageName = AppUtils.getAppPackageName()
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.LEFT or Gravity.TOP
        params.x = 0
        params.y = mScreenHeight / 2
        //焦点问题  透明度
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.format = PixelFormat.TRANSPARENT
        return params
    }

    /**
     * 开启模拟
     */
    fun startMock() {
        if (curNaviType == listener?.getNaviType() && floatingBinding?.startAndPause?.isSelected == true) {
            //模拟模式未变&&当前状态是模拟状态  过滤
            return
        }
        floatingBinding?.mapview?.map?.clear()
        floatingBinding?.startAndPause?.isSelected = true
        when (listener?.getNaviType()) {
            NaviType.LOCATION -> {
                floatingBinding?.ivRest?.visibility = View.GONE
                if (isAddAdjust) {
                    naviAdjust?.parent?.let {
                        removeFromWindow(naviAdjust!!)
                    }
                    addAdjustLocationToWindow()
                }
                mapLocationManager?.setLocationMode(MyLocationConfiguration.LocationMode.NORMAL)
            }

            NaviType.NAVI, NaviType.NAVI_FILE -> {
                floatingBinding?.ivRest?.visibility = View.VISIBLE
                if (isAddAdjust) {
                    locationAdjust?.parent?.let {
                        removeFromWindow(locationAdjust!!)
                    }
                    addAdjustNaviToWindow()
                    naviBinding?.btnChangeLocation?.visibility = View.INVISIBLE
                }
                mapLocationManager?.setLocationMode(MyLocationConfiguration.LocationMode.COMPASS)
                //清空等待  更新当前速度信息
                naviBinding?.speedNavView?.clearLongClickWait()
                naviBinding?.speedNavView?.updateCurValue(MMKVUtils.getSpeed())
                floatingBinding?.mapview?.map?.let { map ->
                    SearchManager.INSTANCE.polylineList.let {
                        if (it.isEmpty()) {
                            return
                        }

                        MapDrawUtils.drawLineToMap(
                            map,
                            it,
                            Rect(0, 0, 0, 0),
                            animateMapStatus = false
                        )
                    }
                }
            }

            else -> {}
        }
        floatingBinding?.ivAdjust?.visibility = View.VISIBLE
    }

    /**
     * 停止模拟
     */
    fun stopMock() {
        floatingBinding?.startAndPause?.isSelected = false
        floatingBinding?.ivAdjust?.visibility = View.GONE
    }

    fun stopMockFromReceiver() {
        floatingBinding?.startAndPause?.callOnClick()
    }

    /**
     * 更新当前模拟位置¬
     */
    fun setCurLocation(curLocation: LatLng?) {
        this.curLocation = curLocation
        curLocation?.let { mapLocationManager?.displayPoint(it) }
        if (curLocation == null) {
            return
        }
        //已加载到window中
        locationAdjust?.parent?.run {
            locationBinding?.tvInfo!!.text =
                format(
                    "当前位置：\nlongitude:%.6f\nlatitude:%.6f",
                    curLocation.longitude,
                    curLocation.latitude
                )
        }
    }

    /**
     * 更新当前进度（道路）
     */
    fun updateNaviInfo(index: Int, size: Int) {
        //已加载到window中
        naviAdjust?.parent?.run {
            naviBinding?.tvNavInfo!!.text = String.format(
                Locale.getDefault(),
                "当前进度(道路)：%d/%d",
                index,
                size
            )
            naviBinding?.roadNavSeekbar?.let {
                if (it.max.toInt() != size - 1) {
//                    GlobalScope.launch(Dispatchers.IO) {
//                        ReflectionUtil.setPrivateField(it, "mMax", size - 1)
//                        ReflectionUtil.callPrivateMethod(it, "initConfigByPriority")
//                    }
                    it.max = size - 1
                }
                it.progress = index
            }
        }
        if (index == size) {
            arriveDestination()
        } else {
            naviBinding?.btnChangeLocation?.visibility = View.INVISIBLE
        }
    }

    fun onDestroy() {
        HandlerUtils.INSTANCE.removeCallbacks(rockerRunnable)
        HandlerUtils.INSTANCE.removeCallbacks(settingViewRunnable)
        locationAdjust?.let { runCatching { removeFromWindow(it) } }
        naviAdjust?.let { runCatching { removeFromWindow(it) } }
        locationAdjust = null; naviAdjust = null
        isAddAdjust = false
        mapLocationManager?.onDestroy()
        mapLocationManager = null
        floatingBinding?.mapview?.onPause()
        floatingBinding?.mapview?.onDestroy()
        listener = null
        try {
            view?.let {
                windowManager.removeView(it)
            }
            isAddFloatingView = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 到达目的地
     */
    private fun arriveDestination() {
        //不是模拟导航 则忽略
        if (!isNaviType() || naviBinding?.btnChangeLocation?.visibility == View.VISIBLE
        ) {
            return
        }
        naviBinding?.btnChangeLocation?.visibility = View.VISIBLE
    }

    /**
     * 是否是导航模式
     */
    private fun isNaviType(): Boolean {
        return listener?.getNaviType() == NaviType.NAVI ||
                listener?.getNaviType() == NaviType.NAVI_FILE
    }


    interface FloatingViewListener {
        /**
         * 暂停
         */
        fun pause()

        /**
         * 重新启动
         */
        fun reStart(isRest: Boolean)

        /**
         * 获取当前导航模拟
         * @see NaviType
         */
        fun getNaviType(): Int

        /**
         * 更新当前定位点
         * 微调 仅支持模拟定位
         */
        fun changeLocation(latLng: LatLng, angle: Double)


        /**
         * 模拟定位微调完成
         */
        fun locationAdjustFinish()

        /**
         * 更新导航信息
         * 微调 支持模拟导航
         * @param index 修改道路index
         * @param speed 修改速度
         */
        fun changeNaviInfo(index: Int, speed: Int)

        /**
         * 切换成模拟定位
         */
        fun switchLocation()
    }
}