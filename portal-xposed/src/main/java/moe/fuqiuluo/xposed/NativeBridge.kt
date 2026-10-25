package moe.fuqiuluo.xposed

import android.os.Process
import moe.fuqiuluo.dobby.Dobby

/** Loads only the bundled module APK entry. No remote path, shell, chmod or SELinux modification. */
object NativeBridge {
    @Volatile var modulePath: String? = null
    @Volatile private var loaded = false
    @Volatile private var requested = false
    @Volatile private var failure: String? = null
    @Synchronized fun enable(value: Boolean): Boolean {
        if (!value) { requested=false; if(loaded) runCatching { Dobby.setStatus(false) }; return true }
        return runCatching {
            if(!loaded) {
                val path=modulePath ?: error("模块路径尚未初始化")
                require(path.startsWith("/data/app/") && path.endsWith(".apk")) { "模块安装路径不受支持" }
                val abi=if(Process.is64Bit()) "arm64-v8a" else "armeabi-v7a"
                System.load("$path!/lib/$abi/libportal.so")
                loaded=true
            }
            check(Dobby.install()) { "当前系统未找到匹配的传感器写入符号" }
            requested=true; Dobby.setStatus(moe.fuqiuluo.xposed.utils.FakeLoc.enable); failure=null
            true
        }.getOrElse { failure=it.javaClass.simpleName+": "+(it.message ?: "加载失败").take(160); false }
    }
    fun heartbeat() { if(requested && loaded) Dobby.heartbeat() }
    fun onSimulationStarted() { if(requested && loaded) Dobby.setStatus(true) }
    fun report(): String = if(loaded) runCatching { "原生传感器：状态位 ${Dobby.status()}，已处理事件 ${Dobby.rewritten()}${failure?.let { "; $it" } ?: ""}" }.getOrDefault("原生状态不可用") else "原生传感器：${failure ?: "未启用"}"
}
