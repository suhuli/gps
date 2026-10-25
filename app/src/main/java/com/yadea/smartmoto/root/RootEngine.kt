package com.yadea.smartmoto.root

import android.content.Context
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Portal protocol adapter. No test-provider fallback and no shell/code-loading interface. */
object RootEngine {
    private var manager: LocationManager? = null
    private var key: String? = null
    private var owner: Binder? = null
    private var executor: ScheduledExecutorService? = null
    @Volatile private var frame: RootFrame? = null
    @Volatile var lastAcknowledgedAt = 0L
        private set
    @Volatile var acknowledgedFrames = 0L
        private set
    @Volatile var error: String? = null
        private set
    @Volatile private var gnssOnly = false
    @Volatile var active = false
        private set
    @Volatile var holding = false
        private set
    private val main = Handler(Looper.getMainLooper())
    fun prefs(context: Context) = context.getSharedPreferences("root_engine_v1", Context.MODE_PRIVATE)
    @Synchronized fun available(context: Context): Boolean = runCatching { connect(context); true }.getOrDefault(false)
    @Synchronized private fun connect(context: Context) {
        manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (key != null && runCatching { command("handshake").getInt("protocol") == 2 }.getOrDefault(false)) return
        key = null
        val exchange = Bundle()
        check(manager!!.sendExtraCommand("portal", "exchange_key", exchange)) { "模块未激活或控制端未获授权" }
        key = exchange.getString("key") ?: kotlin.error("模块未返回会话")
        check(command("handshake").getInt("protocol") == 2) { "模块与应用协议不匹配，请重启后重试" }
        owner = Binder()
        command("attach_session") { putBinder("owner", owner) }
    }
    @Synchronized private fun command(name: String, fill: Bundle.() -> Unit = {}): Bundle {
        val payload = Bundle().apply { putString("command_id", name); fill() }
        check(manager?.sendExtraCommand("portal", key ?: kotlin.error("Root 引擎未连接"), payload) == true) { "引擎命令未确认：$name ${payload.getString("error", "")}" }
        return payload
    }
    @Synchronized fun start(context: Context, satellitesOnly: Boolean = false, onFailure: (String) -> Unit) {
        if (executor != null) { check(gnssOnly == satellitesOnly) { "请先停止当前类型的任务" }; return }
        gnssOnly=satellitesOnly
        connect(context)
        frame = null; active = false; holding = false; error = null
        acknowledgedFrames = 0; lastAcknowledgedAt = 0
        val settings = prefs(context)
        val interval = RootFrame.interval(settings.getLong("interval_ms", 1000L))
        val publishCallbacks = settings.getBoolean("publish_callbacks", true)
        command("configure_session") {
            RootSettings.flags.filter { it.key != "publish_callbacks" }.forEach {
                putBoolean(it.key, if(satellitesOnly && it.key !in setOf("mock_gnss","enable_debug_log","enable_agps")) false else settings.getBoolean(it.key,it.default))
            }
            if(satellitesOnly) putBoolean("mock_gnss",true)
            putInt("min_satellites", settings.getInt("min_satellites", 12))
            putDouble("speed_amplitude", if(satellitesOnly) 0.0 else settings.getString("speed_amplitude_kmh", "0")!!.toDouble()/3.6)
        }
        executor = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "YadeaRootPublisher") }
        executor!!.scheduleWithFixedDelay({
            try {
                synchronized(this) {
                    if(satellitesOnly) {
                        if(!active) { command("start_gnss_mock"); active=true }
                        command("heartbeat")
                        check(command("is_gnss_start").getBoolean("is_gnss_start")) { "卫星任务已停止" }
                        acknowledgedFrames++; lastAcknowledgedAt=SystemClock.elapsedRealtime()
                        return@scheduleWithFixedDelay
                    }
                    val next = frame ?: return@scheduleWithFixedDelay
                    val sample = if (holding) next.stationary() else next
                    command("update_location") { putString("mode", "="); putDouble("lat", sample.latitude); putDouble("lon", sample.longitude) }
                    command("set_speed") { putDouble("speed", sample.speed) }
                    command("set_bearing") { putDouble("bearing", ((sample.bearing % 360) + 360) % 360) }
                    val firstFrame = !active
                    if (firstFrame) {
                        command("start") { putDouble("speed", sample.speed); putDouble("altitude", sample.altitude); putFloat("accuracy", sample.accuracy) }
                        active = true
                    }
                    command("heartbeat")
                    check(command("is_start").getBoolean("is_start")) { "系统引擎已停止" }
                    if (publishCallbacks || firstFrame) command("broadcast_location")
                    acknowledgedFrames++; lastAcknowledgedAt = SystemClock.elapsedRealtime()
                }
            } catch (failure: Exception) {
                val message = "Root 定位引擎异常，已停止；请查看模块诊断"
                synchronized(this) {
                    error = message; active = false; frame = null
                    runCatching { command("stop") }
                    executor?.shutdown(); executor = null; key = null
                }
                main.post { onFailure(message) }
            }
        }, 0, interval, TimeUnit.MILLISECONDS)
    }
    @Synchronized fun setGnssEnabled(value: Boolean) {
        check(active) { "请先启动定位任务，或使用独立卫星任务" }
        command(if(value) "start_gnss_mock" else "stop_gnss_mock")
    }
    fun offer(next: RootFrame) { frame = next }
    fun hold() { holding = true }
    fun resume() { holding = false }
    @Synchronized fun stop() {
        executor?.shutdownNow(); executor = null; frame = null
        if (key != null) runCatching { command("stop") }
        active = false; holding = false
    }
    fun diagnostics(context: Context): String {
        val connected = available(context)
        val capabilities = if (connected) runCatching { command("capabilities").getString("report", "") }.getOrDefault("") else ""
        val selinux=runCatching { when(java.io.File("/sys/fs/selinux/enforce").readText().trim()) { "1"->"Enforcing"; "0"->"Permissive"; else->"未知" } }.getOrDefault("不可读取")
        val age = if (lastAcknowledgedAt == 0L) "尚无确认" else "${SystemClock.elapsedRealtime() - lastAcknowledgedAt} ms 前"
        return "引擎：Portal 衍生系统 Hook\n连接：${if (connected) "已连接（协议 2）" else "未连接，请启用模块并重启"}\n状态：${if (active) { if(gnssOnly) "独立卫星任务（不改写 GPS 坐标）" else if (holding) "暂停保持" else "运行中" } else "未运行"}\n累计分发命令确认：$acknowledgedFrames\n最近确认：$age\n${error ?: ""}\n$capabilities\nSELinux：$selinux（本应用不改变系统模式）\n\n命令确认不等于其他 App 已接收或采用位置。Wi-Fi、基站、原生传感器与完整 Portal UI 迁移尚未验收。"
    }
}
