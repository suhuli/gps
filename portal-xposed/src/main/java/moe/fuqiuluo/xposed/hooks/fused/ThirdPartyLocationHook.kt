package moe.fuqiuluo.xposed.hooks.fused

import android.location.LocationListener
import android.os.Bundle
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseLocationHook
import moe.fuqiuluo.xposed.hooks.blindhook.BlindHookLocation
import moe.fuqiuluo.xposed.hooks.blindhook.BlindHookLocation.invoke
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.hookMethodAfter
import moe.fuqiuluo.xposed.utils.hookMethodBefore
import moe.fuqiuluo.xposed.utils.onceHookMethodBefore
import moe.fuqiuluo.xposed.utils.toClass
import java.lang.reflect.Modifier

object ThirdPartyLocationHook: BaseLocationHook() {
    operator fun invoke(classLoader: ClassLoader) {
        if(!initDivineService("ThirdPartyLocationHook")) {
            Logger.error("Failed to init DivineService in ThirdPartyLocationHook")
            return
        }

        val candidates=listOf("com.amap.android.location.NetworkLocationManager","com.baidu.location.LocationClient","com.tencent.geolocation.nlp.TencentNLPManager")
        val available=candidates.filter { XposedHelpers.findClassIfExists(it,classLoader)!=null }
        moe.fuqiuluo.xposed.HookCapabilities.record("vendor_fused",if(available.isEmpty()) "未匹配厂商 SDK 类，透传" else "匹配类："+available.joinToString { it.substringAfterLast('.') })
        // There is a certain probability that the positioning SDK will exchange data directly with these services
        // instead of ... through the system
        hookAMapNetLocManager(classLoader)
        hookBDMap(classLoader)
        hookTencent(classLoader)
    }

    private fun hookAMapNetLocManager(classLoader: ClassLoader) {
        val cNetworkLocationManager = "com.amap.android.location.NetworkLocationManager".toClass(classLoader)
        if (cNetworkLocationManager == null) {
            Logger.warn("Failed to find NetworkLocationManager (amap service)")
            return
        }

        cNetworkLocationManager.onceHookMethodBefore("onSendExtraCommand") {
            if (FakeLoc.enableDebugLog) {
                val cmd = args[0] as String
                val extras = args[1] as Bundle

                Logger.debug("NetworkLocationManager.onSendExtraCommand: $cmd, $extras")
            }
            if (FakeLoc.enable && FakeLoc.vendorFused) {
                this.result = false
            }
        }

        val boolFields = cNetworkLocationManager.declaredFields
            .filter { it.type == Boolean::class.java && !Modifier.isStatic(it.modifiers) }
        boolFields.forEach {
            it.isAccessible = true
        }
        val listenerField = cNetworkLocationManager.declaredFields
            .filter { it.type == LocationListener::class.java && !Modifier.isStatic(it.modifiers) }
        listenerField.forEach {
            it.isAccessible = true
        }
        BlindHookLocation(cNetworkLocationManager, classLoader)
    }

    private fun hookBDMap(classLoader: ClassLoader) {
        run {
            val bdLocationClient = "com.baidu.location.LocationClient".toClass(classLoader)
            if (bdLocationClient == null) {
                Logger.warn("Failed to find LocationClient (baidu service)")
                return@run
            }

            bdLocationClient.hookMethodAfter("requestNLPNormal") {
                if (FakeLoc.enable && FakeLoc.vendorFused) {
                    val bdLocation = result ?: return@hookMethodAfter

                    val jitterLat = FakeLoc.jitterLocation()
                    XposedHelpers.callMethod(bdLocation, "setLatitude", jitterLat.first)
                    XposedHelpers.callMethod(bdLocation, "setLongitude", jitterLat.second)
                    XposedHelpers.callMethod(bdLocation, "setBuildingID", "")
                    XposedHelpers.callMethod(bdLocation, "setAddrStr", "")
                }
            }

            BlindHookLocation(bdLocationClient, classLoader)
        }


    }

    private fun hookTencent(classLoader: ClassLoader) {
        run {
            val cTencentNLPManager = "com.tencent.geolocation.nlp.TencentNLPManager".toClass(classLoader)
            if (cTencentNLPManager == null) {
                Logger.warn("Failed to find TencentNLPManager (tencent service)")
                return@run
            }

            BlindHookLocation(cTencentNLPManager, classLoader)
        }

//        run {
//            val cTencentLocationManager = "com.tencent.map.geolocation.TencentLocationManager".toClass(classLoader)
//                ?: return@run
//
//
//        }
    }
}