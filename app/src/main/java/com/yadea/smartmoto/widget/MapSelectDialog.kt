package com.yadea.smartmoto.widget

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.yadea.smartmoto.maps.ProviderMap as BaiduMap
import com.yadea.smartmoto.maps.MapOverlay as Overlay
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.route.DrivingRouteLine
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.ScreenUtils
import com.castiel.common.dialog.BaseDialog
import com.yadea.smartmoto.R
import com.yadea.smartmoto.manager.utils.MapConvertUtils
import com.yadea.smartmoto.manager.utils.MapDrawUtils
import com.yadea.smartmoto.model.PoiInfoModel
import com.yadea.smartmoto.model.RouteLines

/**
 * @author jiayu.liu
 */
class MapSelectDialog(
    context: Context,
    private var routeLines: List<RouteLines>,
    private var start: LatLng?,
    private var end: LatLng?,
    wayList: MutableList<PoiInfoModel>?
) : BaseDialog(context) {

    private val binding = com.yadea.smartmoto.databinding.DialogSelectNaviMapBinding.inflate(layoutInflater)
    private val mHorizontalPadding = ConvertUtils.dp2px(20f)
    private val mMapPadding = ConvertUtils.dp2px(30f)
    private val screenWidth = ScreenUtils.getScreenWidth()
    private val mOverlayList = ArrayList<Overlay>()
    private var mainIndex = 0
    var listener: MapSelectDialogListener? = null

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        setContentView(binding.root)

        binding.textureMapview.onCreate(context, null)
        binding.textureMapview.showScaleControl(false)
        binding.textureMapview.showZoomControls(false)
        binding.textureMapview.map?.let {
            it.uiSettings?.isCompassEnabled = false
            it.uiSettings?.setAllGesturesEnabled(false)
            //渲染路线
            it.setOnMapLoadedCallback {
                start?.let { start ->
                    MapDrawUtils.drawMarkerToMap(it, start, "marker_start.png")
                }
                wayList?.map { model ->
                    model.latLng?.let { latLng ->
                        MapDrawUtils.drawMarkerToMap(it, latLng, "marker_way.png")
                    }
                }
                end?.let { end ->
                    MapDrawUtils.drawMarkerToMap(it, end, "marker_end.png")
                }
                drawLine(it)
            }
        }
        ClickUtils.applySingleDebouncing(binding.btnChange) {
            mainIndex = ++mainIndex % routeLines.size
            binding.textureMapview.map?.let {
                drawLine(it)
            }
        }
        ClickUtils.applySingleDebouncing(binding.btnSelect) {
            listener?.onSelectLine(routeLines[mainIndex])
            dismiss()
        }

        window?.let {
            val lp: WindowManager.LayoutParams = it.attributes
            lp.width = screenWidth - mHorizontalPadding
            lp.height = screenWidth + ConvertUtils.dp2px(60f)
            lp.gravity = Gravity.CENTER
            lp.dimAmount = 0.5f
            it.attributes = lp
        }

    }

    private fun drawLine(baiduMap: BaiduMap) {
        mOverlayList.map {
            it.remove()
        }.also {
            mOverlayList.clear()
        }

        routeLines.mapIndexed { index, line ->
            MapDrawUtils.drawLineToMap(
                baiduMap,
                line.route,
                Rect(mMapPadding, mMapPadding, mMapPadding, mMapPadding),
                index == mainIndex
            )?.let { overlay ->
                mOverlayList.add(overlay)
            }
        }
    }

    override fun show() {
        super.show()
        binding.textureMapview.onResume()
    }

    public fun onResume() {
        binding.textureMapview.onResume()
    }

    public fun onPause() {
        binding.textureMapview.onPause()
    }

    override fun dismiss() {
        binding.textureMapview.onPause()
        binding.textureMapview.onDestroy()
        super.dismiss()
    }

    interface MapSelectDialogListener {
        fun onSelectLine(routeLine: RouteLines)
    }
}