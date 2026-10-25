package com.yadea.smartmoto.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.sug.SuggestionResult
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.yadea.smartmoto.R
import com.yadea.smartmoto.adaper.PoiListAdapter
import com.yadea.smartmoto.databinding.ActivityPickBinding
import com.yadea.smartmoto.manager.FollowMode
import com.yadea.smartmoto.manager.MapLocationManager
import com.yadea.smartmoto.maps.*
import com.yadea.smartmoto.model.PoiInfoModel
import com.yadea.smartmoto.model.PoiInfoType
import com.yadea.smartmoto.widget.InputLatLngDialog

class PickMapPoiActivity : BaseActivity<ActivityPickBinding, BaseViewModel>(), View.OnClickListener {
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = PoiListAdapter()
    private lateinit var places: PlaceSearch
    private var locationManager: MapLocationManager? = null
    private var selected: PoiInfoModel? = null
    private var type = PoiInfoType.DEFAULT
    private var index = -1
    private var searchVersion = 0
    private var mapMoveVersion = 0
    private var skipReverse = false
    override fun initViewModel() = BaseViewModel::class.java
    override fun getLayout() = R.layout.activity_pick
    override fun initView() {
        type = intent.getIntExtra("from_tag", PoiInfoType.DEFAULT)
        index = intent.getIntExtra("index", -1)
        dataBinding.clicklistener = this
        dataBinding.isShowSearch = false
        places = PlaceSearch(this)
        dataBinding.recycler.layoutManager = LinearLayoutManager(this)
        dataBinding.recycler.adapter = adapter
        adapter.setOnItemClickListener(object : PoiListAdapter.OnItemClickListener {
            override fun onItemClick(poiInfo: SuggestionResult.SuggestionInfo) {
                val point = poiInfo.pt ?: return
                places.invalidate(); searchVersion++
                select(PoiInfoModel(point, poiInfo.uid, poiInfo.key, type, poiInfo.city))
                editViewShow(false)
                skipReverse = true
                dataBinding.mapview.map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, 16f))
            }
        })
        dataBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                val version = ++searchVersion
                places.invalidate()
                adapter.setData(null)
                if (query.isEmpty()) { dataBinding.recycler.visibility = View.GONE; return }
                handler.postDelayed({
                    if (version != searchVersion || isFinishing) return@postDelayed
                    places.suggestions(query, dataBinding.etSearchCity.text?.toString().orEmpty()) { list, error ->
                        if (version != searchVersion || isFinishing) return@suggestions
                        if (error != null) ToastUtils.showLong(error)
                        else if (list.isNullOrEmpty()) ToastUtils.showShort("未找到地点，请调整关键词或城市")
                        adapter.setData(list); dataBinding.recycler.visibility = View.VISIBLE
                    }
                }, 350)
            }
        })
        dataBinding.mapview.map.setOnMapStatusChangeListener(object : ProviderMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(mapStatus: MapStatus?) {}
            override fun onMapStatusChangeStart(mapStatus: MapStatus?, reason: Int) { mapMoveVersion++; places.invalidate() }
            override fun onMapStatusChange(mapStatus: MapStatus?) {}
            override fun onMapStatusChangeFinish(mapStatus: MapStatus?) {
                val point = mapStatus?.target ?: return
                if (skipReverse) { skipReverse = false; return }
                editViewShow(false)
                select(PoiInfoModel(point, point.toString(), "所选坐标", type, ""))
                val version = ++mapMoveVersion
                handler.postDelayed({
                    if (version != mapMoveVersion || isFinishing) return@postDelayed
                    places.reverse(point) { model, error ->
                        if (version != mapMoveVersion || isFinishing) return@reverse
                        if (model != null) select(model.apply { poiInfoType = type })
                        if (error != null) ToastUtils.showShort(error)
                    }
                }, 300)
            }
        })
        val restored = intent.getParcelableExtra<PoiInfoModel>("model")
        if (restored?.latLng != null) {
            select(restored)
            skipReverse = true
            dataBinding.mapview.map.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(restored.latLng!!, 16f))
        }
        locationManager = MapLocationManager(this, dataBinding.mapview.map, if (restored == null) FollowMode.MODE_SINGLE else FollowMode.MODE_NONE)
    }
    private fun select(model: PoiInfoModel) {
        selected = model
        dataBinding.tvPoiName.text = model.name
        dataBinding.tvLonlat.text = model.latLng?.let { "GCJ-02  经度 %.6f，纬度 %.6f".format(it.longitude, it.latitude) }
        dataBinding.city = model.city
    }
    override fun initData() {}
    override fun initObserver() {}
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.iv_search -> { editViewShow(true); dataBinding.etSearch.requestFocus() }
            R.id.confirm_location -> {
                val model = selected ?: run { ToastUtils.showShort("请先选择地点"); return }
                setResult(RESULT_OK, Intent().putExtra("poiInfo", model).putExtra("index", index)); finish()
            }
            R.id.iv_cur_location -> dataBinding.mapview.map.locationData?.let {
                dataBinding.mapview.map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f))
            } ?: ToastUtils.showShort("尚未获取当前位置，请检查定位权限")
            R.id.iv_back -> finish()
            R.id.tv_input -> InputLatLngDialog(this, object : InputLatLngDialog.InputLatLngDialogListener {
                override fun onConfirm(latLng: LatLng) {
                    if (!latLng.latitude.isFinite() || !latLng.longitude.isFinite() || latLng.latitude !in -90.0..90.0 || latLng.longitude !in -180.0..180.0) {
                        ToastUtils.showShort("经纬度超出有效范围"); return
                    }
                    dataBinding.mapview.map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 16f))
                }
            }).show()
        }
    }
    private fun editViewShow(show: Boolean) {
        dataBinding.isShowSearch = show
        dataBinding.llSearch.layoutParams = dataBinding.llSearch.layoutParams.apply { width = if (show) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT }
        dataBinding.etSearch.visibility = if (show) View.VISIBLE else View.GONE
        dataBinding.etSearchCity.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) { searchVersion++; KeyboardUtils.hideSoftInput(dataBinding.etSearch); dataBinding.recycler.visibility = View.GONE }
    }
    override fun onResume() { super.onResume(); dataBinding.mapview.onResume() }
    override fun onPause() { dataBinding.mapview.onPause(); super.onPause() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        places.destroy(); locationManager?.onDestroy(); dataBinding.mapview.onDestroy(); super.onDestroy()
    }
}
