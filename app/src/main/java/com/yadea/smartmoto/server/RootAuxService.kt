package com.yadea.smartmoto.server

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yadea.smartmoto.R
import com.yadea.smartmoto.maps.MapPreferences
import com.yadea.smartmoto.root.RootEngine
import com.yadea.smartmoto.ui.RootGnssActivity

/** Independent, leased GNSS status task. Never publishes a fabricated GPS coordinate. */
class RootAuxService : Service() {
    companion object {
        const val STOP="com.yadea.smartmoto.STOP_GNSS"
        @Volatile var isRunning=false
            private set
    }
    private var ownsSession=false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); isRunning=true }
    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        if(intent == null || intent.action==STOP) { stopSelf(); return START_NOT_STICKY }
        if(GpsService.isRunning || !MapPreferences.consented(this) || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            android.widget.Toast.makeText(this,"请停止定位任务并确认精确定位与隐私授权",android.widget.Toast.LENGTH_LONG).show(); stopSelf(); return START_NOT_STICKY
        }
        try {
            val manager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel("root_gnss","卫星状态模拟",NotificationManager.IMPORTANCE_LOW))
            val open=PendingIntent.getActivity(this,259,Intent(this,RootGnssActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop=PendingIntent.getService(this,259,Intent(this,RootAuxService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startForeground(259,NotificationCompat.Builder(this,"root_gnss").setSmallIcon(R.drawable.ic_location).setContentTitle("雅迪智行 · 独立卫星模拟").setContentText("仅修改卫星状态，不改写 GPS 坐标").setContentIntent(open).setOngoing(true).addAction(0,"停止",stop).build())
            RootEngine.start(this,true) { stopSelf() }; ownsSession=true
        } catch(e: Exception) {
            android.widget.Toast.makeText(this,"卫星任务启动失败，请检查模块诊断",android.widget.Toast.LENGTH_LONG).show(); stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { isRunning=false; if(ownsSession) RootEngine.stop(); stopForeground(true); super.onDestroy() }
}
