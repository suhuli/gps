package com.yadea.smartmoto.utils

/** Strict, bounded parser. The complete file is validated before any route is replaced. */
object TrackParser {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_POINTS = 50000
    data class Point(val longitude: Double, val latitude: Double)
    fun parse(text: String): List<Point> {
        require(text.length <= MAX_BYTES) { "轨迹文件过大，最多支持 5 MB" }
        val points = ArrayList<Point>()
        for (raw in text.trim().removePrefix("\uFEFF").splitToSequence(';')) {
            val token = raw.trim()
            if (token.isEmpty()) continue
            require(points.size < MAX_POINTS) { "轨迹点过多，最多支持 50000 个点" }
            val pair = token.split(',')
            require(pair.size == 2) { "第 ${points.size + 1} 个点格式错误，应为 经度,纬度" }
            val lng = pair[0].trim().toDoubleOrNull()
            val lat = pair[1].trim().toDoubleOrNull()
            require(lng != null && lat != null && lng.isFinite() && lat.isFinite() && lng in -180.0..180.0 && lat in -90.0..90.0) { "第 ${points.size + 1} 个点经纬度无效" }
            val p = Point(lng, lat)
            if (points.lastOrNull() != p) points.add(p)
        }
        require(points.size >= 2) { "轨迹至少需要两个不同坐标点" }
        return points
    }
}
