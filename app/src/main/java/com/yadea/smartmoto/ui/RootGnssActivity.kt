package com.yadea.smartmoto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.ArrayDeque

/** Lifecycle-bound observation. No background collection and no NMEA data written to disk. */
class RootGnssActivity : AppCompatActivity() {
    private lateinit var manager: LocationManager
    private lateinit var status: TextView
    private lateinit var satellites: TextView
    private lateinit var sentences: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val recent = ArrayDeque<String>()
    private var observing = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startObservation()
        else status.text = "需要精确定位权限；未开始观察"
    }
    private val gnss = object : GnssStatus.Callback() {
        override fun onStarted() { status.text = "GNSS 已开始；等待卫星回调" }
        override fun onStopped() { status.text = "GNSS 已停止" }
        override fun onFirstFix(ttffMillis: Int) { status.text = "首次定位耗时 ${ttffMillis} ms" }
        override fun onSatelliteStatusChanged(value: GnssStatus) {
            if (!observing) return
            satellites.text = buildString {
                append("可见 ${value.satelliteCount} 颗 · 参与定位 ${(0 until value.satelliteCount).count { value.usedInFix(it) }} 颗\n")
                append("星座 / SVID / C/N₀ / 仰角 / 方位 / 使用\n")
                for (index in 0 until value.satelliteCount.coerceAtMost(128)) {
                    val constellation = when(value.getConstellationType(index)) { 1->"GPS"; 2->"SBAS"; 3->"GLONASS"; 4->"QZSS"; 5->"北斗"; 6->"Galileo"; 7->"IRNSS"; else->"未知" }
                    append("%s %d · %.1f dBHz · %.0f° · %.0f° · %s\n".format(constellation,value.getSvid(index),value.getCn0DbHz(index),value.getElevationDegrees(index),value.getAzimuthDegrees(index),if(value.usedInFix(index)) "是" else "否"))
                }
            }
        }
    }
    private val nmea = OnNmeaMessageListener { sentence, _ ->
        if (observing) { recent.addLast(sentence.take(256).trim()); while(recent.size>20) recent.removeFirst(); sentences.text = recent.joinToString("\n") }
    }
    private val location = object : LocationListener {
        override fun onLocationChanged(value: Location) { status.text = "接收 ${value.provider} · 精度 ${value.accuracy} m · 海拔 ${value.altitude} m" }
        @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, state: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) { status.text = "$provider 已启用" }
        override fun onProviderDisabled(provider: String) { status.text = "$provider 未启用；请在系统设置中打开定位" }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val body = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24); setBackgroundColor(0xFFF5F8FC.toInt()) }
        body.addView(TextView(this).apply { text="GNSS / NMEA 观察"; textSize=25f })
        body.addView(TextView(this).apply { text="仅观察本机公开回调，不代表其他应用已经采用模拟位置。此页打开后可请求 GPS 更新；离开页面即注销。NMEA 只暂存在内存。" })
        status=TextView(this).apply { text="尚未开始观察" }; satellites=TextView(this); sentences=TextView(this).apply { typeface=android.graphics.Typeface.MONOSPACE; textSize=11f }
        body.addView(status)
        body.addView(Button(this).apply { text="开始观察"; setOnClickListener {
            if(ContextCompat.checkSelfPermission(this@RootGnssActivity,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startObservation()
            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        } })
        body.addView(Button(this).apply { text="停止并清空"; setOnClickListener { stopObservation(); recent.clear(); satellites.text=""; sentences.text=""; status.text="已停止观察" } })
        body.addView(Button(this).apply { text="启动卫星模拟（定位任务内 / 独立任务）"; setOnClickListener {
            if (com.yadea.smartmoto.server.GpsService.isRunning) {
                runCatching { com.yadea.smartmoto.root.RootEngine.setGnssEnabled(true) }.onSuccess { status.text="已请求在当前定位任务内启用卫星模拟" }.onFailure { status.text=it.message }
            } else if(ContextCompat.checkSelfPermission(this@RootGnssActivity,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
                status.text="请先点击开始观察并授予精确定位权限"
            } else runCatching { ContextCompat.startForegroundService(this@RootGnssActivity,android.content.Intent(this@RootGnssActivity,com.yadea.smartmoto.server.RootAuxService::class.java)) }.onSuccess { status.text="已请求独立卫星任务；无卫星回调时不会凭空制造 GPS 坐标" }.onFailure { status.text="无法启动前台任务" }
        } })
        body.addView(Button(this).apply { text="停止卫星模拟（不停止普通定位任务）"; setOnClickListener {
            if(com.yadea.smartmoto.server.RootAuxService.isRunning) stopService(android.content.Intent(this@RootGnssActivity,com.yadea.smartmoto.server.RootAuxService::class.java))
            else runCatching { com.yadea.smartmoto.root.RootEngine.setGnssEnabled(false) }
            status.text="已请求停止卫星模拟；系统真实卫星回调仍可继续观察"
        } })
        body.addView(satellites); body.addView(sentences); installRootContent(body)
    }
    private fun startObservation() {
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { status.text="精确定位权限已撤销"; return }
        if(observing || isFinishing || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        try {
            observing=true
            val gnssRegistered=manager.registerGnssStatusCallback(gnss,handler)
            val nmeaRegistered=manager.addNmeaListener(nmea,handler)
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,location,Looper.getMainLooper())
            status.text="观察已开始：GNSS=$gnssRegistered，NMEA=$nmeaRegistered；无回调也可能是硬件或系统限制"
        } catch(e: Exception) { stopObservation(); status.text="观察失败：${e.javaClass.simpleName}" }
    }
    @android.annotation.SuppressLint("MissingPermission") // Cleanup must also be attempted after permission revocation.
    private fun stopObservation() {
        observing=false
        runCatching { manager.unregisterGnssStatusCallback(gnss) }; runCatching { manager.removeNmeaListener(nmea) }; runCatching { manager.removeUpdates(location) }
        handler.removeCallbacksAndMessages(null)
    }
    override fun onStop() { stopObservation(); super.onStop() }
}
