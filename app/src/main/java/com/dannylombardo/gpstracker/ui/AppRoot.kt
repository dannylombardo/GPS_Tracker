package com.dannylombardo.gpstracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Tab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Drives("Drives", Icons.Rounded.DirectionsCar, Icons.Outlined.DirectionsCar),
    Fuel("Fuel", Icons.Rounded.LocalGasStation, Icons.Outlined.LocalGasStation),
}

/** The two tabs, with a drive's page sliding in over them when one is opened. */
@Composable
fun AppRoot(viewModel: MainViewModel) {
    val openTripId by viewModel.openTripId.collectAsStateWithLifecycle()
    val fuelDraft by viewModel.fuelDraft.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Drives) }
    // Kept out here so each tab is scrolled where you left it after visiting a drive.
    val drivesList = rememberLazyListState()
    val fuelList = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    BackHandler(enabled = openTripId != null) { viewModel.closeTrip() }

    AnimatedContent(
        targetState = openTripId,
        transitionSpec = {
            val opening = targetState != null
            val motion = tween<IntOffset>(350)
            if (opening) {
                (slideInHorizontally(motion) { it } + fadeIn()) togetherWith
                    (slideOutHorizontally(motion) { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally(motion) { -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally(motion) { it } + fadeOut())
            }
        },
        label = "drive page",
    ) { tripId ->
        if (tripId != null) {
            DriveDetailScreen(tripId = tripId, viewModel = viewModel, onBack = viewModel::closeTrip)
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Tab.entries.forEach { item ->
                            val selected = item == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { tab = item },
                                icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Crossfade(targetState = tab, label = "tab") { shown ->
                    when (shown) {
                        Tab.Drives -> DrivesScreen(viewModel, drivesList, padding)
                        Tab.Fuel -> FuelScreen(viewModel, fuelList, padding)
                    }
                }
            }
        }
    }

    fuelDraft?.let { draft ->
        FuelUpDialog(
            draft = draft,
            onSave = viewModel::saveFuelUp,
            onDelete = { viewModel.deleteFuelUp(draft.id) },
            onDismiss = viewModel::closeFuelUp,
        )
    }
}
