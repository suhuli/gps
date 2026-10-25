package com.huolala.mockgps.utils

import org.junit.Assert.*
import org.junit.Test

class LocationUtilsTest {
    @Test fun gcjBdRoundTrip() {
        val bd = LocationUtils.gcj02ToBd09(116.397, 39.908)
        val gcj = LocationUtils.bd09ToGcj02(bd[0], bd[1])
        assertEquals(116.397, gcj[0], 0.000002)
        assertEquals(39.908, gcj[1], 0.000002)
    }
    @Test fun wgsGcjRoundTrip() {
        val gcj = LocationUtils.wgs84ToGcj02(116.397, 39.908)
        val wgs = LocationUtils.gcj02ToWGS84(gcj[0], gcj[1])
        assertEquals(116.397, wgs[0], 0.00005)
        assertEquals(39.908, wgs[1], 0.00005)
    }
    @Test fun overseasWgsMustRemainUnchanged() {
        listOf(103.9 to 1.35, -73.98 to 40.75, 151.2 to -33.8).forEach { (lng, lat) ->
            assertArrayEquals(doubleArrayOf(lng, lat), LocationUtils.wgs84ToGcj02(lng, lat), 0.0)
            assertArrayEquals(doubleArrayOf(lng, lat), LocationUtils.gcj02ToWGS84(lng, lat), 0.0)
        }
    }
    @Test fun gcjWgsConversionIsNotIdentityInBeijing() {
        val point = LocationUtils.gcj02ToWGS84(116.397, 39.908)
        assertTrue(kotlin.math.abs(point[0] - 116.397) > 0.001)
    }
}
