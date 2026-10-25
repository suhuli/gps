package com.yadea.smartmoto.ui

import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.yadea.smartmoto.root.RootEngine
import com.yadea.smartmoto.root.RootFrame

/** Diagnostics and safe parameters. Never asks su to execute arbitrary commands. */
class RootEngineActivity : AppCompatActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)); setBackgroundColor(0xFFF5F8FC.toInt()) }
        val scroll = ScrollView(this).apply { addView(body) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
        fun text(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; setTextColor(0xFF20364B.toInt()); setPadding(0, dp(10), 0, dp(10)) }
        body.addView(text("Root 系统定位", 25f))
        body.addView(text("需要已启用的 LSPosed 模块，系统框架 android 为必选作用域；电话和融合增强需选中对应系统服务（手机上不存在的服务无需选择）。启用或升级模块后重启手机。仅有 Root 并不代表模块已生效。此测试版没有旧模拟定位后备。", 14f))
        val status = text("点击检查模块连接", 14f); body.addView(status)
        body.addView(Button(this).apply { text = "检查引擎与查看状态"; setOnClickListener {
            isEnabled = false
            Thread {
                val report = runCatching { RootEngine.diagnostics(this@RootEngineActivity) }.getOrDefault("检查失败，请查看 LSPosed 日志")
                runOnUiThread { if (!isFinishing) { status.text = report; isEnabled = true } }
            }.start()
        } })
        body.addView(Button(this).apply { text = "路线管理与编辑"; setOnClickListener { startActivity(android.content.Intent(this@RootEngineActivity, RootRoutesActivity::class.java)) } })
        body.addView(Button(this).apply { text = "方向摇杆与自动前进"; setOnClickListener { startActivity(android.content.Intent(this@RootEngineActivity, RootMotionActivity::class.java)) } })
        body.addView(Button(this).apply { text = "GNSS / NMEA 观察面板"; setOnClickListener { startActivity(android.content.Intent(this@RootEngineActivity, RootGnssActivity::class.java)) } })
        val options = RootEngine.prefs(this)
        fun field(title: String, value: String): EditText {
            body.addView(text(title, 13f))
            return EditText(this).apply { setText(value); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED; body.addView(this) }
        }
        val interval = field("位置回调间隔 / ms（250–5000）", options.getLong("interval_ms", 1000).toString())
        val altitude = field("海拔 / m（-1000–100000）", options.getString("altitude", "80.0")!!)
        val accuracy = field("位置精度 / m（0.5–10000）", options.getString("accuracy", "1.0")!!)
        body.addView(text("路线到达终点后的行为", 13f))
        val endModes = listOf("hold", "stop", "loop", "ping_pong")
        val endMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@RootEngineActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("保持终点（速度归零）", "停止并恢复系统定位", "闭环循环（沿连接段返回起点）", "往返运行"))
            setSelection(endModes.indexOf(options.getString("route_end_mode", "hold")).coerceAtLeast(0)); body.addView(this)
        }
        val amplitude = field("报告速度波动 / km/h（0–30；固定点和暂停保持零速度）",options.getString("speed_amplitude_kmh","0")!!)
        val satellites = field("最少模拟卫星 / 颗（0–35）", options.getInt("min_satellites", 12).toString())
        body.addView(text("实验参数仅在模拟任务运行期间生效，停止、会话丢失后恢复。屏蔽提供器/围栏/地理编码会影响依赖这些接口的应用；默认关闭。", 13f))
        val switches = com.yadea.smartmoto.root.RootSettings.flags.associate { flag ->
            flag.key to CheckBox(this).apply { text = flag.title; isChecked = options.getBoolean(flag.key,flag.default); body.addView(this) }
        }
        body.addView(Button(this).apply { text = "保存参数（下次启动任务生效）"; setOnClickListener {
            try {
                val ms = interval.text.toString().toLong(); require(ms in 250..5000)
                val sample = RootFrame(0.0, 0.0, 0.0, 0.0, altitude.text.toString().toDouble(), accuracy.text.toString().toFloat())
                val speedAmplitude=amplitude.text.toString().toDouble(); require(speedAmplitude.isFinite() && speedAmplitude in 0.0..30.0)
                val satelliteCount = satellites.text.toString().toInt(); require(satelliteCount in 0..35)
                val edit = options.edit().putString("speed_amplitude_kmh",speedAmplitude.toString()).putInt("min_satellites", satelliteCount).putString("route_end_mode", endModes[endMode.selectedItemPosition])
                switches.forEach { (key, box) -> edit.putBoolean(key,box.isChecked) }
                edit.putLong("interval_ms", ms).putString("altitude", sample.altitude.toString()).putString("accuracy", sample.accuracy.toString()).apply()
                Toast.makeText(this@RootEngineActivity, "已保存，下次启动任务生效", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@RootEngineActivity, "参数格式或范围不正确", Toast.LENGTH_LONG).show() }
        } })
        body.addView(Button(this).apply { text = "紧急停止并恢复系统定位"; setOnClickListener {
            AlertDialog.Builder(this@RootEngineActivity).setTitle("停止所有模拟任务？").setMessage("停止前台服务并关闭系统 Hook 中的模拟状态。")
                .setNegativeButton("取消", null).setPositiveButton("停止") { _, _ ->
                    stopService(android.content.Intent(this@RootEngineActivity, com.yadea.smartmoto.server.GpsService::class.java))
                    stopService(android.content.Intent(this@RootEngineActivity, com.yadea.smartmoto.server.RootAuxService::class.java))
                    RootEngine.stop(); status.text = RootEngine.diagnostics(this@RootEngineActivity)
                }.show()
        } })
        body.addView(text("迁移进度：当前为系统位置、回调、既有路线与悬浮窗接入测试。路线管理、摇杆和 GNSS 观察页面已接入源码，尚待构建及真机验收。原生传感器与电话/Wi-Fi/厂商融合为按需启用的实验分支，状态见引擎诊断；构建通过不代表真机兼容。\n\n引擎确认不等于第三方 App 已采用位置。此构建仅供授权测试设备使用。", 13f))
        setContentView(scroll); ViewCompat.requestApplyInsets(scroll)
    }
}
