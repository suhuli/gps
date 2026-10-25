package com.huolala.mockgps.maps

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.widget.FrameLayout
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.CameraPosition

/** Single native map per view: the unselected SDK never creates a renderer. */
class ProviderMapView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    FrameLayout(context, attrs, defStyle) {
    private val provider = MapPreferences.requireProvider(context)
    private var baidu: com.baidu.mapapi.map.TextureMapView? = null
    private var amap: com.amap.api.maps.TextureMapView? = null
    private var destroyed = false
    private var resumed = false
    val map: ProviderMap
    init {
        if (provider == MapPreferences.Provider.BAIDU) {
            baidu = com.baidu.mapapi.map.TextureMapView(context)
            addView(baidu, LayoutParams(-1, -1))
            map = ProviderMap(baidu!!.map, null)
        } else {
            amap = com.amap.api.maps.TextureMapView(context)
            addView(amap, LayoutParams(-1, -1))
            amap!!.onCreate(null)
            map = ProviderMap(null, amap!!.map)
        }
    }
    // Native view creation happens once in init, including views inflated into dialogs/overlays.
    fun onCreate(context: Context, state: Bundle?) {}
    fun showScaleControl(show: Boolean) { baidu?.showScaleControl(show); amap?.map?.uiSettings?.isScaleControlsEnabled = show }
    fun showZoomControls(show: Boolean) { baidu?.showZoomControls(show); amap?.map?.uiSettings?.isZoomControlsEnabled = show }
    fun onResume() { if (!destroyed && !resumed) { resumed = true; baidu?.onResume(); amap?.onResume() } }
    fun onPause() { if (!destroyed && resumed) { resumed = false; baidu?.onPause(); amap?.onPause() } }
    fun onDestroy() {
        if (destroyed) return
        onPause()
        destroyed = true
        map.release()
        baidu?.onDestroy(); amap?.onDestroy()
        removeAllViews()
    }
    fun onLowMemory() { if (!destroyed) amap?.onLowMemory() }
    fun onSaveInstanceState(state: Bundle) { if (!destroyed) amap?.onSaveInstanceState(state) }
}

class MapStatus(val target: LatLng? = null, val zoom: Float? = null, val rotate: Float? = null, val overlook: Float? = null) {
    class Builder {
        private var rotate: Float? = null
        private var overlook: Float? = null
        fun rotate(value: Float) = apply { rotate = value }
        fun overlook(value: Float) = apply { overlook = value }
        fun build() = MapStatus(rotate = rotate, overlook = overlook)
    }
}
class MapUpdate(val status: MapStatus? = null, val bounds: LatLngBounds? = null, val padding: Rect? = null, val zoomDelta: Float? = null)
object MapStatusUpdateFactory {
    fun newLatLngZoom(point: LatLng, zoom: Float) = MapUpdate(status = MapStatus(point, zoom))
    fun newMapStatus(status: MapStatus) = MapUpdate(status = status)
    fun zoomBy(delta: Float) = MapUpdate(zoomDelta = delta)
    fun newLatLngBounds(bounds: LatLngBounds, left: Int, top: Int, right: Int, bottom: Int) =
        MapUpdate(bounds = bounds, padding = Rect(left, top, right, bottom))
}
class MapOverlay(private val removeAction: () -> Unit) { fun remove() = removeAction() }

