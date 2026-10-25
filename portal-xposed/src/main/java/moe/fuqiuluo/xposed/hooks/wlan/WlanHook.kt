package moe.fuqiuluo.xposed.hooks.wlan

import android.net.wifi.WifiInfo
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.utils.*
import java.util.concurrent.ConcurrentHashMap

/** Optional response redaction only; never changes radios, saved networks, or system permissions. */
object WlanHook {
    private val installed = ConcurrentHashMap.newKeySet<Class<*>>()
    operator fun invoke(classLoader: ClassLoader) {
        moe.fuqiuluo.xposed.HookCapabilities.record("wifi","等待 Wi-Fi 服务注册/匹配")
        runCatching { XposedHelpers.findClassIfExists("com.android.server.wifi.WifiServiceImpl",classLoader)?.let { install(it) } }
        val serviceManager = XposedHelpers.findClassIfExists("android.os.ServiceManager",classLoader) ?: return
        serviceManager.hookAllMethods("addService", beforeHook {
            if (args.firstOrNull() == "wifi") args.getOrNull(1)?.javaClass?.let { install(it) }
        })
        runCatching { XposedHelpers.callStaticMethod(serviceManager,"getService","wifi")?.javaClass?.let { install(it) } }
    }
    private fun install(type: Class<*>) {
        if (!generateSequence(type) { it.superclass }.any { it.name == "com.android.server.wifi.WifiServiceImpl" }) return
        if (!installed.add(type)) return
        type.hookAllMethods("getConnectionInfo", afterHook {
            if (hasThrowable() || result !is WifiInfo || !FakeLoc.enable || !FakeLoc.hookWifi || !FakeLoc.enableMockWifi || BinderUtils.isSystemAppsCall()) return@afterHook
            // Run only AFTER the platform has enforced the caller's access and package identity.
            runCatching {
                val info=WifiInfo::class.java.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
                XposedHelpers.callMethod(info,"setMacAddress","02:00:00:00:00:00")
                XposedHelpers.callMethod(info,"setBSSID","02:00:00:00:00:00")
                info
            }.getOrNull()?.let { result=it }
        })
        type.hookAllMethods("getScanResults", afterHook {
            if(hasThrowable() || result == null || !FakeLoc.enable || !FakeLoc.hookWifi || !FakeLoc.enableMockWifi || BinderUtils.isSystemAppsCall()) return@afterHook
            when(val original=result) {
                is List<*> -> result=emptyList<Any>()
                is Array<*> -> result=java.lang.reflect.Array.newInstance(original.javaClass.componentType!!,0)
                else -> if(original.javaClass.name.contains("ParceledListSlice")) runCatching {
                    original.javaClass.getConstructor(List::class.java).newInstance(emptyList<Any>())
                }.getOrNull()?.let { result=it }
            }
        })
        moe.fuqiuluo.xposed.HookCapabilities.record("wifi","已安装响应过滤；默认关闭")
    }
}
