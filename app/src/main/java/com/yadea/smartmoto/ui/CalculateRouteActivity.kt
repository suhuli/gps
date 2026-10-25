package com.yadea.smartmoto.ui

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Rect
import android.text.TextUtils
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import com.yadea.smartmoto.maps.ProviderMap as BaiduMap
import com.yadea.smartmoto.maps.MapStatus
import com.yadea.smartmoto.maps.MapStatusUpdateFactory
import com.baidu.mapapi.search.route.*
import com.blankj.utilcode.util.*
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.yadea.smartmoto.R
import com.yadea.smartmoto.databinding.ActivityCalculateRouteBinding
import com.yadea.smartmoto.manager.FollowMode
import com.yadea.smartmoto.manager.MapLocationManager
import com.yadea.smartmoto.manager.SearchManager
import com.yadea.smartmoto.manager.utils.MapConvertUtils
import com.yadea.smartmoto.manager.utils.MapDrawUtils
import com.yadea.smartmoto.model.PoiInfoModel
import com.yadea.smartmoto.model.PoiInfoType
import com.yadea.smartmoto.model.RouteLines
import java.io.File
import kotlin.collections.ArrayList


/**
 * @author jiayu.liu
 */
class CalculateRouteActivity : BaseActivity<ActivityCalculateRouteBinding, BaseViewModel>(),
    View.OnClickListener {
    private lateinit var mBaiduMap: BaiduMap
    private var mapLocationManager: MapLocationManager? = null
    private val mDefaultPadding = ConvertUtils.dp2px(50f)
    private var mainIndex = 0

    /**
     * 算路成功的路线
     */
    private var routeLines: ArrayList<RouteLines> = arrayListOf()
    private val mSearchManagerListener = object : SearchManager.SearchManagerListener {
        override fun onRouteResultLines(routeLines: List<RouteLines>?) {
            viewModel.loading.value = false
            if (routeLines?.isEmpty() != false) {
                return
            }
            this@CalculateRouteActivity.routeLines = ArrayList(routeLines)
            mainIndex = 0
            mBaiduMap.clear()
            mBaiduMap.let {
                (dataBinding.tvStart.tag as PoiInfoModel?)?.latLng?.let { start ->
                    MapDrawUtils.drawMarkerToMap(it, start, "marker_start.png")
                }
                (dataBinding.tvEnd.tag as PoiInfoModel?)?.latLng?.let { end ->
                    MapDrawUtils.drawMarkerToMap(it, end, "marker_end.png")
                }

                routeLines.mapIndexed { index, line ->
                    MapDrawUtils.drawLineToMap(
                        it,
                        line.route,
                        Rect(
                            mDefaultPadding,
                            mDefaultPadding + dataBinding.clPanel.height,
                            mDefaultPadding,
                            mDefaultPadding
                        ),
                        index == mainIndex
                    )
                }
            }
            dataBinding.fileName = "${dataBinding.tvStart.text}-${dataBinding.tvEnd.text}"
        }
    }

    private var registerForActivityResultToStartPoi =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                it.data?.run {
                    setDataToView(getParcelableExtra<PoiInfoModel>("poiInfo"), dataBinding.tvStart)
                }
            }
        }


    private var registerForActivityResultToEndPoi =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                it.data?.run {
                    setDataToView(getParcelableExtra<PoiInfoModel>("poiInfo"), dataBinding.tvEnd)
                }
            }
        }

    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_calculate_route
    }

    override fun initView() {
        initMap()

        System.currentTimeMillis()

        ClickUtils.applySingleDebouncing(dataBinding.tvStart, this)
        ClickUtils.applySingleDebouncing(dataBinding.tvEnd, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnChange, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnStartRoute, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnSaveFile, this)
    }


    private fun initMap() {

        mBaiduMap = dataBinding.mapview.map

        mBaiduMap.setMapStatus(MapStatusUpdateFactory.zoomBy(16f))

        mapLocationManager = MapLocationManager(this, mBaiduMap, FollowMode.MODE_SINGLE)
    }


    override fun initData() {

    }


    override fun initObserver() {
    }

    override fun onClick(v: View?) {
        when (v) {
            dataBinding.tvStart -> {
                registerForActivityResultToStartPoi.launch(Intent(
                    this@CalculateRouteActivity,
                    PickMapPoiActivity::class.java
                ).apply {
                    putExtra("from_tag", PoiInfoType.DEFAULT)
                    if (dataBinding.tvStart.tag != null) {
                        putExtra("model", dataBinding.tvStart.tag as PoiInfoModel?)
                    }
                })
            }

            dataBinding.tvEnd -> {
                registerForActivityResultToEndPoi.launch(Intent(
                    this@CalculateRouteActivity,
                    PickMapPoiActivity::class.java
                ).apply {
                    putExtra("from_tag", PoiInfoType.DEFAULT)
                    if (dataBinding.tvEnd.tag != null) {
                        putExtra("model", dataBinding.tvEnd.tag as PoiInfoModel?)
                    }
                })
            }

            dataBinding.btnStartRoute -> {
                var stNode: PlanNode? = null
                (dataBinding.tvStart.tag as PoiInfoModel?)?.run {
                    stNode = PlanNode.withLocation(latLng);
                }

                var enNode: PlanNode? = null
                (dataBinding.tvEnd.tag as PoiInfoModel?)?.run {
                    enNode = PlanNode.withLocation(latLng);
                }

                if (stNode == null || enNode == null) {
                    ToastUtils.showShort("起终点不能null")
                    return
                }
                mBaiduMap.clear()
                routeLines.clear()
                viewModel.loading.value = true
                SearchManager.INSTANCE.driverSearch(stNode?.location, enNode?.location, true)
            }

            dataBinding.btnChange -> {
                if (routeLines.isEmpty()) {
                    ToastUtils.showShort("没有路线切换")
                    return
                }
                mBaiduMap.clear()

                mainIndex = ++mainIndex % routeLines.size
                mBaiduMap.let {
                    routeLines.mapIndexed { index, line ->
                        (dataBinding.tvStart.tag as PoiInfoModel?)?.latLng?.let { start ->
                            MapDrawUtils.drawMarkerToMap(it, start, "marker_start.png")
                        }
                        (dataBinding.tvEnd.tag as PoiInfoModel?)?.latLng?.let { end ->
                            MapDrawUtils.drawMarkerToMap(it, end, "marker_end.png")
                        }
                        MapDrawUtils.drawLineToMap(
                            it,
                            line.route,
                            Rect(
                                mDefaultPadding,
                                mDefaultPadding + dataBinding.clPanel.height,
                                mDefaultPadding,
                                mDefaultPadding
                            ),
                            index == mainIndex
                        )
                    }
                }
                dataBinding.fileName = "${dataBinding.tvStart.text}-${dataBinding.tvEnd.text}"
            }

            dataBinding.btnSaveFile -> {
                if (routeLines.isEmpty() || mainIndex < 0 || mainIndex >= routeLines.size) {
                    ToastUtils.showShort("数据列表为null！，无法保存")
                    return
                }
                val points = routeLines[mainIndex].route.toList()
                val filename = dataBinding.fileName?.toString()?.trim().orEmpty()
                if (filename.isEmpty() || filename.length > 100 || filename.any { it in "/\\:*?\"<>|" || it.code < 32 } || filename == "." || filename == "..") {
                    ToastUtils.showShort("文件名无效，请使用不含路径符号的名称"); return
                }
                lifecycleScope.launch {
                    viewModel.loading.value = true
                    try {
                        withContext(Dispatchers.IO) {
                            val file = File(getExternalFilesDir("nav_path"), "$filename.txt")
                            require(file.createNewFile()) { "文件已存在，请更换名称" }
                            try {
                                file.bufferedWriter().use { output ->
                                    points.forEach { output.write("${it.longitude},${it.latitude};") }
                                }
                            } catch (e: Exception) { file.delete(); throw e }
                        }
                        ToastUtils.showShort("保存成功（GCJ-02 坐标）"); finish()
                    } catch (e: Exception) { ToastUtils.showLong(e.message ?: "保存失败") }
                    finally { viewModel.loading.value = false }
                }
            }

            else -> {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SearchManager.INSTANCE.addSearchManagerListener(mSearchManagerListener)
        dataBinding.mapview.onResume()
    }

    override fun onPause() {
        super.onPause()
        SearchManager.INSTANCE.removeSearchManagerListener(mSearchManagerListener)
        dataBinding.mapview.onPause()
    }

    override fun onDestroy() {
        destroy()
        super.onDestroy()
    }

    private fun destroy() {
        mapLocationManager?.onDestroy()
        dataBinding.mapview.onDestroy()
    }


    private fun setDataToView(model: PoiInfoModel?, view: AppCompatTextView) {
        model?.run {
            when (poiInfoType) {
                PoiInfoType.DEFAULT -> {
                    view.text = String.format(
                        "%s",
                        name
                    )
                    view.tag = this
                }

                else -> {
                }
            }
        }
    }
}