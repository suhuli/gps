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
        body.addView(text("需要已启用的 LSPosed 模块，作用域为系统框架 android，启用或升级模块后重启手机。仅有 Root 并不代表模块已生效。此测试版没有旧模拟定位后备。", 14f))
        val status = text("点击检查模块连接", 14f); body.addView(status)
        body.addView(Button(this).apply { text = "检查引擎与查看状态"; setOnClickListener {
            isEnabled = false
            Thread {
                val report = runCatching { RootEngine.diagnostics(this@RootEngineActivity) }.getOrDefault("检查失败，请查看 LSPosed 日志")
                runOnUiThread { if (!isFinishing) { status.text = report; isEnabled = true } }
            }.start()
        } })
        val options = RootEngine.prefs(this)
        fun field(title: String, value: String): EditText {
            body.addView(text(title, 13f))
            return EditText(this).apply { setText(value); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED; body.addView(this) }
        }
        val interval = field("位置回调间隔 / ms（250–5000）", options.getLong("interval_ms", 1000).toString())
        val altitude = field("海拔 / m（-1000–100000）", options.getString("altitude", "80.0")!!)
        val accuracy = field("位置精度 / m（0.5–10000）", options.getString("accuracy", "1.0")!!)
        body.addView(Button(this).apply { text = "保存参数（下次启动任务生效）"; setOnClickListener {
            try {
                val ms = interval.text.toString().toLong(); require(ms in 250..5000)
                val sample = RootFrame(0.0, 0.0, 0.0, 0.0, altitude.text.toString().toDouble(), accuracy.text.toString().toFloat())
                options.edit().putLong("interval_ms", ms).putString("altitude", sample.altitude.toString()).putString("accuracy", sample.accuracy.toString()).apply()
                Toast.makeText(this@RootEngineActivity, "已保存，下次启动任务生效", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@RootEngineActivity, "参数格式或范围不正确", Toast.LENGTH_LONG).show() }
        } })
        body.addView(Button(this).apply { text = "紧急停止并恢复系统定位"; setOnClickListener {
            AlertDialog.Builder(this@RootEngineActivity).setTitle("停止所有模拟任务？").setMessage("停止前台服务并关闭系统 Hook 中的模拟状态。")
                .setNegativeButton("取消", null).setPositiveButton("停止") { _, _ ->
                    stopService(android.content.Intent(this@RootEngineActivity, com.yadea.smartmoto.server.GpsService::class.java))
                    RootEngine.stop(); status.text = RootEngine.diagnostics(this@RootEngineActivity)
                }.show()
        } })
        body.addView(text("迁移进度：当前为系统位置、回调、既有路线与悬浮窗接入测试。Portal 路线编辑界面、GNSS 面板、原生传感器及网络相关能力仍在迁移清单中，尚未完成或验收。\n\n引擎确认不等于第三方 App 已采用位置。此构建仅供授权测试设备使用。", 13f))
        setContentView(scroll); ViewCompat.requestApplyInsets(scroll)
    }
}
