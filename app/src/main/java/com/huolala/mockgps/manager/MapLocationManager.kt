package com.huolala.mockgps.manager

import android.content.Context
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.huolala.mockgps.maps.ProviderMap
import com.huolala.mockgps.maps.MapStatus
import com.huolala.mockgps.maps.MapStatusUpdateFactory
import com.huolala.mockgps.utils.LocationUtils
import com.blankj.utilcode.util.ToastUtils

enum class FollowMode { MODE_SINGLE, MODE_PERSISTENT, MODE_NONE }
enum class Source { FLOATING, SIMULATED, OTHER }

/** Both providers publish GCJ-02; never pass BD-09 into the mock service. */
class MapLocationManager(context: Context, private val map: ProviderMap, private val follow: FollowMode,
                         private val source: Source = Source.OTHER) {
    private var baiduClient: LocationClient? = null
    private var amapClient: AMapLocationClient? = null
    private var zoomed = false
    private var destroyed = false
    private var errorShown = false
    private val baiduListener = object : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation?) {
            if (location == null || destroyed) return
            if (location.locType !in listOf(61, 161, 66)) { error("百度定位失败（${location.locType}），请检查权限、网络和 Key"); return }
            var lng = location.longitude
            var lat = location.latitude
            if (location.coorType == "bd09ll") LocationUtils.bd09ToGcj02(lng, lat).let { lng = it[0]; lat = it[1] }
            update(lat, lng, location.radius, location.speed, location.direction)
        }
    }
    private val amapListener = AMapLocationListener { location ->
        if (location != null && !destroyed) {
            if (location.errorCode == 0) update(location.latitude, location.longitude, location.accuracy, location.speed, location.bearing)
            else error("高德定位失败（${location.errorCode}），请检查权限、网络和 Key")
        }
    }
    init {
        map.isMyLocationEnabled = true
        setLocationMode()
        try {
            if (source != Source.OTHER) {
                // Simulated views consume our own position stream, not a second SDK location client.
            } else if (map.baidu != null) {
                baiduClient = LocationClient(context.applicationContext).apply {
                    locOption = LocationClientOption().apply {
                        setCoorType("gcj02")
                        setScanSpan(2000)
                        isOpenGnss = true
                        setIsNeedAddress(false)
                    }
                    registerLocationListener(baiduListener)
                    start()
                }
            } else {
                amapClient = AMapLocationClient(context.applicationContext).apply {
                    setLocationOption(AMapLocationClientOption().apply {
                        interval = 2000
                        isNeedAddress = false
                    })
                    setLocationListener(amapListener)
                    startLocation()
                }
            }
        } catch (e: Exception) { error("地图定位初始化失败，请检查权限与地图配置") }
    }
    private fun error(message: String) { if (!errorShown) { errorShown = true; ToastUtils.showLong(message) } }
    private fun update(lat: Double, lng: Double, accuracy: Float, speed: Float, direction: Float) {
        if (destroyed || !lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) return
        val data = MyLocationData.Builder().latitude(lat).longitude(lng).accuracy(accuracy).speed(speed).direction(direction).build()
        map.setMyLocationData(data)
        if (follow == FollowMode.MODE_PERSISTENT || (follow == FollowMode.MODE_SINGLE && !zoomed)) { zoom(data); zoomed = true }
    }
    fun displayPoint(point: LatLng) { update(point.latitude, point.longitude, 1f, 0f, 0f) }
    fun zoom(data: MyLocationData) { map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(LatLng(data.latitude, data.longitude), if (source == Source.FLOATING) 18f else 16f)) }
    fun setLocationMode(mode: MyLocationConfiguration.LocationMode = MyLocationConfiguration.LocationMode.NORMAL) {
        map.setMyLocationConfiguration(MyLocationConfiguration(mode, true, null))
        if (mode == MyLocationConfiguration.LocationMode.NORMAL) map.setMapStatus(MapStatusUpdateFactory.newMapStatus(MapStatus.Builder().overlook(0f).rotate(0f).build()))
    }
    fun onDestroy() {
        if (destroyed) return
        destroyed = true
        baiduClient?.unRegisterLocationListener(baiduListener); baiduClient?.stop(); baiduClient = null
        amapClient?.unRegisterLocationListener(amapListener); amapClient?.stopLocation(); amapClient?.onDestroy(); amapClient = null
        map.isMyLocationEnabled = false
    }
}
