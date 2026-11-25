package com.dannylombardo.gpstracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.vector.ImageVector
import com.dannylombardo.gpstracker.ui.theme.apple
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.CarStats
import com.dannylombardo.gpstracker.ui.theme.HeroColors

/**
 * The chips under a screen's title: all cars together, or one car on its own.
 * With a single car there's nothing to switch, so it just offers to add another.
 */
@Composable
internal fun CarFilterRow(
    cars: List<Car>,
    viewedCarId: Long?,
    onSelect: (Long?) -> Unit,
    onManage: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cars.size > 1) {
            Capsule("All cars", selected = viewedCarId == null, onClick = { onSelect(null) })
            cars.forEach { car ->
                Capsule(car.name, selected = viewedCarId == car.id, onClick = { onSelect(car.id) })
            }
            Capsule("Cars", selected = false, onClick = onManage, icon = Icons.Rounded.Garage)
        } else {
            cars.firstOrNull()?.let { car -> Capsule(car.name, selected = true, onClick = onManage, icon = Icons.Rounded.DirectionsCar) }
            Capsule("Add a car", selected = false, onClick = onManage, icon = Icons.Rounded.Add)
        }
    }
}

/** A capsule chip: solid accent when picked, glass when not. */
@Composable
internal fun Capsule(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else glassFill(), label = "chip")
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(background)
            .border(if (selected) BorderStroke(0.dp, Color.Transparent) else glassEdge(), CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of chips to pick one car, e.g. which car a fill-up or drive belongs to. */
@Composable
internal fun CarChoiceChips(cars: List<Car>, selectedId: Long?, onSelect: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cars.forEach { car ->
            Capsule(
                car.name,
                selected = car.id == selectedId,
                onClick = { onSelect(car.id) },
                icon = if (car.id == selectedId) Icons.Rounded.Check else Icons.Rounded.DirectionsCar,
            )
        }
    }
}

/** The car's name with a drop-down to switch it, for picking the car new drives go to. */
@Composable
internal fun ActiveCarPicker(
    label: String,
    cars: List<Car>,
    active: Car?,
    onPick: (Long) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("$label ${active?.name.orEmpty()}", color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Change car", tint = color, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            cars.forEach { car ->
                DropdownMenuItem(
                    text = { Text(car.name) },
                    leadingIcon = {
                        if (car.id == active?.id) Icon(Icons.Rounded.Check, contentDescription = null) else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        open = false
                        onPick(car.id)
                    },
                )
            }
        }
    }
}

/** Every car with its all-time numbers; add, rename, delete, and pick the one new drives go to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val overviews by viewModel.carOverviews.collectAsStateWithLifecycle()
    val activeCar by viewModel.activeCar.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Car?>(null) }
    var deleting by remember { mutableStateOf<Car?>(null) }
    val listState = rememberLazyListState()
    val scrolled by listState.scrolledState()

    PageScaffold(title = "Your cars", scrolled = scrolled, onBack = onBack) { padding ->
        val (allTotals, perCar) = overviews ?: return@PageScaffold
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AllCarsCard(allTotals, carCount = perCar.size) }
            item { SectionTitle("Cars") }
            items(perCar, key = { "car-${it.car.id}" }) { overview ->
                CarCard(
                    overview = overview,
                    isActive = overview.car.id == activeCar?.id,
                    canDelete = perCar.size > 1,
                    onMakeActive = { viewModel.setActiveCar(overview.car.id) },
                    onRename = { renaming = overview.car },
                    onDelete = { deleting = overview.car },
                )
            }
            item {
                TintedButton("Add a car", onClick = { adding = true }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
            }
            item {
                Text(
                    "New drives go to the car marked \"New drives\". You can move any drive to another car on its page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (adding) {
        CarNameDialog(
            title = "Add a car",
            initial = "",
            onSave = {
                adding = false
                viewModel.addCar(it)
            },
            onDismiss = { adding = false },
        )
    }
    renaming?.let { car ->
        CarNameDialog(
            title = "Rename car",
            initial = car.name,
            onSave = {
                renaming = null
                viewModel.renameCar(car.id, it)
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { car ->
        DeleteCarDialog(
            car = car,
            others = cars.filter { it.id != car.id },
            onDelete = { moveTo ->
                deleting = null
                viewModel.deleteCar(car.id, moveTo)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun AllCarsCard(totals: CarStats.Totals, carCount: Int) {
    val onHero = HeroColors.content
    HeroCard(HeroColors.drive) {
        run {
            Text(
                if (carCount == 1) "All time" else "All $carCount cars, all time",
                style = MaterialTheme.typography.titleMedium,
                color = onHero,
            )
            BigNumber(kmNumber(totals.distanceMeters), "km", color = onHero, style = MaterialTheme.typography.displayMedium)
            Row(Modifier.fillMaxWidth()) {
                HeroStat("Drives", totals.driveCount.toString(), onHero, Modifier.weight(1f))
                HeroStat("Spent on fuel", formatMoney(totals.moneySpent), onHero, Modifier.weight(1f))
                HeroStat("Litres", formatLitres(totals.litres), onHero, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CarCard(
    overview: CarOverview,
    isActive: Boolean,
    canDelete: Boolean,
    onMakeActive: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    GlassCard(contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    Icons.Rounded.DirectionsCar,
                    container = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.apple.fill,
                    content = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        overview.car.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isActive) {
                        Pill("New drives", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${overview.car.name}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menu = false
                                onRename()
                            },
                        )
                        if (canDelete) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(end = 12.dp)) {
                CarFigure("Driven", formatKm(overview.totals.distanceMeters), Modifier.weight(1f))
                CarFigure("Drives", overview.totals.driveCount.toString(), Modifier.weight(1f))
                CarFigure("Economy", overview.litresPer100Km?.let(::formatConsumption) ?: "–", Modifier.weight(1.3f))
                CarFigure("Fuel", formatMoney(overview.totals.moneySpent), Modifier.weight(1f))
            }
            if (!isActive) {
                TintedButton("Use for new drives", onClick = onMakeActive, modifier = Modifier.padding(end = 12.dp).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CarFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CarNameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DirectionsCar, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Civic, Work truck") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Deleting a car asks where its drives and fill-ups go: another car, or the bin. */
@Composable
private fun DeleteCarDialog(car: Car, others: List<Car>, onDelete: (Long?) -> Unit, onDismiss: () -> Unit) {
    // Null means delete them along with the car.
    var moveTo by remember { mutableStateOf(others.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text("Delete ${car.name}?") },
        text = {
            Column(Modifier.selectableGroup()) {
                Text("What should happen to its drives and fill-ups?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                others.forEach { other ->
                    ChoiceRow("Move them to ${other.name}", selected = moveTo == other.id, onClick = { moveTo = other.id })
                }
                ChoiceRow("Delete them for good", selected = moveTo == null, onClick = { moveTo = null })
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(moveTo) }) { Text("Delete car", color = MaterialTheme.apple.red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
