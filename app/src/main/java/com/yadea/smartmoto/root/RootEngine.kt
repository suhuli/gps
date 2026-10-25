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
    @Volatile var active = false
        private set
    @Volatile var holding = false
        private set
    private val main = Handler(Looper.getMainLooper())
    fun prefs(context: Context) = context.getSharedPreferences("root_engine_v1", Context.MODE_PRIVATE)
    @Synchronized fun available(context: Context): Boolean = runCatching { connect(context); true }.getOrDefault(false)
    @Synchronized private fun connect(context: Context) {
        manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (key != null && runCatching { command("handshake").getInt("protocol") == 1 }.getOrDefault(false)) return
        key = null
        val exchange = Bundle()
        check(manager!!.sendExtraCommand("portal", "exchange_key", exchange)) { "模块未激活或控制端未获授权" }
        key = exchange.getString("key") ?: kotlin.error("模块未返回会话")
        check(command("handshake").getInt("protocol") == 1) { "模块与应用协议不匹配，请重启后重试" }
        owner = Binder()
        command("attach_session") { putBinder("owner", owner) }
    }
    @Synchronized private fun command(name: String, fill: Bundle.() -> Unit = {}): Bundle {
        val payload = Bundle().apply { putString("command_id", name); fill() }
        check(manager?.sendExtraCommand("portal", key ?: kotlin.error("Root 引擎未连接"), payload) == true) { "引擎命令未确认：$name" }
        return payload
    }
    @Synchronized fun start(context: Context, onFailure: (String) -> Unit) {
        if (executor != null) return
        connect(context)
        frame = null; active = false; holding = false; error = null
        acknowledgedFrames = 0; lastAcknowledgedAt = 0
        val interval = RootFrame.interval(prefs(context).getLong("interval_ms", 1000L))
        executor = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "YadeaRootPublisher") }
        executor!!.scheduleWithFixedDelay({
            try {
                synchronized(this) {
                    val next = frame ?: return@scheduleWithFixedDelay
                    val sample = if (holding) next.stationary() else next
                    command("update_location") { putString("mode", "="); putDouble("lat", sample.latitude); putDouble("lon", sample.longitude) }
                    command("set_speed") { putDouble("speed", sample.speed) }
                    command("set_bearing") { putDouble("bearing", ((sample.bearing % 360) + 360) % 360) }
                    if (!active) {
                        command("start") { putDouble("speed", sample.speed); putDouble("altitude", sample.altitude); putFloat("accuracy", sample.accuracy) }
                        active = true
                    }
                    command("heartbeat")
                    check(command("is_start").getBoolean("is_start")) { "系统引擎已停止" }
                    command("broadcast_location")
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
        val age = if (lastAcknowledgedAt == 0L) "尚无确认" else "${SystemClock.elapsedRealtime() - lastAcknowledgedAt} ms 前"
        return "引擎：Portal 衍生系统 Hook\n连接：${if (connected) "已连接（协议 1）" else "未连接，请启用模块并重启"}\n状态：${if (active) { if (holding) "暂停保持" else "运行中" } else "未运行"}\n累计分发命令确认：$acknowledgedFrames\n最近确认：$age\n${error ?: ""}\n\n命令确认不等于其他 App 已接收或采用位置。Wi-Fi、基站、原生传感器与完整 Portal UI 迁移尚未验收。"
    }
}
