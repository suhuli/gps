package com.yadea.smartmoto.root

import com.google.gson.Gson
import java.util.UUID
import kotlin.math.*

data class RoutePoint(val latitude: Double, val longitude: Double) {
    fun validate() { require(latitude.isFinite() && latitude in -90.0..90.0); require(longitude.isFinite() && longitude in -180.0..180.0) }
}
data class RoutePlan(val id: String = UUID.randomUUID().toString(), val name: String,
                     val points: List<RoutePoint>, val coordinateType: String = "gcj02", val schema: Int = 1) {
    fun validate() {
        require(schema == 1); UUID.fromString(id)
        require(name.isNotBlank() && name.length <= 80)
        require(points.size in 2..50000)
        require(coordinateType in setOf("gcj02", "wgs84", "bd09"))
        points.forEach { it.validate() }
        require(points.any { it.latitude != points.first().latitude || it.longitude != points.first().longitude }) { "路线至少需要两个不同的点" }
    }
    fun reversed() = copy(id = UUID.randomUUID().toString(), name = (name.take(76) + "·反向"), points = points.reversed())
}
object RouteCodec {
    const val MAX_BYTES = 5 * 1024 * 1024
    private fun checkInput(text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "文件超过 5 MB" }
        var depth=0; var quoted=false; var escaped=false
        text.forEach { character ->
            if(quoted) { if(escaped) escaped=false else if(character=='\\') escaped=true else if(character=='"') quoted=false }
            else when(character) { '"' -> quoted=true; '{','[' -> { depth++; require(depth<=32) { "JSON 嵌套过深" } }; '}',']' -> { depth--; require(depth>=0) } }
        }
        require(!quoted && depth==0)
    }
    fun decodeMany(text: String, allowPortalHistory: Boolean = false): List<RoutePlan> {
        checkInput(text)
        return try {
            val root=com.google.gson.JsonParser().parse(text)
            val entries=if(root.isJsonArray) root.asJsonArray.toList() else listOf(root)
            require(entries.size in 1..1000)
            val plans=entries.map { element ->
                val item=element.asJsonObject
                if(item.has("schema")) decode(element.toString())
                else {
                    require(allowPortalHistory) { "Portal 历史文件请使用专用导入入口（GCJ-02）" }
                    val points=item.getAsJsonArray("route").map { pair ->
                        if(pair.isJsonArray) { require(pair.asJsonArray.size()==2); RoutePoint(pair.asJsonArray[0].asDouble,pair.asJsonArray[1].asDouble) }
                        else RoutePoint(pair.asJsonObject["first"].asDouble,pair.asJsonObject["second"].asDouble)
                    }
                    RoutePlan(name=item["name"].asString,points=points).also { it.validate() }
                }
            }
            require(plans.sumOf { it.points.size } <= 50000) { "单次导入最多 50000 点" }; plans
        } catch(e: Exception) { throw IllegalArgumentException(e.message ?: "路线文件不合法",e) }
    }

    fun encode(plan: RoutePlan): String { plan.validate(); return Gson().toJson(plan) }
    fun decode(text: String): RoutePlan {
        checkInput(text)
        return try { Gson().fromJson(text, RoutePlan::class.java).also { requireNotNull(it); it.validate() } }
        catch (e: Exception) { throw IllegalArgumentException("路线格式或坐标不合法", e) }
    }
}
object MotionMath {
    fun step(point: RoutePoint, bearing: Double, distance: Double): RoutePoint {
        point.validate(); require(bearing.isFinite() && distance.isFinite() && distance in 0.0..10000.0)
        val lat = Math.toRadians(point.latitude); val lon = Math.toRadians(point.longitude)
        val angular = distance / 6371000.0; val direction = Math.toRadians(bearing)
        val nextLat = asin((sin(lat) * cos(angular) + cos(lat) * sin(angular) * cos(direction)).coerceIn(-1.0,1.0))
        val nextLon = lon + atan2(sin(direction)*sin(angular)*cos(lat),cos(angular)-sin(lat)*sin(nextLat))
        return RoutePoint(Math.toDegrees(nextLat), ((Math.toDegrees(nextLon)+540)%360)-180)
    }
}
object RootMotion {
    data class Command(val bearing: Double = 0.0, val speedKmh: Double = 0.0, val automatic: Boolean = false)
    @Volatile private var wakeup: (() -> Unit)? = null
    fun installWakeup(callback: (() -> Unit)?) { wakeup=callback }
    @Volatile var command = Command()
        private set
    fun set(bearing: Double, speed: Double, automatic: Boolean) {
        require(bearing.isFinite() && speed.isFinite() && speed in 0.0..300.0)
        val previous=command
        command = Command(((bearing%360)+360)%360, speed, automatic)
        if(previous.speedKmh==0.0 && speed>0.0) wakeup?.invoke()
    }
    fun stop() { val previous=command; command = Command(); if(previous.speedKmh>0.0) wakeup?.invoke() }
}