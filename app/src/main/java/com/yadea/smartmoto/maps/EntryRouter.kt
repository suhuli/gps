package com.yadea.smartmoto.maps

/** Pure startup decision: configuration never bypasses consent or required permissions. */
object EntryRouter {
    enum class Destination { GUIDE, MAP_SELECTION, MAIN }
    fun destination(consent: Boolean, location: Boolean, mockLocation: Boolean, configured: Boolean, switching: Boolean): Destination = when {
        !consent || !location || !mockLocation -> Destination.GUIDE
        switching || !configured -> Destination.MAP_SELECTION
        else -> Destination.MAIN
    }
}
