package com.yadea.smartmoto.root

/** WGS84 and SI units at the engine boundary. Does not depend on map or Android classes. */
data class RootFrame(val latitude: Double, val longitude: Double, val speed: Double,
                     val bearing: Double, val altitude: Double, val accuracy: Float) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(speed.isFinite() && speed in 0.0..100.0)
        require(bearing.isFinite())
        require(altitude.isFinite() && altitude in -1000.0..100000.0)
        require(accuracy.isFinite() && accuracy in 0.5f..10000f)
    }
    fun stationary() = copy(speed = 0.0)
    companion object {
        fun interval(value: Long) = value.coerceIn(250L, 5000L)
    }
}
