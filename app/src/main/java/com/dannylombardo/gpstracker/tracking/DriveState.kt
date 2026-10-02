package com.dannylombardo.gpstracker.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process view of whether a drive is being recorded right now. */
object DriveState {
    private val _activeTripId = MutableStateFlow<Long?>(null)
    val activeTripId: StateFlow<Long?> = _activeTripId.asStateFlow()

    /** True from the moment the tracking service starts a drive until it ends. */
    @Volatile
    var isServiceRunning: Boolean = false
        internal set

    internal fun setActiveTrip(tripId: Long?) {
        _activeTripId.value = tripId
    }
}
