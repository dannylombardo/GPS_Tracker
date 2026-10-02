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

/** What fills the screen: the tabs, the cars page, or a drive's page, deepest last. */
private sealed interface Page {
    val depth: Int

    data object Tabs : Page {
        override val depth = 0
    }

    data object Cars : Page {
        override val depth = 1
    }

    data class Drive(val tripId: Long) : Page {
        override val depth = 2
    }
}

/** The two tabs, with the cars page or a drive's page sliding in over them when opened. */
@Composable
fun AppRoot(viewModel: MainViewModel) {
    val openTripId by viewModel.openTripId.collectAsStateWithLifecycle()
    val carsOpen by viewModel.carsOpen.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val fuelDraft by viewModel.fuelDraft.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Drives) }
    // Kept out here so each tab is scrolled where you left it after visiting a drive.
    val drivesList = rememberLazyListState()
    val fuelList = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    BackHandler(enabled = openTripId != null || carsOpen) {
        if (openTripId != null) viewModel.closeTrip() else viewModel.closeCars()
    }

    val page = openTripId?.let { Page.Drive(it) } ?: if (carsOpen) Page.Cars else Page.Tabs

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val opening = targetState.depth > initialState.depth
            val motion = tween<IntOffset>(350)
            if (opening) {
                (slideInHorizontally(motion) { it } + fadeIn()) togetherWith
                    (slideOutHorizontally(motion) { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally(motion) { -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally(motion) { it } + fadeOut())
            }
        },
        label = "page",
    ) { shownPage ->
        when (shownPage) {
            is Page.Drive -> DriveDetailScreen(tripId = shownPage.tripId, viewModel = viewModel, onBack = viewModel::closeTrip)
            Page.Cars -> CarsScreen(viewModel = viewModel, onBack = viewModel::closeCars)
            Page.Tabs -> Scaffold(
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
            cars = cars,
            onSave = viewModel::saveFuelUp,
            onDelete = { viewModel.deleteFuelUp(draft.id) },
            onDismiss = viewModel::closeFuelUp,
        )
    }
}
