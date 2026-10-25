package com.yadea.smartmoto.maps

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.sug.*
import com.baidu.mapapi.search.geocode.*
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.geocoder.*
import com.amap.api.services.core.LatLonPoint
import com.yadea.smartmoto.model.PoiInfoModel

/** Per-request handles + generation checks prevent out-of-order suggestions/reverse-geocodes. */
class PlaceSearch(private val context: Context) {
    private var generation = 0
    private val handler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var suggestion: SuggestionSearch? = null
    private var coder: GeoCoder? = null
    fun invalidate() { generation++; handler.removeCallbacksAndMessages(null); suggestion?.destroy(); suggestion = null; coder?.destroy(); coder = null }
    fun suggestions(keyword: String, city: String, callback: (MutableList<SuggestionResult.SuggestionInfo>?, String?) -> Unit) {
        invalidate()
        val token = generation
        val timeout = Runnable { if (!destroyed && token == generation) { invalidate(); callback(null, "搜索超时，请检查网络") } }
        handler.postDelayed(timeout, 15000)
        fun result(list: MutableList<SuggestionResult.SuggestionInfo>?, error: String?) {
            if (destroyed || token != generation) return
            handler.removeCallbacks(timeout)
            callback(list, error)
        }
        try {
            if (MapPreferences.requireProvider(context) == MapPreferences.Provider.BAIDU) {
                suggestion = SuggestionSearch.newInstance().apply {
                    setOnGetSuggestionResultListener { response ->
                        if (response == null || response.error != SearchResult.ERRORNO.NO_ERROR)
                            result(null, "百度搜索失败：${response?.error ?: "无响应"}，请检查 Key、网络和配额")
                        else result(response.allSuggestions?.filter { it.pt != null }?.toMutableList(), null)
                    }
                    if (!requestSuggestion(SuggestionSearchOption().city(city.ifBlank { "中国" }).keyword(keyword))) result(null, "百度搜索请求未能发起")
                }
            } else {
                Inputtips(context, InputtipsQuery(keyword, city.takeUnless { it == "中国" } ?: "")).apply {
                    setInputtipsListener { tips, code ->
                        if (code != 1000) result(null, "高德搜索失败（$code），请检查 Key、服务权限和配额")
                        else result(tips?.filter { it.point != null }?.map { tip ->
                            SuggestionResult.SuggestionInfo().apply {
                                key = tip.name; uid = tip.poiID; address = tip.address; district = tip.district
                                this.city = city; pt = LatLng(tip.point.latitude, tip.point.longitude)
                            }
                        }?.toMutableList(), null)
                    }
                    requestInputtipsAsyn()
                }
            }
        } catch (e: Exception) { result(null, "搜索初始化失败，请检查地图配置") }
    }
    fun reverse(point: LatLng, callback: (PoiInfoModel?, String?) -> Unit) {
        invalidate()
        val token = generation
        val timeout = Runnable { if (!destroyed && token == generation) { invalidate(); callback(null, "地址解析超时，可直接使用坐标") } }
        handler.postDelayed(timeout, 15000)
        fun result(model: PoiInfoModel?, error: String?) {
            if (destroyed || token != generation) return
            handler.removeCallbacks(timeout); callback(model, error)
        }
        try {
            if (MapPreferences.requireProvider(context) == MapPreferences.Provider.BAIDU) {
                coder = GeoCoder.newInstance().apply {
                    setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                        override fun onGetGeoCodeResult(response: GeoCodeResult?) {}
                        override fun onGetReverseGeoCodeResult(response: ReverseGeoCodeResult?) {
                            if (response == null || response.error != SearchResult.ERRORNO.NO_ERROR) result(null, "百度地址解析失败：${response?.error}")
                            else result(PoiInfoModel(point, point.toString(), response.sematicDescription?.takeIf { it.isNotBlank() }
                                ?: response.address ?: "所选坐标", city = response.addressDetail?.city ?: ""), null)
                        }
                    })
                    if (!reverseGeoCode(ReverseGeoCodeOption().location(point).radius(500).newVersion(1))) result(null, "地址解析请求未能发起")
                }
            } else {
                GeocodeSearch(context).apply {
                    setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                        override fun onGeocodeSearched(response: GeocodeResult?, code: Int) {}
                        override fun onRegeocodeSearched(response: RegeocodeResult?, code: Int) {
                            if (code != 1000 || response?.regeocodeAddress == null) result(null, "高德地址解析失败（$code），可直接使用坐标")
                            else result(PoiInfoModel(point, point.toString(), response.regeocodeAddress.formatAddress ?: "所选坐标",
                                city = response.regeocodeAddress.city ?: ""), null)
                        }
                    })
                    getFromLocationAsyn(RegeocodeQuery(LatLonPoint(point.latitude, point.longitude), 500f, GeocodeSearch.AMAP))
                }
            }
        } catch (e: Exception) { result(null, "地址解析初始化失败，可直接使用坐标") }
    }
    fun destroy() { destroyed = true; invalidate() }
}
