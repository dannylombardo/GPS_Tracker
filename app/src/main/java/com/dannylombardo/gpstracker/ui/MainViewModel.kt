package com.dannylombardo.gpstracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.data.TripRepository
import com.dannylombardo.gpstracker.tracking.DriveDetection
import com.dannylombardo.gpstracker.tracking.DriveState
import com.dannylombardo.gpstracker.tracking.DriveTrackingService
import com.dannylombardo.gpstracker.tracking.Permissions
import com.dannylombardo.gpstracker.tracking.TrackingPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val location: Boolean = false,
    val backgroundLocation: Boolean = false,
    val activityRecognition: Boolean = false,
    val notifications: Boolean = false,
) {
    val canAutoTrack get() = location && backgroundLocation && activityRecognition
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository = TripRepository.get(application)

    val trips: StateFlow<List<Trip>> = repository.observeFinishedTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTrip: StateFlow<Trip?> = DriveState.activeTripId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeTrip(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _autoTrack = MutableStateFlow(TrackingPrefs.isAutoTrackEnabled(application))
    val autoTrack: StateFlow<Boolean> = _autoTrack.asStateFlow()

    init {
        // Tidy up trips left open if the app was killed mid-drive while not tracking now.
        if (!DriveState.isServiceRunning) {
            viewModelScope.launch {
                repository.closeAbandonedTrips(
                    exceptTripId = null,
                    minDistanceMeters = DriveTrackingService.MIN_TRIP_METERS,
                )
            }
        }
    }

    /** Called whenever the screen resumes, since permissions can change in system settings. */
    fun refresh() {
        val state = PermissionState(
            location = Permissions.hasLocation(app),
            backgroundLocation = Permissions.hasBackgroundLocation(app),
            activityRecognition = Permissions.hasActivityRecognition(app),
            notifications = Permissions.hasNotifications(app),
        )
        _permissions.value = state
        if (_autoTrack.value && state.canAutoTrack) {
            // Cheap and idempotent; makes sure detection is registered after reinstalls etc.
            viewModelScope.launch { DriveDetection.start(app) }
        }
    }

    fun setAutoTrack(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                if (!DriveDetection.start(app)) return@launch
            } else {
                DriveDetection.stop(app)
            }
            TrackingPrefs.setAutoTrackEnabled(app, enabled)
            _autoTrack.value = enabled
        }
    }

    fun startDrive() = DriveTrackingService.startManually(app)

    fun endDrive() = DriveTrackingService.stopManually(app)
}
