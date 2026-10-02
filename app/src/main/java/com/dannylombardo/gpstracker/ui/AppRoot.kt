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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth

private enum class Tab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Drives("Drives", Icons.Rounded.DirectionsCar, Icons.Outlined.DirectionsCar),
    Fuel("Fuel", Icons.Rounded.LocalGasStation, Icons.Outlined.LocalGasStation),
}

/** What fills the screen, deepest last: the tabs, All drives or All fill-ups, the cars page or Recently deleted, a drive's page. */
private sealed interface Page {
    val depth: Int

    data object Tabs : Page {
        override val depth = 0
    }

    data object AllDrives : Page {
        override val depth = 1
    }

    data object AllFillUps : Page {
        override val depth = 1
    }

    data object Cars : Page {
        override val depth = 2
    }

    data object Bin : Page {
        override val depth = 2
    }

    data class Drive(val tripId: Long) : Page {
        override val depth = 3
    }
}

/** The two tabs, with the other pages sliding in over them when opened. */
@Composable
fun AppRoot(viewModel: MainViewModel) {
    val openTripId by viewModel.openTripId.collectAsStateWithLifecycle()
    val carsOpen by viewModel.carsOpen.collectAsStateWithLifecycle()
    val historyOpen by viewModel.historyOpen.collectAsStateWithLifecycle()
    val fuelHistoryOpen by viewModel.fuelHistoryOpen.collectAsStateWithLifecycle()
    val binOpen by viewModel.binOpen.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val fuelDraft by viewModel.fuelDraft.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Drives) }
    // Kept out here so each tab is scrolled where you left it after visiting a drive.
    val drivesList = rememberLazyListState()
    val fuelList = rememberLazyListState()
    val historyList = rememberLazyListState()
    val openedWeeks = remember { mutableStateMapOf<LocalDate, Boolean>() }
    val fuelHistoryList = rememberLazyListState()
    val openedMonths = remember { mutableStateMapOf<YearMonth, Boolean>() }
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        viewModel.binnedEvents.collect { binned ->
            val result = snackbar.showSnackbar(
                message = when (binned) {
                    is Binned.Drive -> "Drive moved to Recently deleted"
                    is Binned.FillUp -> "Fill-up moved to Recently deleted"
                },
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(binned)
        }
    }

    val page = openTripId?.let { Page.Drive(it) } ?: when {
        binOpen -> Page.Bin
        carsOpen -> Page.Cars
        historyOpen -> Page.AllDrives
        fuelHistoryOpen -> Page.AllFillUps
        else -> Page.Tabs
    }

    BackHandler(enabled = page != Page.Tabs) {
        when (page) {
            is Page.Drive -> viewModel.closeTrip()
            Page.Bin -> viewModel.closeBin()
            Page.Cars -> viewModel.closeCars()
            Page.AllDrives -> viewModel.closeHistory()
            Page.AllFillUps -> viewModel.closeFuelHistory()
            Page.Tabs -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                Page.AllDrives -> AllDrivesScreen(viewModel, historyList, openedWeeks, onBack = viewModel::closeHistory)
                Page.AllFillUps -> AllFillUpsScreen(viewModel, fuelHistoryList, openedMonths, onBack = viewModel::closeFuelHistory)
                Page.Bin -> RecentlyDeletedScreen(viewModel, onBack = viewModel::closeBin)
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
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Clear of the tab bar when it's showing.
                .padding(bottom = if (page == Page.Tabs) 80.dp else 0.dp),
        )
    }

    fuelDraft?.let { draft ->
        FuelUpDialog(
            draft = draft,
            cars = cars,
            onSave = viewModel::saveFuelUp,
            onDelete = { viewModel.moveFuelUpToBin(draft.id) },
            onDismiss = viewModel::closeFuelUp,
        )
    }
}
