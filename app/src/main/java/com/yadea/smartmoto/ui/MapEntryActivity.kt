package com.yadea.smartmoto.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.yadea.smartmoto.R
import com.yadea.smartmoto.maps.MapPreferences
import com.yadea.smartmoto.manager.SearchManager
import com.yadea.smartmoto.server.GpsService
import com.yadea.smartmoto.utils.Utils

/** Routing and selection only. Never initializes a map SDK without prior consent. */
class MapEntryActivity : AppCompatActivity() {
    private var switching = false
    private var chosen: MapPreferences.Provider? = null
    private val blue = Color.rgb(63, 129, 193)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        switching = intent.getBooleanExtra("switch_map", false)
        val prefs = MapPreferences.prefs(this)
        val saved = prefs.getString("last_selected", null)?.let { name ->
            runCatching { MapPreferences.Provider.valueOf(name) }.getOrNull()
        } ?: MapPreferences.remembered(this)
        val locationReady = Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val destination = com.yadea.smartmoto.maps.EntryRouter.destination(
            MapPreferences.consented(this), locationReady, Utils.isAllowMockLocation(this), saved != null, switching)
        if (destination == com.yadea.smartmoto.maps.EntryRouter.Destination.GUIDE) {
            startActivity(Intent(this, GuideActivity::class.java).putExtra("switch_map", switching))
            finish(); return
        }
        // Existing users with valid consent/provider do not need to repeat onboarding.
        if (destination == com.yadea.smartmoto.maps.EntryRouter.Destination.MAIN && saved != null) { enter(saved); return }
        chosen = state?.getString("chosen")?.let { runCatching { MapPreferences.Provider.valueOf(it) }.getOrNull() } ?: saved
        showSelection()
    }
    override fun onSaveInstanceState(out: Bundle) {
        chosen?.let { out.putString("chosen", it.name) }
        super.onSaveInstanceState(out)
    }
    private fun showSelection() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(24))
            setBackgroundColor(Color.rgb(245, 248, 252))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher); contentDescription = "雅迪智行" }, LinearLayout.LayoutParams(dp(44), dp(44)))
        brand.addView(label("雅迪智行", 18f, Color.rgb(32, 54, 75), true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        root.addView(brand)
        root.addView(label("选择你的地图", 28f, Color.rgb(32, 54, 75), true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(36) })
        root.addView(label("选点、搜索与路线规划，统一随地图切换", 14f, Color.rgb(113, 131, 152)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(28) })
        val cards = mutableMapOf<MapPreferences.Provider, MaterialCardView>()
        val radios = mutableMapOf<MapPreferences.Provider, RadioButton>()
        val action = MaterialButton(this).apply { text = "开始使用"; textSize = 16f; cornerRadius = dp(28); setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(blue) }
        fun refresh() {
            cards.forEach { (provider, card) ->
                val selected = chosen == provider
                card.strokeColor = if (selected) blue else Color.rgb(226, 233, 241)
                card.strokeWidth = dp(if (selected) 2 else 1)
                card.setCardBackgroundColor(if (selected) Color.rgb(238, 246, 255) else Color.WHITE)
                card.isSelected = selected; radios[provider]?.isChecked = selected
            }
            action.isEnabled = chosen != null; action.alpha = if (chosen != null) 1f else 0.45f
        }
        MapPreferences.Provider.values().forEach { provider ->
            val card = MaterialCardView(this).apply { radius = dp(20).toFloat(); cardElevation = 0f; isClickable = true; isFocusable = true }
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(16), dp(20)) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(if (provider == MapPreferences.Provider.BAIDU) "百" else "高", 22f, blue, true).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(Color.rgb(226, 239, 253)); cornerRadius = dp(14).toFloat() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
            words.addView(label(provider.title, 19f, Color.rgb(32, 54, 75), true))
            words.addView(label("地图 · 搜索 · 路线", 12f, Color.rgb(113, 131, 152)).apply { setPadding(0, dp(6), 0, 0) })
            row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            val radio = RadioButton(this).apply { isClickable = false; isFocusable = false; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO; buttonTintList = android.content.res.ColorStateList.valueOf(blue) }
            row.addView(radio); radios[provider] = radio
            content.addView(row)
            content.addView(label(if (provider == MapPreferences.Provider.BAIDU) "支持步行、骑行、驾车等路线规划" else "支持步行、骑行与驾车；暂无电动车专用路线", 12f, Color.rgb(113, 131, 152)).apply { setPadding(0, dp(18), 0, 0) })
            card.addView(content); cards[provider] = card
            card.contentDescription = "${provider.title}，点按选择"
            card.setOnClickListener { chosen = provider; refresh() }
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        }
        root.addView(label("自动记住本次选择，下次直接进入主界面。\n可随时在「扩展功能 → 模拟功能设置」中切换。", 13f, Color.rgb(113, 131, 152)).apply { setLineSpacing(dp(5).toFloat(), 1f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(24) })
        action.setOnClickListener { chosen?.let { enter(it) } }
        root.addView(action, LinearLayout.LayoutParams(-1, dp(58)))
        refresh(); setContentView(scroll); ViewCompat.requestApplyInsets(scroll)
    }
    private fun enter(provider: MapPreferences.Provider) {
        try {
            if (switching) {
                stopService(Intent(this, GpsService::class.java))
                SearchManager.INSTANCE.cancel(); SearchManager.INSTANCE.polylineList.clear()
            }
            MapPreferences.select(this, provider, true)
            MapPreferences.prefs(this).edit().putBoolean("setup_complete", true).apply()
            startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, if (e is com.yadea.smartmoto.maps.MapConfigurationException) e.message else "${provider.title}初始化失败，请检查配置后重试", Toast.LENGTH_LONG).show()
            if (isFinishing.not()) showSelection()
        }
    }
}
