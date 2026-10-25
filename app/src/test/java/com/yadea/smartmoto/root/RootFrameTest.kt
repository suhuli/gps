package com.yadea.smartmoto.root

import org.junit.Assert.*
import org.junit.Test

class RootFrameTest {
    private fun point(lat: Double = 31.2, lon: Double = 121.5, speed: Double = 0.0) = RootFrame(lat, lon, speed, 0.0, 80.0, 1f)
    @Test fun fixedPointDoesNotIntroduceMotion() { assertEquals(0.0, point().speed, 0.0) }
    @Test fun pausePreservesCoordinatesAndStopsSpeed() {
        val before = point(speed = 12.0); val after = before.stationary()
        assertEquals(before.latitude, after.latitude, 0.0)
        assertEquals(before.longitude, after.longitude, 0.0)
        assertEquals(0.0, after.speed, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidLatitudeRejected() { point(lat = 100.0) }
    @Test(expected = IllegalArgumentException::class) fun nonFinitePositionRejected() { point(lon = Double.NaN) }
    @Test(expected = IllegalArgumentException::class) fun invalidSpeedRejected() { point(speed = -1.0) }
    @Test(expected = IllegalArgumentException::class) fun invalidAccuracyRejected() { point().copy(accuracy = 0f) }
    @Test fun intervalIsBounded() { assertEquals(250L, RootFrame.interval(0)); assertEquals(5000L, RootFrame.interval(Long.MAX_VALUE)) }
}
