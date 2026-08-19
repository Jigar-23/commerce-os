package com.commerceos.rider.service

import com.commerceos.rider.model.RiderLocationUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RiderLocationService {

    private val _lastLocation = MutableStateFlow<RiderLocationUpdate?>(null)
    val lastLocation: StateFlow<RiderLocationUpdate?> = _lastLocation.asStateFlow()

    private var lastEmittedTimestamp = 0L

    fun onLocationSensorUpdate(update: RiderLocationUpdate) {
        if (lastEmittedTimestamp != 0L && update.timestamp - lastEmittedTimestamp < 1000L) {
            return
        }
        lastEmittedTimestamp = update.timestamp
        _lastLocation.value = update
    }
}
