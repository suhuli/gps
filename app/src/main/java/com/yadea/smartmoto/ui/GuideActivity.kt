package com.yadea.smartmoto.ui

import android.Manifest
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.ToastUtils
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.yadea.smartmoto.R
import com.yadea.smartmoto.databinding.ActivityGuideBinding
import com.yadea.smartmoto.server.GpsService
import com.yadea.smartmoto.utils.Utils
import java.lang.Exception

/**
 * @author jiayu.liu
 */
class GuideActivity : BaseActivity<ActivityGuideBinding, BaseViewModel>(), View.OnClickListener {
    companion object {
        private const val PERMISSION_REQUEST = 1001
        private const val PERMISSION_BACKGROUND_REQUEST = 1002
    }

    private val permissions: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )


    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_guide
    }

    override fun initView() {
        ClickUtils.applySingleDebouncing(dataBinding.btnGo, this)
        dataBinding.privacyConsent.isChecked = com.yadea.smartmoto.maps.MapPreferences.consented(this)
        val policies = android.text.SpannableString("使用说明  ·  百度隐私政策  ·  高德隐私政策")
        fun link(label: String, action: () -> Unit) {
            val start = policies.toString().indexOf(label)
            policies.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) { action() }
            }, start, start + label.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        link("使用说明") {
            AlertDialog.Builder(this).setTitle("使用说明")
                .setMessage("雅迪智行提供地图选点、搜索、路线规划与系统模拟定位。勾选同意并选择地图后才初始化对应 SDK；相关服务可能处理位置、设备与网络信息。地点历史保存在本机；可从通知停止模拟，在设置中撤回同意。请勿用于违法或未经授权的用途。")
                .setPositiveButton("知道了", null).show()
        }
        fun openPolicy(url: String) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                .onFailure { ToastUtils.showShort("无法打开浏览器") }
        }
        link("百度隐私政策") { openPolicy("https://lbsyun.baidu.com/index.php?title=openprivacy") }
        link("高德隐私政策") { openPolicy("https://lbs.amap.com/pages/privacy/") }
        dataBinding.privacyLinks.text = policies
        dataBinding.privacyLinks.movementMethod = android.text.method.LinkMovementMethod.getInstance()
    }

    override fun initData() {
        viewModel.toast.observe(this) {

        }
    }

    override fun initObserver() {
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkPermission(): Boolean {
        val needPermissions = arrayListOf<String>()
        permissions.map {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                needPermissions.add(it)
            }
        }
        if (needPermissions.size == 0) {
            return true
        }

        requestPermissions(
            needPermissions.toArray(arrayOfNulls(needPermissions.size)),
            PERMISSION_REQUEST
        )
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            grantResults.map {
                if (it != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "需要权限", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }
        when (requestCode) {
            PERMISSION_REQUEST -> {
                grantResults.map {
                    if (it != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "需要定位权限", Toast.LENGTH_SHORT).show()
                        return
                    }
                }

            }

            else -> {}
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            dataBinding.btnGo -> {
                if (!dataBinding.privacyConsent.isChecked) {
                    ToastUtils.showShort("请先阅读并勾选同意使用说明与隐私政策")
                    return
                }
                com.yadea.smartmoto.maps.MapPreferences.prefs(this).edit().putBoolean("privacy_v1", true).apply()
                //权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkPermission()) {
                    return
                }
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    !getPreferences(MODE_PRIVATE).getBoolean("notification_asked", false)) {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply()
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1003)
                    ToastUtils.showLong("建议允许通知，以便暂停或停止后台模拟；授权后请再次点击进入")
                    return
                }
                if (!Utils.isAllowMockLocation(this)) {
                    startActivity(Intent(this, RootEngineActivity::class.java))
                    return
                }
                com.yadea.smartmoto.maps.MapPreferences.prefs(this).edit().putBoolean("intro_done", true).apply()
                startActivity(Intent(this, MapEntryActivity::class.java).putExtra("switch_map", intent.getBooleanExtra("switch_map", false)))
                finish()
            }

            else -> {}
        }
    }

}