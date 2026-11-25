package com.dannylombardo.gpstracker.ui.garage

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.ui.LargeTitle
import com.dannylombardo.gpstracker.ui.PageScaffold
import com.dannylombardo.gpstracker.ui.largeTitleGoneState
import com.dannylombardo.gpstracker.ui.scrolledState

/**
 * Draws the Garage's pages over the whole app, sliding in from the right (the live
 * dash rises from the bottom). Drawn in-page rather than in a dialog so the system
 * bars and keyboard insets behave.
 */
@Composable
fun GarageOverlay(vm: GarageViewModel, onOpenTrip: (Long) -> Unit) {
    val page by vm.page.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf(page) }
    if (page != null) shown = page
    val isLive = shown == GaragePage.Live
    AnimatedVisibility(
        visible = page != null,
        enter = if (isLive) slideInVertically(tween(380)) { it } + fadeIn() else slideInHorizontally(tween(380)) { it } + fadeIn(),
        exit = if (isLive) slideOutVertically(tween(320)) { it } + fadeOut() else slideOutHorizontally(tween(320)) { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        BackHandler(enabled = page != null, onBack = vm::close)
        val current = shown ?: return@AnimatedVisibility
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val motion = tween<IntOffset>(320)
                (slideInHorizontally(motion) { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(motion) { -it / 3 } + fadeOut())
            },
            label = "garage-page",
        ) { p ->
            val openTrip: (Long) -> Unit = { id ->
                vm.close()
                onOpenTrip(id)
            }
            when (p) {
                GaragePage.Live -> LiveDriveScreen(vm, onBack = vm::close)
                GaragePage.Spec -> SpecScreen(vm, onBack = vm::close)
                GaragePage.Maintenance -> MaintenanceScreen(vm, onBack = vm::close)
                GaragePage.FuelPlanner -> FuelPlannerScreen(vm, onBack = vm::close)
                GaragePage.Score -> ScoreScreen(vm, onBack = vm::close, onOpenTrip = openTrip)
                GaragePage.Records -> RecordsScreen(vm, onBack = vm::close, onOpenTrip = openTrip)
                GaragePage.Places -> PlacesScreen(vm, onBack = vm::close)
                GaragePage.Data -> DataScreen(vm, onBack = vm::close)
                is GaragePage.Wrapped -> WrappedScreen(vm, p.period, onBack = vm::close)
            }
        }
    }
}

/** A garage page: frosted bar with a back button, a large title, and a list. */
@Composable
internal fun GarageList(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val list = rememberLazyListState()
    val scrolled by list.scrolledState()
    val titleGone by list.largeTitleGoneState()
    PageScaffold(title = title, scrolled = scrolled, showTitle = titleGone, onBack = onBack, actions = actions) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LargeTitle(title, subtitle) }
            content()
        }
    }
}
