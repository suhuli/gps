package moe.fuqiuluo.xposed.hooks.telephony

import android.os.Bundle
import android.telephony.CellIdentity
import android.telephony.CellIdentityCdma
import android.telephony.CellInfoCdma
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.HookCapabilities
import moe.fuqiuluo.xposed.utils.*
import java.util.concurrent.ConcurrentHashMap

/** Optional CDMA-format responses. No radio commands, subscription edits or permission bypass. */
object TelephonyHook : BaseTelephonyHook() {
    private val registries=ConcurrentHashMap.newKeySet<Class<*>>()
    private fun enabled() = FakeLoc.enable && FakeLoc.needDowngradeToCdma
    private fun identity(): CellIdentityCdma? = runCatching {
        val (lat,lon)=FakeLoc.coordinatesSnapshot()
        CellIdentityCdma::class.java.getConstructor(Int::class.java,Int::class.java,Int::class.java,Int::class.java,Int::class.java,String::class.java,String::class.java)
            .newInstance(Int.MAX_VALUE,Int.MAX_VALUE,Int.MAX_VALUE,(lon*14400).toInt(),(lat*14400).toInt(),null,null)
    }.getOrNull()
    private fun locationBundle() = Bundle().apply {
        val (lat,lon)=FakeLoc.coordinatesSnapshot()
        putInt("baseStationLatitude",(lat*14400).toInt()); putInt("baseStationLongitude",(lon*14400).toInt())
        putInt("baseStationId",Int.MAX_VALUE); putInt("systemId",Int.MAX_VALUE); putInt("networkId",Int.MAX_VALUE)
    }
    private fun cellInfo(): CellInfoCdma? = runCatching {
        val id=identity() ?: return null
        CellInfoCdma::class.java.getDeclaredConstructor().apply { isAccessible=true }.newInstance().also {
            XposedHelpers.callMethod(it,"setRegistered",true)
            XposedHelpers.callMethod(it,"setTimeStamp",android.os.SystemClock.elapsedRealtimeNanos())
            XposedHelpers.setObjectField(it,"mCellIdentityCdma",id)
        }
    }.getOrNull()
    operator fun invoke(classLoader: ClassLoader) {
        initDivineService("phone")
        val type=XposedHelpers.findClassIfExists("com.android.phone.PhoneInterfaceManager",classLoader) ?: run { HookCapabilities.record("phone","接口类未匹配，透传"); return }
        for(name in listOf("getActivePhoneType","getActivePhoneTypeForSlot")) type.hookAllMethods(name,afterHook {
            if(!hasThrowable() && enabled() && !BinderUtils.isSystemAppsCall() && result is Int) result=2
        })
        for(name in listOf("getDataNetworkType","getNetworkType","getDataNetworkTypeForSubscriber","getNetworkTypeForSubscriber")) type.hookAllMethods(name,afterHook {
            if(!hasThrowable() && enabled() && !BinderUtils.isSystemAppsCall() && result is Int) result=4
        })
        type.hookAllMethods("getAllCellInfo",afterHook {
            if(hasThrowable() || !enabled() || BinderUtils.isSystemAppsCall() || result !is List<*> || (result as List<*>).isEmpty()) return@afterHook
            cellInfo()?.let { result=listOf(it) }
        })
        type.hookAllMethods("getCellLocation",afterHook {
            if(hasThrowable() || !enabled() || BinderUtils.isSystemAppsCall() || result == null) return@afterHook
            when(result) { is Bundle -> result=locationBundle(); is CellIdentity -> identity()?.let { result=it } }
        })
        type.hookAllMethods("getNeighboringCellInfo",afterHook {
            if(!hasThrowable() && enabled() && !BinderUtils.isSystemAppsCall() && result is List<*>) result=emptyList<Any>()
        })
        HookCapabilities.record("phone","电话接口已安装；默认关闭，权限检查保留")
    }
    fun hookTelephonyRegistry(type: Class<*>) {
        if(!registries.add(type)) return
        type.declaredMethods.filter { it.name in setOf("listen","listenWithEventList") }.forEach { method ->
            val index=method.parameterTypes.indexOfFirst { it.simpleName=="IPhoneStateListener" }
            if(index<0) return@forEach
            XposedBridge.hookMethod(method,beforeHook {
                val callback=args.getOrNull(index) ?: return@beforeHook
                // Do not cancel registration. Only real platform-authorized callbacks are rewritten.
                callback.javaClass.onceHookMethodBefore("onCellLocationChanged",CellIdentity::class.java) {
                    if(enabled()) identity()?.let { args[0]=it }
                }
                callback.javaClass.onceHookMethodBefore("onCellLocationChanged",Bundle::class.java) {
                    if(enabled()) args[0]=locationBundle()
                }
                callback.javaClass.onceHookMethodBefore("onCellInfoChanged",List::class.java) {
                    if(enabled() && (args.firstOrNull() as? List<*>)?.isNotEmpty()==true) cellInfo()?.let { args[0]=listOf(it) }
                }
            })
        }
        HookCapabilities.record("telephony_registry","按接收者改写回调，系统注册/注销保持原样")
    }
}
