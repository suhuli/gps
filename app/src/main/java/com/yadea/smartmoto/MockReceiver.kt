package com.yadea.smartmoto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.route.DrivingRouteLine
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.yadea.smartmoto.manager.FloatingViewManger
import com.yadea.smartmoto.manager.SearchManager
import com.yadea.smartmoto.manager.utils.MapConvertUtils
import com.yadea.smartmoto.manager.utils.MapDrawUtils
import com.yadea.smartmoto.model.MockMessageModel
import com.yadea.smartmoto.model.NaviType
import com.yadea.smartmoto.model.PoiInfoModel
import com.yadea.smartmoto.model.PoiInfoType
import com.yadea.smartmoto.model.RouteLines
import com.yadea.smartmoto.server.GpsService
import com.yadea.smartmoto.utils.LocationUtils
import com.yadea.smartmoto.utils.MMKVUtils
import com.yadea.smartmoto.utils.Utils

/**
 * @author jiayu.liu
 *
 *  Intent intentBroadcast = new Intent();
 *  intentBroadcast.setAction("com.yadea.smartmoto.navi");
 *  intentBroadcast.putExtra("start", "116.419431,40.028795");
 *  intentBroadcast.putExtra("end", "116.409816,40.05139");
 *  //bd09   gps84   gcj02
 *  intentBroadcast.putExtra("type", "gcj02");
 *  sendBroadcast(intentBroadcast);
 *
 */
class MockReceiver : BroadcastReceiver() {

    companion object {
        val MOCK_ACTION = BuildConfig.APPLICATION_ID + ".navi"
        const val CAR = "car"
        const val BICYCLE = "bicycle"
        const val ELECTRIC_BICYCLE = "electricBicycle"
    }

    override fun onReceive(context: Context?, intent: Intent) {
        if (context == null) {
            return
        }
        if (!com.yadea.smartmoto.maps.MapPreferences.consented(context)) return
        val action = intent.action
        if (MOCK_ACTION != action) {
            return
        }
        val event = intent.getStringExtra("event")
        when (event) {
            "stopNavi" -> {
                context.stopService(Intent(context, GpsService::class.java))
                return
            }

            else -> {}
        }
        val start = intent.getStringExtra("start")
        val end = intent.getStringExtra("end")
        val type = intent.getStringExtra("type") ?: LocationUtils.gcj02
        val routeType = intent.getStringExtra("planType") ?: CAR
        Utils.checkFloatWindow(context).let {
            if (!it) {
                ToastUtils.showShort("悬浮窗权限未开启，请返回app开启权限！")
                return
            }

            if (TextUtils.isEmpty(start) || TextUtils.isEmpty(end)) {
                return
            }
            var startLatLng: LatLng? = null
            var endLatLng: LatLng? = null

            try {
                start!!.split(",").apply {
                    require(size == 2)
                    val rawLng = get(0).toDouble()
                    val rawLat = get(1).toDouble()
                    require(rawLng.isFinite() && rawLat.isFinite() && rawLng in -180.0..180.0 && rawLat in -90.0..90.0)
                    startLatLng = when (type) {
                        LocationUtils.bd09 -> {
                            val bd09ToGcj02 =
                                LocationUtils.bd09ToGcj02(get(0).toDouble(), get(1).toDouble())
                            LatLng(bd09ToGcj02[1], bd09ToGcj02[0])
                        }

                        LocationUtils.gps84 -> {
                            val wgs84ToGcj02 =
                                LocationUtils.wgs84ToGcj02(get(0).toDouble(), get(1).toDouble())
                            LatLng(wgs84ToGcj02[1], wgs84ToGcj02[0])
                        }

                        else -> {
                            LatLng(get(1).toDouble(), get(0).toDouble())
                        }
                    }
                }
                end!!.split(",").apply {
                    require(size == 2)
                    val rawLng = get(0).toDouble()
                    val rawLat = get(1).toDouble()
                    require(rawLng.isFinite() && rawLat.isFinite() && rawLng in -180.0..180.0 && rawLat in -90.0..90.0)
                    endLatLng = when (type) {
                        LocationUtils.bd09 -> {
                            val wgs84ToGcj02 =
                                LocationUtils.bd09ToGcj02(get(0).toDouble(), get(1).toDouble())
                            LatLng(wgs84ToGcj02[1], wgs84ToGcj02[0])
                        }

                        LocationUtils.gps84 -> {
                            val wgs84ToGcj02 =
                                LocationUtils.wgs84ToGcj02(get(0).toDouble(), get(1).toDouble())
                            LatLng(wgs84ToGcj02[1], wgs84ToGcj02[0])
                        }

                        else -> {
                            LatLng(get(1).toDouble(), get(0).toDouble())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (type !in listOf(LocationUtils.bd09, LocationUtils.gcj02, LocationUtils.gps84)) return
            if (startLatLng == null || endLatLng == null) {
                return
            }

            val model = MockMessageModel(
                startNavi = PoiInfoModel().apply {
                    poiInfoType = PoiInfoType.NAVI_START
                    uid = ""
                    name = ""
                    latLng = startLatLng
                },
                endNavi = PoiInfoModel().apply {
                    poiInfoType = PoiInfoType.NAVI_END
                    uid = ""
                    name = ""
                    latLng = endLatLng
                },
                naviType = NaviType.NAVI,
                speed = MMKVUtils.getSpeed(),
                uid = ""
            )

            SearchManager.INSTANCE.addSearchManagerListener(object :
                SearchManager.SearchManagerListener {
                override fun onRouteResultLines(routeLines: List<RouteLines>?) {
                    SearchManager.INSTANCE.removeSearchManagerListener(this)
                    if (routeLines?.isEmpty() != false) {
                        ToastUtils.showShort("路线规划数据获取失败,请检测网络or数据是否正确!")
                        return
                    }
                    //使用第一条路线
                    SearchManager.INSTANCE.selectDriverLine(routeLines[0])
                    startMockServer(context, model)
                    SearchManager.INSTANCE.removeSearchManagerListener(this)
                }
            })

            when (routeType) {
                BICYCLE -> {
                    SearchManager.INSTANCE.bikingSearch(
                        startLatLng,
                        endLatLng,
                        wayList = null,
                        ridingType = 0
                    )
                }

                ELECTRIC_BICYCLE -> {
                    SearchManager.INSTANCE.bikingSearch(
                        startLatLng,
                        endLatLng,
                        wayList = null,
                        ridingType = 1
                    )
                }

                else -> {
                    SearchManager.INSTANCE.driverSearch(
                        startLatLng,
                        endLatLng,
                        true
                    )
                }
            }
        }
    }


    private fun startMockServer(context: Context, parcelable: MockMessageModel?) {
        //判断  为null先启动服务  悬浮窗需要
        parcelable?.run {
            if (!Utils.isAllowMockLocation(context)) {
                Toast.makeText(
                    context,
                    "将本应用设置为\"模拟位置信息应用\"，否则无法正常使用",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
        //启动服务  定位以及悬浮窗
        parcelable?.let { GpsService.start(context, it) }
    }
}
