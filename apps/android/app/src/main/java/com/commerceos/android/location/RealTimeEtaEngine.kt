package com.commerceos.android.location

import kotlin.math.*

object RealTimeEtaEngine {
    /**
     * Calculates the great-circle Haversine distance in kilometers.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Real-time road trip ETA in minutes from store to delivery coordinate.
     * Takes into account:
     * 1. 1.30x urban road routing winding factor
     * 2. 4 minutes warehouse picking & packing SLA
     * 3. 20 km/h 2-wheeler average transit speed (3.0 mins/km)
     */
    fun calculateEtaMinutes(
        userLat: Double?,
        userLng: Double?,
        storeLat: Double? = null,
        storeLng: Double? = null
    ): Int {
        if (userLat == null || userLng == null || userLat == 0.0 || userLng == 0.0 ||
            storeLat == null || storeLng == null || storeLat == 0.0 || storeLng == 0.0) {
            return 10
        }
        val straightLineKm = calculateDistanceKm(storeLat, storeLng, userLat, userLng)
        val roadDistanceKm = straightLineKm * 1.30
        val prepTimeMinutes = 4.0
        val travelMinutes = roadDistanceKm * 3.0
        val totalMinutes = (prepTimeMinutes + travelMinutes).roundToInt()

        return totalMinutes.coerceIn(8, 45)
    }

    fun formatEtaLabel(minutes: Int): String {
        return "$minutes mins"
    }
}
