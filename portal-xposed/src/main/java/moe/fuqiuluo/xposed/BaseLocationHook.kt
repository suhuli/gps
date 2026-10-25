package moe.fuqiuluo.xposed

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.microbios.nmea.NMEA
import moe.microbios.nmea.NmeaValue
import kotlin.random.Random

abstract class BaseLocationHook: BaseDivineService() {
    fun injectLocation(originLocation: Location, realLocation: Boolean = true): Location {
        if (realLocation) {
            if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    originLocation.provider == LocationManager.GPS_PROVIDER && originLocation.isComplete
                } else {
                    originLocation.provider == LocationManager.GPS_PROVIDER
                }
            ) {
                FakeLoc.lastLocation = originLocation
            }
        }

        if (!FakeLoc.enable)
            return originLocation


        val provider = if (FakeLoc.disableNetworkLocation && originLocation.provider == LocationManager.NETWORK_PROVIDER) LocationManager.GPS_PROVIDER else originLocation.provider ?: LocationManager.GPS_PROVIDER
        val location = Location(provider)
        location.accuracy = if (FakeLoc.accuracy != 0.0f) FakeLoc.accuracy else originLocation.accuracy
        val coordinates = FakeLoc.coordinatesSnapshot()
        val jitterLat = FakeLoc.jitterLocation(lat = coordinates.first, lon = coordinates.second)
        location.latitude = jitterLat.first
        location.longitude = jitterLat.second
        location.altitude = FakeLoc.altitude
        val speedAmp = if (FakeLoc.speed > 0 && FakeLoc.speedAmplitude > 0) Random.nextDouble(-FakeLoc.speedAmplitude, FakeLoc.speedAmplitude) else 0.0
        location.speed = (FakeLoc.speed + speedAmp).coerceIn(0.0,100.0).toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && originLocation.hasSpeedAccuracy()) {
            location.speedAccuracyMetersPerSecond = 1.0f
        }


        location.time = System.currentTimeMillis()

        // final addition of zero is to remove -0 results. while these are technically within the
        // range [0, 360) according to IEEE semantics, this eliminates possible user confusion.
        var modBearing = FakeLoc.bearing % 360.0 + 0.0
        if (modBearing < 0) {
            modBearing += 360.0
        }
        location.bearing = modBearing.toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.bearingAccuracyDegrees = 1.0f
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (location.hasBearingAccuracy() && location.bearingAccuracyDegrees == 0.0f) {
                location.bearingAccuracyDegrees = 1.0f
            }
        }


        location.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            location.elapsedRealtimeUncertaintyNanos = originLocation.elapsedRealtimeUncertaintyNanos
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.verticalAccuracyMeters = originLocation.verticalAccuracyMeters
        }
        originLocation.extras?.let {
            location.extras = Bundle(it)
        }
        if (location.extras == null) {
            location.extras = Bundle()
        }
        location.extras?.putBoolean("yadea.injected", true)
        location.extras?.putDouble("latlon", location.latitude + location.longitude)
        location.extras?.putInt("satellites", Random.nextInt(8, 45))
        location.extras?.putInt("maxCn0", Random.nextInt(30, 50))
        location.extras?.putInt("meanCn0", Random.nextInt(20, 30))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (originLocation.hasMslAltitude()) {
                location.mslAltitudeMeters = FakeLoc.altitude
            }
            if (originLocation.hasVerticalAccuracy()) {
                location.mslAltitudeAccuracyMeters = location.accuracy
            }
        }
        if (FakeLoc.hideMock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock = false
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock = true
            }
            location.extras?.putBoolean("portal.enable", true)
            location.extras?.putBoolean("is_mock", true)
        }

        kotlin.runCatching {
            XposedHelpers.callMethod(location, "makeComplete")
        }.onFailure {
            Logger.error("makeComplete failed", it)
        }

        if (FakeLoc.enableDebugLog) {
            Logger.debug("injectLocation success! $location")
        }

        return location
    }

    /** Preserve runtime permission and app-op delivery checks for synthetic callbacks. */
    fun forCaller(location: Location, uid: Int): Location? {
        if (uid == android.os.Process.SYSTEM_UID || uid == 0) return location
        val identity = android.os.Binder.clearCallingIdentity()
        try {
        val context = moe.fuqiuluo.xposed.utils.BinderUtils.getSystemContext() ?: return null
        val pkg = context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: return null
        val ops = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        fun allowed(permission: String, op: String): Boolean = runCatching {
            context.checkPermission(permission, -1, uid) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                ops.noteOpNoThrow(op, uid, pkg) == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        if (allowed(android.Manifest.permission.ACCESS_FINE_LOCATION, android.app.AppOpsManager.OPSTR_FINE_LOCATION)) return location
        if (!allowed(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.app.AppOpsManager.OPSTR_COARSE_LOCATION)) return null
        return Location(location).apply {
            latitude = kotlin.math.round(latitude * 100.0) / 100.0
            longitude = kotlin.math.round(longitude * 100.0) / 100.0
            accuracy = maxOf(accuracy, 2000f)
            removeAltitude(); removeSpeed(); removeBearing()
        }
        } finally { android.os.Binder.restoreCallingIdentity(identity) }
    }

    fun injectNMEA(nmeaStr: String): String? {
        if (!FakeLoc.enable || !FakeLoc.enableNMEA) return null
        val (latitude, longitude) = FakeLoc.coordinatesSnapshot()
        return runCatching {
            moe.microbios.nmea.NmeaRewriter.rewrite(nmeaStr, latitude, longitude,
                FakeLoc.altitude, FakeLoc.speed, FakeLoc.bearing, FakeLoc.minSatellites.coerceIn(0, 35))
        }.getOrNull()
    }
}