class ProviderMap(val baidu: com.baidu.mapapi.map.BaiduMap?, val amap: com.amap.api.maps.AMap?) {
    val uiSettings = Settings()
    var locationData: MyLocationData? = null
        private set
    var isMyLocationEnabled = false
        set(value) { field = value; baidu?.isMyLocationEnabled = value; if (!value) locationMarker?.remove() }
    private var locationMarker: com.amap.api.maps.model.Marker? = null
    private var loaded = false
    private var loadedCallback: (() -> Unit)? = null
    init {
        baidu?.setOnMapLoadedCallback { loaded = true; loadedCallback?.invoke() }
        amap?.setOnMapLoadedListener { loaded = true; loadedCallback?.invoke() }
    }
    fun setOnMapLoadedCallback(callback: () -> Unit) { loadedCallback = callback; if (loaded) callback() }
    fun clear() { baidu?.clear(); amap?.clear(); locationMarker = null }
    fun setMyLocationData(data: MyLocationData) {
        locationData = data
        baidu?.setMyLocationData(data)
        if (amap != null && isMyLocationEnabled) {
            val point = com.amap.api.maps.model.LatLng(data.latitude, data.longitude)
            val marker = locationMarker
            if (marker == null) locationMarker = amap.addMarker(com.amap.api.maps.model.MarkerOptions()
                .position(point).title("当前位置").zIndex(10f)
                .icon(com.amap.api.maps.model.BitmapDescriptorFactory.defaultMarker(com.amap.api.maps.model.BitmapDescriptorFactory.HUE_AZURE)))
            else marker.position = point
        }
    }
    fun setMyLocationConfiguration(config: MyLocationConfiguration) { baidu?.setMyLocationConfiguration(config) }
    fun animateMapStatus(update: MapUpdate) = update(update, true)
    fun setMapStatus(update: MapUpdate) = update(update, false)
    private fun update(value: MapUpdate, animate: Boolean) {
        if (baidu != null) {
            val u = when {
                value.bounds != null -> {
                    val p = value.padding ?: Rect()
                    com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngBounds(value.bounds, p.left, p.top, p.right, p.bottom)
                }
                value.zoomDelta != null -> com.baidu.mapapi.map.MapStatusUpdateFactory.zoomBy(value.zoomDelta)
                else -> {
                    val s = value.status ?: return
                    val b = com.baidu.mapapi.map.MapStatus.Builder(baidu.mapStatus)
                    s.target?.let { b.target(it) }; s.zoom?.let { b.zoom(it) }
                    s.rotate?.let { b.rotate(it) }; s.overlook?.let { b.overlook(it) }
                    com.baidu.mapapi.map.MapStatusUpdateFactory.newMapStatus(b.build())
                }
            }
            if (animate) baidu.animateMapStatus(u) else baidu.setMapStatus(u)
        }
        if (amap != null) {
            val u = when {
                value.bounds != null -> {
                    val b = com.amap.api.maps.model.LatLngBounds.Builder()
                    b.include(value.bounds.southwest.amap()); b.include(value.bounds.northeast.amap())
                    val p = value.padding ?: Rect()
                    CameraUpdateFactory.newLatLngBoundsRect(b.build(), p.left, p.right, p.top, p.bottom)
                }
                value.zoomDelta != null -> CameraUpdateFactory.zoomBy(value.zoomDelta)
                else -> {
                    val s = value.status ?: return
                    val b = CameraPosition.Builder(amap.cameraPosition)
                    s.target?.let { b.target(it.amap()) }; s.zoom?.let { b.zoom(it) }
                    s.rotate?.let { b.bearing(it) }; s.overlook?.let { b.tilt(kotlin.math.abs(it)) }
                    CameraUpdateFactory.newCameraPosition(b.build())
                }
            }
            if (animate) amap.animateCamera(u) else amap.moveCamera(u)
        }
    }
    interface OnMapStatusChangeListener {
        fun onMapStatusChangeStart(mapStatus: MapStatus?)
        fun onMapStatusChangeStart(mapStatus: MapStatus?, reason: Int)
        fun onMapStatusChange(mapStatus: MapStatus?)
        fun onMapStatusChangeFinish(mapStatus: MapStatus?)
    }
    fun setOnMapStatusChangeListener(listener: OnMapStatusChangeListener) {
        baidu?.setOnMapStatusChangeListener(object : com.baidu.mapapi.map.BaiduMap.OnMapStatusChangeListener {
            fun status(s: com.baidu.mapapi.map.MapStatus?) = s?.let { MapStatus(it.target, it.zoom, it.rotate, it.overlook) }
            override fun onMapStatusChangeStart(s: com.baidu.mapapi.map.MapStatus?) = listener.onMapStatusChangeStart(status(s))
            override fun onMapStatusChangeStart(s: com.baidu.mapapi.map.MapStatus?, reason: Int) = listener.onMapStatusChangeStart(status(s), reason)
            override fun onMapStatusChange(s: com.baidu.mapapi.map.MapStatus?) = listener.onMapStatusChange(status(s))
            override fun onMapStatusChangeFinish(s: com.baidu.mapapi.map.MapStatus?) = listener.onMapStatusChangeFinish(status(s))
        })
        amap?.setOnCameraChangeListener(object : com.amap.api.maps.AMap.OnCameraChangeListener {
            private var changing = false
            fun status(s: CameraPosition?) = s?.let { MapStatus(LatLng(it.target.latitude, it.target.longitude), it.zoom, it.bearing, -it.tilt) }
            override fun onCameraChange(s: CameraPosition?) {
                if (!changing) { changing = true; listener.onMapStatusChangeStart(status(s), 1) }
                listener.onMapStatusChange(status(s))
            }
            override fun onCameraChangeFinish(s: CameraPosition?) { changing = false; listener.onMapStatusChangeFinish(status(s)) }
        })
    }
    fun marker(point: LatLng, asset: String) {
        baidu?.addOverlay(com.baidu.mapapi.map.MarkerOptions().position(point)
            .icon(com.baidu.mapapi.map.BitmapDescriptorFactory.fromAsset(asset)).zIndex(2))
        amap?.addMarker(com.amap.api.maps.model.MarkerOptions().position(point.amap())
            .icon(com.amap.api.maps.model.BitmapDescriptorFactory.fromAsset(asset)).zIndex(2f))
    }
    fun line(points: List<LatLng>, main: Boolean): MapOverlay? {
        if (points.size < 2) return null
        baidu?.let { map ->
            val overlay = map.addOverlay(com.baidu.mapapi.map.PolylineOptions().points(points).width(16f)
                .color(if (main) Color.rgb(24, 125, 243) else Color.GRAY).zIndex(if (main) 1 else 0))
            return MapOverlay { overlay?.remove() }
        }
        amap?.let { map ->
            val overlay = map.addPolyline(com.amap.api.maps.model.PolylineOptions().addAll(points.map { it.amap() })
                .width(16f).color(if (main) Color.rgb(24, 125, 243) else Color.GRAY).zIndex(if (main) 1f else 0f))
            return MapOverlay { overlay?.remove() }
        }
        return null
    }
    fun release() { loadedCallback = null; amap?.setOnCameraChangeListener(null); amap?.setOnMapLoadedListener(null); baidu?.setOnMapStatusChangeListener(null) }
    inner class Settings {
        var isCompassEnabled: Boolean
            get() = baidu?.uiSettings?.isCompassEnabled ?: amap?.uiSettings?.isCompassEnabled ?: false
            set(value) { baidu?.uiSettings?.isCompassEnabled = value; amap?.uiSettings?.isCompassEnabled = value }
        var isRotateGesturesEnabled: Boolean
            get() = true
            set(value) { baidu?.uiSettings?.isRotateGesturesEnabled = value; amap?.uiSettings?.isRotateGesturesEnabled = value }
        var isOverlookingGesturesEnabled: Boolean
            get() = true
            set(value) { baidu?.uiSettings?.isOverlookingGesturesEnabled = value; amap?.uiSettings?.isTiltGesturesEnabled = value }
        fun setAllGesturesEnabled(value: Boolean) { baidu?.uiSettings?.setAllGesturesEnabled(value); amap?.uiSettings?.setAllGesturesEnabled(value) }
        fun setEnlargeCenterWithDoubleClickEnable(value: Boolean) { baidu?.uiSettings?.setEnlargeCenterWithDoubleClickEnable(value) }
    }
}
fun LatLng.amap() = com.amap.api.maps.model.LatLng(latitude, longitude)
