package moe.fuqiuluo.xposed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.location.LocationManager
import android.os.*
import moe.fuqiuluo.xposed.utils.BinderUtils
import moe.fuqiuluo.xposed.utils.FakeLoc
import java.util.concurrent.atomic.AtomicBoolean

/** Read-only, allowlisted system peers. They never obtain the controller key or command authority. */
object PeerBridge {
    val packages = PeerPolicy.packages
    @Volatile var processPackage = ""
    private val started = AtomicBoolean(false)
    @Volatile private var receivedAt = 0L
    private val expiry = Handler(Looper.getMainLooper())
    private val peerReports = java.util.concurrent.ConcurrentHashMap<Int,String>()
    private val knownPeers = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    fun serverSnapshot(output: Bundle): Boolean {
        if (!FakeLoc.isSystemServerProcess) return false
        val uid = Binder.getCallingUid()
        val context = BinderUtils.getSystemContext() ?: return false
        val allowed = runCatching {
            context.packageManager.getPackagesForUid(uid).orEmpty().any { name ->
                PeerPolicy.allowed(name, context.packageManager.getApplicationInfo(name,0).flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            }
        }.getOrDefault(false)
        if (!allowed) return false
        knownPeers[uid] = SystemClock.uptimeMillis()
        peerReports[uid]=output.getString("peer_report", "尚无能力报告").take(512)
        val coordinates = FakeLoc.coordinatesSnapshot()
        output.putInt("schema", 1)
        output.putBoolean("enable", FakeLoc.enable && RemoteCommandHandler.liveSession())
        output.putDouble("latitude",coordinates.first); output.putDouble("longitude",coordinates.second)
        output.putDouble("altitude",FakeLoc.altitude); output.putDouble("speed",FakeLoc.speed)
        output.putDouble("bearing",((FakeLoc.bearing%360)+360)%360); output.putFloat("accuracy",FakeLoc.accuracy)
        output.putDouble("speed_amplitude",FakeLoc.speedAmplitude)
        output.putBoolean("telephony",FakeLoc.needDowngradeToCdma)
        output.putBoolean("vendor_fused",FakeLoc.vendorFused)
        output.putBoolean("debug",FakeLoc.enableDebugLog)
        return true
    }
    fun reports() = peerReports.entries.filter { SystemClock.uptimeMillis()-(knownPeers[it.key] ?: 0L)<5000L }.joinToString("\n") { "系统 UID ${it.key}: ${it.value}" }
    fun activePeers() = knownPeers.values.count { SystemClock.uptimeMillis()-it < 5000L }
    fun start(): Boolean {
        if (FakeLoc.isSystemServerProcess || !started.compareAndSet(false,true)) return true
        val worker = Handler(HandlerThread("YadeaPeerSnapshot").apply { start() }.looper)
        val poll = object : Runnable {
            override fun run() {
                runCatching {
                    val context = BinderUtils.getSystemContext() ?: return@runCatching
                    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@runCatching
                    val snapshot = Bundle().apply { putString("peer_report",HookCapabilities.report()) }
                    if (!manager.sendExtraCommand("portal","peer_snapshot",snapshot) || snapshot.getInt("schema") != 1) return@runCatching
                    val lat=snapshot.getDouble("latitude"); val lon=snapshot.getDouble("longitude")
                    val speed=snapshot.getDouble("speed"); val altitude=snapshot.getDouble("altitude"); val accuracy=snapshot.getFloat("accuracy")
                    val bearing=snapshot.getDouble("bearing"); val amplitude=snapshot.getDouble("speed_amplitude")
                    if(!PeerPolicy.valid(lat,lon,speed,altitude,accuracy,bearing) || !amplitude.isFinite() || amplitude !in 0.0..100.0) return@runCatching
                    FakeLoc.setCoordinates(lat,lon); FakeLoc.speed=speed; FakeLoc.altitude=altitude; FakeLoc.accuracy=accuracy
                    FakeLoc.bearing=bearing; FakeLoc.hasBearings=true; FakeLoc.speedAmplitude=amplitude
                    FakeLoc.needDowngradeToCdma=snapshot.getBoolean("telephony"); FakeLoc.vendorFused=snapshot.getBoolean("vendor_fused")
                    FakeLoc.enableDebugLog=snapshot.getBoolean("debug")
                    receivedAt=SystemClock.uptimeMillis()
                    FakeLoc.enable=PeerPolicy.enabledFor(processPackage,snapshot.getBoolean("enable"),FakeLoc.needDowngradeToCdma,FakeLoc.vendorFused)
                }
                worker.postDelayed(this,1000L)
            }
        }
        worker.post(poll)
        expiry.post(object : Runnable {
            override fun run() {
                if(PeerPolicy.expired(receivedAt,SystemClock.uptimeMillis())) {
                    FakeLoc.enable=false; FakeLoc.resetSessionOptions()
                }
                expiry.postDelayed(this,3000L)
            }
        })
        return true
    }
}
