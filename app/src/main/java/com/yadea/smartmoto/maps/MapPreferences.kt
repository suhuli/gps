package com.yadea.smartmoto.maps

import android.content.Context
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.baidu.location.LocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.location.AMapLocationClient
import com.amap.api.services.core.ServiceSettings

/** Selection is process scoped; remember only when the user explicitly opts in. */
class MapConfigurationException(message: String) : IllegalStateException(message)

object MapPreferences {
    enum class Provider(val title: String) { BAIDU("百度地图"), AMAP("高德地图") }
    var selected: Provider? = null
        private set
    private val authListener = com.baidu.mapapi.RequestAuthResultListener { status, _ ->
        if (status != 0) com.blankj.utilcode.util.ToastUtils.showLong(BaiduAuthErrors.message(status))
    }
    private val initialized = mutableSetOf<Provider>()
    fun prefs(context: Context) = context.getSharedPreferences("map_preferences", Context.MODE_PRIVATE)
    fun remembered(context: Context): Provider? = prefs(context).getString("remembered", null)
        ?.let { runCatching { Provider.valueOf(it) }.getOrNull() }
    fun consented(context: Context) = prefs(context).getBoolean("privacy_v1", false)
    fun select(context: Context, provider: Provider, remember: Boolean) {
        check(consented(context)) { "请先阅读并同意隐私说明" }
        // Selection must be initialized successfully before persisting.
        initialize(context.applicationContext, provider)
        selected = provider
        prefs(context).edit().apply {
            putString("last_selected", provider.name)
            if (remember) putString("remembered", provider.name) else remove("remembered")
        }.apply()
    }
    fun initialize(context: Context, provider: Provider) {
        check(consented(context)) { "地图 SDK 尚未获得隐私同意" }
        if (provider in initialized) return
        val metadata = context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA).metaData
        val keyName = if (provider == Provider.BAIDU) "com.baidu.lbsapi.API_KEY" else "com.amap.api.v2.apikey"
        if (metadata?.getString(keyName).isNullOrBlank()) throw MapConfigurationException("${provider.title} Key 未配置，请在 Actions 输入对应 Android Key 后重新构建")
        when (provider) {
            Provider.BAIDU -> {
                SDKInitializer.setAgreePrivacy(context, true)
                LocationClient.setAgreePrivacy(true)
                SDKInitializer.addAuthResultListener(authListener)
                SDKInitializer.initialize(context)
                SDKInitializer.setCoordType(CoordType.GCJ02)
            }
            Provider.AMAP -> {
                MapsInitializer.updatePrivacyShow(context, true, true)
                MapsInitializer.updatePrivacyAgree(context, true)
                AMapLocationClient.updatePrivacyShow(context, true, true)
                AMapLocationClient.updatePrivacyAgree(context, true)
                ServiceSettings.updatePrivacyShow(context, true, true)
                ServiceSettings.updatePrivacyAgree(context, true)
            }
        }
        initialized.add(provider)
    }
    fun requireProvider(context: Context): Provider {
        val provider = selected ?: remembered(context) ?: prefs(context).getString("last_selected", null)?.let { runCatching { Provider.valueOf(it) }.getOrNull() } ?: error("请先选择地图服务")
        initialize(context.applicationContext, provider)
        selected = provider
        return provider
    }
}
