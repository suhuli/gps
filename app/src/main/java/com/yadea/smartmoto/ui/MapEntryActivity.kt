package com.yadea.smartmoto.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.yadea.smartmoto.maps.MapPreferences
import com.yadea.smartmoto.manager.SearchManager
import com.yadea.smartmoto.server.GpsService

/** No MapView, location client or search instance exists on this screen. */
class MapEntryActivity : AppCompatActivity() {
    private var switching = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        switching = intent.getBooleanExtra("switch_map", false)
        if (!MapPreferences.consented(this)) showPrivacy() else showSelection()
    }
    private fun showPrivacy() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 16) }
        content.addView(TextView(this).apply {
            text = "雅迪智行提供地图选点、搜索、路线规划和系统模拟定位。\n\n仅在你同意并选择地图后初始化相应服务。地图、搜索和定位由百度或高德 SDK 提供；使用相关功能时，相应服务可能处理位置、设备与网络信息。仅在使用时申请必要权限。\n\n路线历史保存在本机；停止模拟可从常驻通知操作。模拟位置可能被其他应用识别。\n\n你可在设置中撤回同意并清除本应用的地点历史与地图偏好。请同时阅读所选服务的隐私政策。"
            setTextColor(Color.DKGRAY); textSize = 15f
        })
        listOf("百度地图隐私政策" to "https://lbsyun.baidu.com/index.php?title=openprivacy",
            "高德地图隐私政策" to "https://lbs.amap.com/pages/privacy/").forEach { (title, url) ->
            content.addView(Button(this).apply { text = title; setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure { Toast.makeText(this@MapEntryActivity, "无法打开浏览器", Toast.LENGTH_SHORT).show() }
            } })
        }
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle("隐私与使用说明").setView(scroll).setCancelable(false)
            .setNegativeButton("不同意并退出") { _, _ -> finish() }
            .setPositiveButton("已阅读并同意") { _, _ ->
                MapPreferences.prefs(this).edit().putBoolean("privacy_v1", true).apply(); showSelection()
            }.show()
    }
    private fun showSelection() {
        if (!switching) MapPreferences.remembered(this)?.let { enter(it, true); return }
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding((28 * dp).toInt(), 0, (28 * dp).toInt(), 0)
            setBackgroundColor(Color.rgb(245, 248, 253))
        }
        root.addView(TextView(this).apply { text = "雅迪智行"; textSize = 30f; setTextColor(Color.rgb(20, 45, 74)); gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = "选择地图服务\n地图、地点搜索与路线规划一起切换"; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 24, 0, 32) })
        val remember = CheckBox(this).apply { text = "记住选择，下次直接进入"; isChecked = MapPreferences.remembered(this@MapEntryActivity) != null }
        MapPreferences.Provider.values().forEach { provider ->
            root.addView(Button(this).apply {
                text = "使用${provider.title}"; textSize = 18f
                setOnClickListener { enter(provider, remember.isChecked) }
            }, LinearLayout.LayoutParams(-1, (60 * dp).toInt()).apply { bottomMargin = (12 * dp).toInt() })
        }
        root.addView(remember)
        root.addView(TextView(this).apply { text = "可在设置中切换；高德电动车专用路线暂不可用"; textSize = 12f; gravity = Gravity.CENTER; setPadding(0, 24, 0, 0) })
        setContentView(root)
    }
    private fun enter(provider: MapPreferences.Provider, remember: Boolean) {
        if (GpsService.isRunning && !switching) {
            AlertDialog.Builder(this).setTitle("当前有模拟任务")
                .setMessage("重新选择地图将停止当前模拟。也可以返回原任务继续使用。")
                .setNegativeButton("返回原任务") { _, _ -> startActivity(Intent(this, MainActivity::class.java)); finish() }
                .setPositiveButton("停止并进入") { _, _ -> switching = true; enter(provider, remember) }.show()
            return
        }
        try {
            if (switching) {
                stopService(Intent(this, GpsService::class.java))
                SearchManager.INSTANCE.cancel(); SearchManager.INSTANCE.polylineList.clear()
            }
            MapPreferences.select(this, provider, remember)
            startActivity(Intent(this, GuideActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, if (e is com.yadea.smartmoto.maps.MapConfigurationException) e.message else "${provider.title}初始化失败，请检查 SDK 和配置后重试", Toast.LENGTH_LONG).show()
        }
    }
}
