package com.yadea.smartmoto.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.baidu.mapapi.model.LatLng
import com.yadea.smartmoto.root.*
import com.yadea.smartmoto.model.*
import com.yadea.smartmoto.manager.SearchManager
import com.yadea.smartmoto.utils.LocationUtils
import com.yadea.smartmoto.utils.MMKVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Route editing uses the existing provider-aware map picker and navigation confirmation screen. */
class RootRoutesActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private lateinit var repository: RouteRepository
    private var exporting: RoutePlan? = null
    private var allowPortalHistory = false
    private var renderGeneration = 0
    private var coordinates: EditText? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val poi = result.data?.getParcelableExtra<PoiInfoModel>("poiInfo")
            poi?.latLng?.let { coordinates?.append("${if (coordinates?.text.isNullOrBlank()) "" else "\n"}${it.latitude},${it.longitude}") }
        }
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        val plan = exporting; exporting = null
        if (uri != null && plan != null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(RouteCodec.encode(plan)) } } }
                .onSuccess { toast("已导出") }.onFailure { toast(it.message ?: "导出失败") }
        }
    }
    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val plans = contentResolver.openInputStream(uri)!!.use { RouteCodec.decodeMany(it.readBytesLimited().toString(Charsets.UTF_8),allowPortalHistory) }
                plans.forEach { repository.save(it.copy(id = UUID.randomUUID().toString())) }
            } }.onSuccess { refresh() }.onFailure { toast(it.message ?: "导入失败") }
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); allowPortalHistory=state?.getBoolean("portal_history",false) ?: false; repository = RouteRepository(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24); setBackgroundColor(0xFFF5F8FC.toInt()) }
        installRootContent(body); refresh()
    }
    override fun onSaveInstanceState(out: Bundle) { out.putBoolean("portal_history",allowPortalHistory); super.onSaveInstanceState(out) }
    private fun button(parent: LinearLayout, title: String, click: () -> Unit) {
        parent.addView(Button(this).apply { text = title; setOnClickListener { click() } })
    }
    private fun refresh() {
        val generation = ++renderGeneration
        body.removeAllViews(); body.addView(TextView(this).apply { text = "路线管理"; textSize = 25f })
        body.addView(TextView(this).apply { text = "新建、编辑、反向、导入导出。地图选点为 GCJ-02；外部路线必须携带正确坐标类型。GPX / KML / CSV 仍从原轨迹导入入口使用。" })
        button(body,"新建路线") { edit(null) }; button(body,"导入路线 JSON") { allowPortalHistory=false; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        button(body,"导入 Portal 历史路线 JSON（GCJ-02）") { allowPortalHistory=true; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        lifecycleScope.launch {
            val plans = withContext(Dispatchers.IO) { repository.list() }
            if (isFinishing || generation != renderGeneration) return@launch
            plans.forEach { plan -> button(body,"${plan.name} · ${plan.points.size} 点") {
                AlertDialog.Builder(this@RootRoutesActivity).setTitle(plan.name)
                    .setItems(arrayOf("预览并运行", "编辑", "另存反向路线", "导出", "删除")) { _, index ->
                        when(index) {
                            0 -> play(plan)
                            1 -> edit(plan)
                            2 -> lifecycleScope.launch { runCatching { withContext(Dispatchers.IO) { repository.save(plan.reversed()) } }.onSuccess { refresh() }.onFailure { toast(it.message ?: "保存失败") } }
                            3 -> { exporting = plan; export.launch("${plan.name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_")}.json") }
                            4 -> AlertDialog.Builder(this@RootRoutesActivity).setMessage("删除 ${plan.name}？").setNegativeButton("取消",null).setPositiveButton("删除") { _, _ -> lifecycleScope.launch { runCatching { withContext(Dispatchers.IO) { repository.delete(plan) } }.onSuccess { refresh() }.onFailure { toast(it.message ?: "删除失败") } } }.show()
                        }
                    }.show()
            } }
        }
    }
    private fun edit(plan: RoutePlan?) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,16,24,16) }
        val name = EditText(this).apply { hint = "路线名称"; setText(plan?.name ?: "") }; panel.addView(name)
        val crs = Spinner(this).apply { adapter = ArrayAdapter(this@RootRoutesActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("gcj02", "wgs84", "bd09")); setSelection(arrayOf("gcj02","wgs84","bd09").indexOf(plan?.coordinateType ?: "gcj02").coerceAtLeast(0)) }; panel.addView(crs)
        val points = EditText(this).apply { hint = "每行：纬度,经度（至少两点）\n调整行顺序即可调整路线"; minLines = 7; maxLines = 15; setText(plan?.points?.joinToString("\n") { "${it.latitude},${it.longitude}" } ?: "") }; panel.addView(points); coordinates = points
        button(panel,"地图追加选点（仅 GCJ-02）") {
            if (crs.selectedItem.toString() != "gcj02") { toast("请先选择 GCJ-02，避免混用坐标"); return@button }
            picker.launch(Intent(this, PickMapPoiActivity::class.java).putExtra("model", PoiInfoModel()))
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (plan == null) "新建路线" else "编辑路线")
            .setView(ScrollView(this).apply { addView(panel) }).setNegativeButton("取消",null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val parsed = points.text.lines().filter { it.isNotBlank() }.map { line ->
                    val pair = line.trim().split(Regex("[,，\\s]+")); require(pair.size == 2) { "每行需要纬度、经度两个值" }; RoutePoint(pair[0].toDouble(),pair[1].toDouble())
                }
                val updated = RoutePlan(plan?.id ?: UUID.randomUUID().toString(), name.text.toString().trim(), parsed, crs.selectedItem.toString()); updated.validate()
                lifecycleScope.launch {
                    runCatching { withContext(Dispatchers.IO) { repository.save(updated) } }.onSuccess { dialog.dismiss(); refresh() }.onFailure { toast(it.message ?: "保存失败") }
                }
            } catch (e: Exception) { toast(e.message ?: "格式不正确") }
        } }; dialog.show()
    }
    private fun play(plan: RoutePlan) {
        if (com.yadea.smartmoto.server.GpsService.isRunning) { toast("请先停止当前模拟任务，避免替换运行中的路线"); return }
        val route = ArrayList<LatLng>(plan.points.size)
        plan.points.forEach { point ->
            val xy = when(plan.coordinateType) {
                "wgs84" -> LocationUtils.wgs84ToGcj02(point.longitude,point.latitude)
                "bd09" -> LocationUtils.bd09ToGcj02(point.longitude,point.latitude)
                else -> doubleArrayOf(point.longitude,point.latitude)
            }
            route.add(LatLng(xy[1],xy[0]))
        }
        SearchManager.INSTANCE.polylineList = route
        val model = MockMessageModel(naviType = NaviType.NAVI_FILE, speed = MMKVUtils.getSpeed(), pointType = LocationUtils.gcj02)
        startActivity(Intent(this,MockLocationActivity::class.java).putExtra("model",model))
    }
    private fun toast(message: String) { Toast.makeText(this,message,Toast.LENGTH_LONG).show() }
}
