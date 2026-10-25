package com.yadea.smartmoto.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.model.LatLng
import com.yadea.smartmoto.model.MockMessageModel
import com.yadea.smartmoto.model.PoiInfoModel
import com.yadea.smartmoto.root.RootMotion
import com.yadea.smartmoto.server.GpsService
import kotlin.math.*

class RootMotionActivity : AppCompatActivity() {
    private lateinit var latitude: EditText
    private lateinit var longitude: EditText
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) it.data?.getParcelableExtra<PoiInfoModel>("poiInfo")?.latLng?.let { point -> latitude.setText(point.latitude.toString()); longitude.setText(point.longitude.toString()) }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24); setBackgroundColor(0xFFF5F8FC.toInt()) }
        body.addView(TextView(this).apply { text = "方向摇杆"; textSize = 25f })
        body.addView(TextView(this).apply { text = "上北下南，拖动幅度控制速度。先选择 GCJ-02 起点并启动固定点任务。勾选自动前进后，松手和离开页面仍会继续，通知栏可暂停或停止。" })
        latitude = EditText(this).apply { hint = "起点纬度（GCJ-02）"; setSingleLine() }; longitude = EditText(this).apply { hint = "起点经度（GCJ-02）"; setSingleLine() }
        body.addView(latitude); body.addView(longitude)
        body.addView(Button(this).apply { text = "地图选择起点"; setOnClickListener { picker.launch(Intent(this@RootMotionActivity,PickMapPoiActivity::class.java).putExtra("model",PoiInfoModel())) } })
        val speed = EditText(this).apply { hint = "最大速度 km/h（1–300）"; setText("5"); inputType = android.text.InputType.TYPE_CLASS_NUMBER }; body.addView(speed)
        body.addView(Button(this).apply { text = "从此点启动摇杆任务"; setOnClickListener {
            try {
                val lat = latitude.text.toString().toDouble(); val lon = longitude.text.toString().toDouble()
                require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
                require(speed.text.toString().toInt() in 1..300)
                RootMotion.stop()
                GpsService.start(this@RootMotionActivity,MockMessageModel(locationModel = PoiInfoModel(latLng = LatLng(lat,lon)),speed = speed.text.toString().toInt()))
            } catch (e: Exception) { Toast.makeText(this@RootMotionActivity,"请填写合法坐标和速度",Toast.LENGTH_LONG).show() }
        } })
        val automatic = CheckBox(this).apply { text = "自动前进 / 松手锁定方向"; isChecked = RootMotion.command.automatic
            setOnCheckedChangeListener { _, checked -> if (!checked) RootMotion.stop() }
        }; body.addView(automatic)
        val status = TextView(this); body.addView(status)
        val rocker = Rocker(this) { angle, strength, pressed ->
            if (!pressed) { if (!automatic.isChecked) RootMotion.stop() }
            else if (GpsService.acceptsMotion) {
                val maximum = speed.text.toString().toDoubleOrNull()
                if (maximum != null && maximum in 1.0..300.0) RootMotion.set(angle, maximum*strength, automatic.isChecked)
            }
            status.text = if (!GpsService.acceptsMotion) "先启动固定点任务；路线任务不能使用摇杆" else "方向 %.0f° · %.1f km/h".format(RootMotion.command.bearing,RootMotion.command.speedKmh)
        }
        body.addView(rocker,LinearLayout.LayoutParams(-1,(260*resources.displayMetrics.density).toInt()))
        body.addView(Button(this).apply { text = "停止移动并保持位置"; setOnClickListener { RootMotion.stop(); automatic.isChecked = false; status.text = "已停止移动，保留当前模拟位置" } })
        body.addView(Button(this).apply { text = "停止模拟并恢复系统定位"; setOnClickListener { RootMotion.stop(); stopService(Intent(this@RootMotionActivity,GpsService::class.java)); stopService(Intent(this@RootMotionActivity,com.yadea.smartmoto.server.RootAuxService::class.java)); com.yadea.smartmoto.root.RootEngine.stop(); status.text = "已停止任务" } })
        installRootContent(body)
    }
    override fun onPause() { if (!RootMotion.command.automatic) RootMotion.stop(); super.onPause() }
}
private class Rocker(context: Context, val changed: (Double,Double,Boolean)->Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dx = 0f; private var dy = 0f
    override fun onDraw(canvas: Canvas) {
        val radius = min(width,height)*0.4f
        paint.color = 0xFFE1EAF8.toInt(); canvas.drawCircle(width/2f,height/2f,radius,paint)
        paint.color = 0xFF3478F6.toInt(); canvas.drawCircle(width/2f+dx,height/2f+dy,radius*0.22f,paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val radius = min(width,height)*0.4f
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                dx = event.x-width/2f; dy = event.y-height/2f
                val length = hypot(dx,dy); if(length>radius) { dx*=radius/length; dy*=radius/length }
                val strength = (hypot(dx,dy)/radius).toDouble().coerceIn(0.0,1.0)
                changed((Math.toDegrees(atan2(dx.toDouble(),-dy.toDouble()))+360)%360,if(strength<0.08) 0.0 else strength,true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if(event.actionMasked == MotionEvent.ACTION_CANCEL) RootMotion.stop(); dx=0f; dy=0f; changed(0.0,0.0,false); parent.requestDisallowInterceptTouchEvent(false); if(event.actionMasked == MotionEvent.ACTION_UP) performClick() }
        }; invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
