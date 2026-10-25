package com.huolala.mockgps.manager

import android.os.Handler
import android.os.Looper
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.route.*
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.RouteSearchV2
import com.amap.api.services.route.DriveRouteResultV2
import com.amap.api.services.route.BusRouteResultV2
import com.amap.api.services.route.WalkRouteResultV2
import com.amap.api.services.route.RideRouteResultV2
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.Utils
import com.huolala.mockgps.maps.MapPreferences
import com.huolala.mockgps.model.RouteLines

/** All route points are GCJ-02. No cross-provider silent fallback. */
class SearchManager private constructor() {
    companion object { val INSTANCE by lazy { SearchManager() } }
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<SearchManagerListener>()
    private var search: RoutePlanSearch? = null
    private var busy = false
    private var generation = 0
    var polylineList: ArrayList<LatLng> = arrayListOf()
    fun addSearchManagerListener(listener: SearchManagerListener) { listeners.add(listener) }
    fun removeSearchManagerListener(listener: SearchManagerListener) { listeners.remove(listener) }
    fun cancel() { generation++; busy = false; handler.removeCallbacksAndMessages(null); search?.destroy(); search = null }
    private fun valid(point: LatLng?) = point != null && point.latitude.isFinite() && point.longitude.isFinite() && point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
    fun driverSearch(startLatLng: LatLng?, endLatLng: LatLng?, multiRoute: Boolean, wayList: MutableList<LatLng>? = null) =
        route(startLatLng, endLatLng, wayList, "drive", multiRoute)
    fun bikingSearch(startLatLng: LatLng?, endLatLng: LatLng?, wayList: MutableList<LatLng>? = null, ridingType: Int = 0) =
        route(startLatLng, endLatLng, wayList, if (ridingType == 1) "electric" else "ride", false)
    fun walkingSearch(startLatLng: LatLng?, endLatLng: LatLng?, wayList: MutableList<LatLng>? = null) = route(startLatLng, endLatLng, wayList, "walk", false)
    private fun route(start: LatLng?, end: LatLng?, way: MutableList<LatLng>?, mode: String, multi: Boolean) {
        if (busy) { ToastUtils.showShort("路线规划中，请稍后"); return }
        val recipients = listeners.toList()
        fun early(message: String) { ToastUtils.showLong(message); recipients.forEach { if (it in listeners) it.onRouteResultLines(emptyList()) } }
        if (!valid(start) || !valid(end) || way?.any { !valid(it) } == true) { early("起终点或途经点坐标无效"); return }
        if ((way?.size ?: 0) > 16) { early("单次最多支持 16 个途经点"); return }
        val provider = try { MapPreferences.requireProvider(Utils.getApp()) } catch (e: Exception) { early("请先在应用中选择地图并同意隐私说明"); return }
        if (provider == MapPreferences.Provider.AMAP && mode == "electric") {
            early("当前高德 Android 搜索 SDK 未提供电动车专用算路接口，请选择普通骑行或切换百度电动车模式；不会替换成驾车路线")
            return
        }
        busy = true
        val token = ++generation
        fun complete(lines: List<RouteLines>?, error: String? = null) {
            if (token != generation || !busy) return
            busy = false; handler.removeCallbacksAndMessages(null)
            search?.destroy(); search = null
            if (error != null) ToastUtils.showLong(error)
            val validLines = lines?.filter { it.route.size in 2..50000 && it.route.all { point -> valid(point) } }.orEmpty()
            if (validLines.isEmpty() && error == null) ToastUtils.showShort("没有可用路线或路线超过 50000 点，请调整起终点或分段规划")
            recipients.forEach { if (it in listeners) it.onRouteResultLines(validLines) }
        }
        handler.postDelayed({ complete(null, "路线规划超时，请检查网络、Key 和配额后重试") }, 45000)
        try {
            if (provider == MapPreferences.Provider.BAIDU) {
                val client = RoutePlanSearch.newInstance()
                search = client
                client.apply {
                    setOnGetRoutePlanResultListener(object : OnGetRoutePlanResultListener {
                        override fun onGetDrivingRouteResult(result: DrivingRouteResult?) {
                            if (result?.error != SearchResult.ERRORNO.NO_ERROR) { complete(null, "百度驾车算路失败：${result?.error}"); return }
                            complete(result.routeLines?.map { line -> RouteLines(ArrayList(line.allStep.orEmpty().flatMap { it.wayPoints.orEmpty() })) })
                        }
                        override fun onGetBikingRouteResult(result: BikingRouteResult?) {
                            if (result?.error != SearchResult.ERRORNO.NO_ERROR) { complete(null, "百度骑行算路失败：${result?.error}"); return }
                            complete(result.routeLines?.map { line -> RouteLines(ArrayList(line.allStep.orEmpty().flatMap { it.wayPoints.orEmpty() })) })
                        }
                        override fun onGetWalkingRouteResult(result: WalkingRouteResult?) {
                            if (result?.error != SearchResult.ERRORNO.NO_ERROR) { complete(null, "百度步行算路失败：${result?.error}"); return }
                            complete(result.routeLines?.map { line -> RouteLines(ArrayList(line.allStep.orEmpty().flatMap { it.wayPoints.orEmpty() })) })
                        }
                        override fun onGetTransitRouteResult(result: TransitRouteResult?) {}
                        override fun onGetMassTransitRouteResult(result: MassTransitRouteResult?) {}
                        override fun onGetIndoorRouteResult(result: IndoorRouteResult?) {}
                        override fun onGetIntegralRouteResult(result: IntegralRouteResult?) {}
                    })
                    val from = PlanNode.withLocation(start)
                    val to = PlanNode.withLocation(end)
                    val pass = way?.map { PlanNode.withLocation(it) }
                    val accepted = when (mode) {
                        "drive" -> drivingSearch(DrivingRoutePlanOption().from(from).to(to).apply {
                            if (!pass.isNullOrEmpty()) passBy(pass)
                            if (!multi) policy(DrivingRoutePlanOption.DrivingPolicy.ECAR_DIS_FIRST)
                        })
                        "walk" -> {
                            if (!way.isNullOrEmpty()) { complete(null, "百度步行暂不支持途经点，请移除途经点"); return@apply }
                            walkingSearch(WalkingRoutePlanOption().from(from).to(to))
                        }
                        else -> bikingSearch(BikingRoutePlanOption().from(from).to(to).apply {
                            if (!pass.isNullOrEmpty()) passBy(pass)
                            ridingType(if (mode == "electric") 1 else 0)
                        })
                    }
                    if (!accepted) complete(null, "百度算路请求未能发起")
                }
            } else {
                // Ride/walk use ordered per-leg requests because the native API has no waypoint field.
                val nodes = listOf(start!!) + way.orEmpty() + listOf(end!!)
                val combined = arrayListOf<LatLng>()
                fun point(p: LatLng) = LatLonPoint(p.latitude, p.longitude)
                fun requestLeg(leg: Int) {
                    if (token != generation || !busy) return
                    val client = RouteSearchV2(Utils.getApp())
                    fun append(points: List<LatLonPoint>) {
                        if (token != generation || !busy) return
                        if (points.size < 2) { complete(null, "高德未返回完整路线坐标，请检查路线服务权限"); return }
                        for (p in points) {
                            val coordinate = LatLng(p.latitude, p.longitude)
                            if (combined.lastOrNull() != coordinate) combined.add(coordinate)
                        }
                        if (leg + 1 < nodes.size - 1) requestLeg(leg + 1) else complete(listOf(RouteLines(combined)))
                    }
                    client.setRouteSearchListener(object : RouteSearchV2.OnRouteSearchListener {
                        override fun onDriveRouteSearched(result: DriveRouteResultV2?, code: Int) {
                            if (code != 1000) { complete(null, "高德驾车算路失败（$code），请检查 Key、权限和配额"); return }
                            val lines = result?.paths?.map { path -> RouteLines(ArrayList(path.steps.orEmpty().flatMap { it.polyline.orEmpty() }.map { LatLng(it.latitude, it.longitude) })) }
                            complete(if (multi) lines else lines?.take(1))
                        }
                        override fun onBusRouteSearched(result: BusRouteResultV2?, code: Int) {}
                        override fun onWalkRouteSearched(result: WalkRouteResultV2?, code: Int) {
                            if (code != 1000) complete(null, "高德步行算路失败（$code）")
                            else append(result?.paths?.firstOrNull()?.steps.orEmpty().flatMap { it.polyline.orEmpty() })
                        }
                        override fun onRideRouteSearched(result: RideRouteResultV2?, code: Int) {
                            if (code != 1000) complete(null, "高德骑行算路失败（$code）")
                            else append(result?.paths?.firstOrNull()?.steps.orEmpty().flatMap { it.polyline.orEmpty() })
                        }
                    })
                    val endpoints = RouteSearchV2.FromAndTo(point(nodes[leg]), point(nodes[leg + 1]))
                    when (mode) {
                        "drive" -> client.calculateDriveRouteAsyn(RouteSearchV2.DriveRouteQuery(
                            RouteSearchV2.FromAndTo(point(start), point(end)), RouteSearchV2.DrivingStrategy.DEFAULT,
                            way?.map { point(it) }, null, null).apply { showFields = RouteSearchV2.ShowFields.POLINE })
                        "walk" -> client.calculateWalkRouteAsyn(RouteSearchV2.WalkRouteQuery(endpoints).apply { showFields = RouteSearchV2.ShowFields.POLINE })
                        else -> client.calculateRideRouteAsyn(RouteSearchV2.RideRouteQuery(endpoints).apply { showFields = RouteSearchV2.ShowFields.POLINE })
                    }
                }
                requestLeg(0)
            }
        } catch (e: Exception) { complete(null, "路线规划初始化失败，请检查地图配置和服务权限") }
    }
    fun selectDriverLine(line: RouteLines?) { polylineList.clear(); line?.let { polylineList.addAll(it.route) } }
    interface SearchManagerListener { fun onRouteResultLines(routeLines: List<RouteLines>?) }
}
