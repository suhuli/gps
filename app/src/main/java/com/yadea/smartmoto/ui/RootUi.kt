package com.yadea.smartmoto.ui

import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

internal fun AppCompatActivity.installRootContent(body: LinearLayout) {
    val scroll = ScrollView(this).apply { addView(body) }
    WindowCompat.setDecorFitsSystemWindows(window, false)
    ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
    }
    setContentView(scroll); ViewCompat.requestApplyInsets(scroll)
}
