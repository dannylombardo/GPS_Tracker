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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Garage
import androidx.compose.material.icons.rounded.Garage
import com.dannylombardo.gpstracker.ui.garage.GarageOverlay
import com.dannylombardo.gpstracker.ui.garage.GarageScreen
import com.dannylombardo.gpstracker.ui.garage.GarageViewModel
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.dannylombardo.gpstracker.ui.theme.apple
import java.time.LocalDate
import java.time.YearMonth

private enum class Tab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Drives("Drives", Icons.Rounded.DirectionsCar, Icons.Outlined.DirectionsCar),
    Fuel("Fuel", Icons.Rounded.LocalGasStation, Icons.Outlined.LocalGasStation),
    Garage("Garage", Icons.Rounded.Garage, Icons.Outlined.Garage),
}

/** What fills the screen, deepest last: the tabs, a day, All drives, All fill-ups or Settings, the cars page or Recently deleted, a drive's page. */
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

    data object Day : Page {
        override val depth = 1
    }

    data object Settings : Page {
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
fun AppRoot(viewModel: MainViewModel, garage: GarageViewModel) {
    val openTripId by viewModel.openTripId.collectAsStateWithLifecycle()
    val carsOpen by viewModel.carsOpen.collectAsStateWithLifecycle()
    val historyOpen by viewModel.historyOpen.collectAsStateWithLifecycle()
    val fuelHistoryOpen by viewModel.fuelHistoryOpen.collectAsStateWithLifecycle()
    val binOpen by viewModel.binOpen.collectAsStateWithLifecycle()
    val dayOpen by viewModel.openDay.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val fuelDraft by viewModel.fuelDraft.collectAsStateWithLifecycle()
    val fuelUpStats by viewModel.fuelUpStats.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Drives) }
    // Kept out here so each tab is scrolled where you left it after visiting a drive.
    val drivesList = rememberLazyListState()
    val fuelList = rememberLazyListState()
    val garageList = rememberLazyListState()
    val historyList = rememberLazyListState()
    val openedWeeks = remember { mutableStateMapOf<LocalDate, Boolean>() }
    val fuelHistoryList = rememberLazyListState()
    val openedMonths = remember { mutableStateMapOf<YearMonth, Boolean>() }
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        garage.tabRequests.collect {
            viewModel.closeTrip()
            tab = Tab.Garage
        }
    }
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
        dayOpen != null -> Page.Day
        historyOpen -> Page.AllDrives
        fuelHistoryOpen -> Page.AllFillUps
        settingsOpen -> Page.Settings
        else -> Page.Tabs
    }

    // The fill-up sheet handles Back itself while it's open.
    BackHandler(enabled = page != Page.Tabs && fuelDraft == null) {
        when (page) {
            is Page.Drive -> viewModel.closeTrip()
            Page.Bin -> viewModel.closeBin()
            Page.Cars -> viewModel.closeCars()
            Page.Day -> viewModel.closeDay()
            Page.AllDrives -> viewModel.closeHistory()
            Page.AllFillUps -> viewModel.closeFuelHistory()
            Page.Settings -> viewModel.closeSettings()
            Page.Tabs -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val opening = targetState.depth > initialState.depth
                val motion = tween<IntOffset>(380)
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
                Page.Day -> DayScreen(viewModel, onBack = viewModel::closeDay)
                Page.AllDrives -> AllDrivesScreen(viewModel, historyList, openedWeeks, onBack = viewModel::closeHistory)
                Page.AllFillUps -> AllFillUpsScreen(viewModel, fuelHistoryList, openedMonths, onBack = viewModel::closeFuelHistory)
                Page.Bin -> RecentlyDeletedScreen(viewModel, onBack = viewModel::closeBin)
                Page.Settings -> SettingsScreen(viewModel, onBack = viewModel::closeSettings)
                Page.Tabs -> TabsPage(
                    tab = tab,
                    onTab = { tab = it },
                    viewModel = viewModel,
                    drivesList = drivesList,
                    fuelList = fuelList,
                    garageList = garageList,
                    garage = garage,
                )
            }
        }

        // The fill-up form slides up as a sheet over whatever page is showing.
        FuelUpSheet(
            draft = fuelDraft,
            stats = fuelDraft?.let { fuelUpStats[it.id] },
            cars = cars,
            onSave = viewModel::saveFuelUp,
            onDelete = { fuelDraft?.let { viewModel.moveFuelUpToBin(it.id) } },
            onDismiss = viewModel::closeFuelUp,
        )

        // The Garage's own pages (live dash, upkeep, recaps...) slide in over everything.
        GarageOverlay(garage, onOpenTrip = viewModel::openTrip)

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Clear of the floating tab bar when it's showing.
                .padding(bottom = if (page == Page.Tabs) TabBarSpace else 8.dp),
        ) { data ->
            Snackbar(
                data,
                modifier = Modifier.padding(horizontal = 4.dp),
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.apple.blue,
                dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

/** The two tabs under one frosted top bar, with the floating glass tab bar at the bottom. */
@Composable
private fun TabsPage(
    tab: Tab,
    onTab: (Tab) -> Unit,
    viewModel: MainViewModel,
    drivesList: LazyListState,
    fuelList: LazyListState,
    garageList: LazyListState,
    garage: GarageViewModel,
) {
    val list = when (tab) {
        Tab.Drives -> drivesList
        Tab.Fuel -> fuelList
        Tab.Garage -> garageList
    }
    val scrolled by list.scrolledState()
    val titleGone by list.largeTitleGoneState()
    val items = remember { Tab.entries.map { TabItem(it.label, it.icon, it.selectedIcon) } }
    PageScaffold(
        title = tab.label,
        scrolled = scrolled,
        showTitle = titleGone,
        bottomSpace = TabBarSpace,
        actions = {
            if (tab == Tab.Fuel) {
                GlassIconButton(
                    Icons.Rounded.Add,
                    contentDescription = "Add fill-up",
                    onClick = { viewModel.openFuelUp() },
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 26.dp,
                )
            }
            GlassIconButton(Icons.Rounded.Settings, contentDescription = "Settings", onClick = viewModel::openSettings)
        },
        bottomBar = { haze ->
            GlassTabBar(
                items = items,
                selected = tab.ordinal,
                onSelect = { onTab(Tab.entries[it]) },
                hazeState = haze,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        },
    ) { padding ->
        Crossfade(targetState = tab, label = "tab") { shown ->
            when (shown) {
                Tab.Drives -> DrivesScreen(viewModel, drivesList, padding)
                Tab.Fuel -> FuelScreen(viewModel, fuelList, padding)
                Tab.Garage -> GarageScreen(garage, garageList, padding)
            }
        }
    }
}
