package com.yadea.smartmoto.manager.utils

import android.graphics.Rect
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.yadea.smartmoto.maps.ProviderMap
import com.yadea.smartmoto.maps.MapOverlay
import com.yadea.smartmoto.maps.MapStatusUpdateFactory

object MapDrawUtils {
    fun drawMarkerToMap(map: ProviderMap, point: LatLng, assetName: String) {
        if (assetName.isNotEmpty()) map.marker(point, assetName)
    }
    fun drawLineToMap(map: ProviderMap, polylineList: List<LatLng>, rect: Rect,
                      isMainLine: Boolean = true, animateMapStatus: Boolean = true): MapOverlay? {
        if (polylineList.isEmpty()) return null
        val overlay = map.line(polylineList, isMainLine)
        if (isMainLine && animateMapStatus) {
            if (polylineList.size == 1) map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(polylineList[0], 16f))
            else map.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(
                LatLngBounds.Builder().include(polylineList).build(), rect.left, rect.top, rect.right, rect.bottom))
        }
        return overlay
    }
}
