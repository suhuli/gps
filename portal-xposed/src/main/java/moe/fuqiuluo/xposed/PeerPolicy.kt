package moe.fuqiuluo.xposed

/** Pure policy so read-only peer admission, input bounds and lease behavior are unit-testable. */
object PeerPolicy {
    val packages = setOf("com.android.phone", "com.android.location.fused", "com.xiaomi.location.fused", "com.xiaomi.metoknlp", "com.oplus.location")
    fun allowed(name: String, systemInstalled: Boolean) = systemInstalled && name in packages
    fun valid(lat: Double, lon: Double, speed: Double, altitude: Double, accuracy: Float, bearing: Double) =
        lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0 &&
        speed.isFinite() && speed in 0.0..100.0 && altitude.isFinite() && altitude in -1000.0..100000.0 &&
        accuracy.isFinite() && accuracy in 0.5f..10000f && bearing.isFinite() && bearing in 0.0..360.0
    fun expired(received: Long, now: Long) = received == 0L || now < received || now-received > 15000L
    fun enabledFor(name: String, enabled: Boolean, phone: Boolean, fused: Boolean) = enabled && name in packages && if(name == "com.android.phone") phone else fused
}
