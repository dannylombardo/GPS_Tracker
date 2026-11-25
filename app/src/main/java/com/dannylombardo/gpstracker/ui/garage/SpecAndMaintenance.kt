package com.dannylombardo.gpstracker.ui.garage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.garage.CarSpec
import com.dannylombardo.gpstracker.garage.Maintenance
import com.dannylombardo.gpstracker.garage.MaintenanceItem
import com.dannylombardo.gpstracker.garage.MaintenanceKind
import com.dannylombardo.gpstracker.ui.GlassCard
import com.dannylombardo.gpstracker.ui.GlassTextButton
import com.dannylombardo.gpstracker.ui.GroupedRow
import com.dannylombardo.gpstracker.ui.GroupedSection
import com.dannylombardo.gpstracker.ui.PrimaryButton
import com.dannylombardo.gpstracker.ui.RowDivider
import com.dannylombardo.gpstracker.ui.SectionTitle
import com.dannylombardo.gpstracker.ui.TintedButton
import com.dannylombardo.gpstracker.ui.formatMoney
import com.dannylombardo.gpstracker.ui.theme.apple
import kotlin.math.roundToInt

/** The car's spec sheet: make, model, power, tank size, and the odometer reading. */
@Composable
internal fun SpecScreen(vm: GarageViewModel, onBack: () -> Unit) {
    val garage by vm.garage.collectAsStateWithLifecycle()
    val g = garage ?: return
    val spec = g.spec ?: CarSpec(carId = g.car.id)
    val key = g.car.id
    var make by rememberSaveable(key) { mutableStateOf(spec.make.orEmpty()) }
    var model by rememberSaveable(key) { mutableStateOf(spec.model.orEmpty()) }
    var year by rememberSaveable(key) { mutableStateOf(spec.year?.toString().orEmpty()) }
    var trim by rememberSaveable(key) { mutableStateOf(spec.trim.orEmpty()) }
    var engine by rememberSaveable(key) { mutableStateOf(spec.engine.orEmpty()) }
    var drivetrain by rememberSaveable(key) { mutableStateOf(spec.drivetrain.orEmpty()) }
    var hp by rememberSaveable(key) { mutableStateOf(spec.horsepower?.toString().orEmpty()) }
    var torque by rememberSaveable(key) { mutableStateOf(spec.torqueNm?.toString().orEmpty()) }
    var tank by rememberSaveable(key) { mutableStateOf(plainNumber(spec.fuelTankLitres)) }
    var plate by rememberSaveable(key) { mutableStateOf(spec.plate.orEmpty()) }
    // Starts from what the app thinks the dash says now, so saving without touching it changes nothing.
    val odoNow = g.odometerKm.roundToInt().toString()
    var odometer by rememberSaveable(key) { mutableStateOf(if (spec.odometerKm != null) odoNow else "") }

    fun save() {
        val newOdo = parseNumber(odometer)
        val odoChanged = newOdo != null && (spec.odometerKm == null || newOdo.roundToInt().toString() != odoNow)
        vm.saveSpec(
            spec.copy(
                make = make.trim().ifBlank { null },
                model = model.trim().ifBlank { null },
                year = year.trim().toIntOrNull(),
                trim = trim.trim().ifBlank { null },
                engine = engine.trim().ifBlank { null },
                drivetrain = drivetrain.trim().ifBlank { null },
                horsepower = hp.trim().toIntOrNull(),
                torqueNm = torque.trim().toIntOrNull(),
                fuelTankLitres = parseNumber(tank),
                plate = plate.trim().ifBlank { null },
                odometerKm = if (odoChanged) newOdo else if (newOdo == null) null else spec.odometerKm,
                odometerSetAt = if (odoChanged) System.currentTimeMillis() else if (newOdo == null) null else spec.odometerSetAt,
            ),
        )
        onBack()
    }

    GarageList(
        title = "Spec sheet",
        subtitle = g.car.name,
        onBack = onBack,
        actions = { GlassTextButton("Save", onClick = ::save) },
    ) {
        item {
            GroupedSection(header = "The car") {
                InputRow("Make", make, { make = it }, "Honda")
                RowDivider(16.dp)
                InputRow("Model", model, { model = it }, "Civic")
                RowDivider(16.dp)
                InputRow("Year", year, { year = it.filter(Char::isDigit).take(4) }, "2019", number = true)
                RowDivider(16.dp)
                InputRow("Trim", trim, { trim = it }, "Si")
                RowDivider(16.dp)
                InputRow("Plate", plate, { plate = it }, "Optional")
            }
        }
        item {
            GroupedSection(header = "Under the hood") {
                InputRow("Engine", engine, { engine = it }, "1.5L turbo I4")
                RowDivider(16.dp)
                InputRow("Drivetrain", drivetrain, { drivetrain = it }, "FWD, 6MT")
                RowDivider(16.dp)
                InputRow("Power", hp, { hp = it.filter(Char::isDigit).take(4) }, "205", unit = "hp", number = true)
                RowDivider(16.dp)
                InputRow("Torque", torque, { torque = it.filter(Char::isDigit).take(4) }, "260", unit = "Nm", number = true)
            }
        }
        item {
            GroupedSection(
                header = "Running",
                footer = "Enter what the dash shows now and the app keeps it going from every drive in this car. " +
                    "The tank size lets it estimate how far you can go before the next fill-up.",
            ) {
                InputRow("Odometer", odometer, { odometer = it.filter { c -> c.isDigit() }.take(7) }, odoNow, unit = "km", number = true)
                RowDivider(16.dp)
                InputRow("Fuel tank", tank, { tank = it }, "47", unit = "L", number = true)
            }
        }
        item { PrimaryButton("Save", onClick = ::save, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
    }
}

/** Every maintenance job for the car, how close each is, and the log of what was done. */
@Composable
internal fun MaintenanceScreen(vm: GarageViewModel, onBack: () -> Unit) {
    val garage by vm.garage.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val g = garage ?: return
    var editing by remember { mutableStateOf<MaintenanceItem?>(null) }
    var finishing by remember { mutableStateOf<MaintenanceItem?>(null) }
    var logging by remember { mutableStateOf(false) }
    val carLogs = logs.filter { it.carId == g.car.id }

    GarageList(
        title = "Upkeep",
        subtitle = g.spec?.title ?: g.car.name,
        onBack = onBack,
        actions = { GlassTextButton("Add", onClick = { editing = MaintenanceItem(carId = g.car.id, name = "", lastDoneAt = System.currentTimeMillis()) }) },
    ) {
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        GaugeLabel("Odometer")
                        Text(formatKmWhole(g.odometerKm), style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        GaugeLabel("Spent on upkeep")
                        Text(formatMoney(g.maintenanceSpent), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        items(g.maintenance, key = { it.item.id }) { s ->
            MaintenanceCard(s, onDone = { finishing = s.item }, onEdit = { editing = s.item })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TintedButton("Log other work", onClick = { logging = true }, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
            }
        }
        if (carLogs.isNotEmpty()) {
            item { SectionTitle("History", trailing = "${carLogs.size} jobs") }
            item {
                GroupedSection {
                    carLogs.forEachIndexed { i, log ->
                        if (i > 0) RowDivider(16.dp)
                        GroupedRow(
                            title = log.name,
                            subtitle = listOfNotNull(
                                shortDate(log.time),
                                log.odometerKm?.let { formatKmWhole(it) },
                                log.note,
                            ).joinToString(" · "),
                            value = log.cost?.let(::formatMoney),
                            trailing = {
                                Text(
                                    "Delete",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.apple.red,
                                    modifier = Modifier.clickable { vm.deleteLog(log.id) }.padding(4.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { item ->
        ItemDialog(
            item = item,
            onSave = {
                vm.saveItem(it)
                editing = null
            },
            onDelete = if (item.id != 0L) {
                {
                    vm.deleteItem(item.id)
                    editing = null
                }
            } else {
                null
            },
            onDismiss = { editing = null },
        )
    }
    finishing?.let { item ->
        DoneDialog(
            title = "${item.name} done",
            onSave = { cost, note ->
                vm.markDone(item, System.currentTimeMillis(), cost, note)
                finishing = null
            },
            onDismiss = { finishing = null },
        )
    }
    if (logging) {
        var name by remember { mutableStateOf("") }
        DoneDialog(
            title = "Log work",
            name = name,
            onName = { name = it },
            onSave = { cost, note ->
                if (name.isNotBlank()) vm.addLog(g.car.id, name.trim(), System.currentTimeMillis(), cost, note)
                logging = false
            },
            onDismiss = { logging = false },
        )
    }
}

@Composable
private fun MaintenanceCard(s: Maintenance.Status, onDone: () -> Unit, onEdit: () -> Unit) {
    val color = s.state.color()
    GlassCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(s.progress ?: 0.0, color, size = 54.dp, thickness = 6.dp) {
                Text(
                    s.progress?.let { "${(it * 100).roundToInt().coerceAtMost(999)}%" } ?: "–",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.item.name, style = MaterialTheme.typography.titleMedium)
                Text(dueText(s), style = MaterialTheme.typography.bodySmall, color = color)
                Text(
                    listOfNotNull(
                        s.item.intervalKm?.let { "every ${formatKmWhole(it.toDouble())}" },
                        s.item.intervalMonths?.let { if (it == 1) "every month" else "every $it months" },
                    ).joinToString(" or ") + " · last ${shortDate(s.item.lastDoneAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TintedButton("Done", onClick = onDone, icon = Icons.Rounded.Check, color = color)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ItemDialog(item: MaintenanceItem, onSave: (MaintenanceItem) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var km by remember { mutableStateOf(item.intervalKm?.toString().orEmpty()) }
    var months by remember { mutableStateOf(item.intervalMonths?.toString().orEmpty()) }
    var kind by remember { mutableStateOf(MaintenanceKind.of(item.kind)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.id == 0L) "New job" else "Edit job") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.id == 0L) {
                    Text("Pick one or type your own", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MaintenanceKind.entries.filter { it != MaintenanceKind.OTHER }.forEach { k ->
                            androidx.compose.material3.FilterChip(
                                selected = kind == k,
                                onClick = {
                                    kind = k
                                    name = k.label
                                    km = k.defaultKm?.toString().orEmpty()
                                    months = k.defaultMonths?.toString().orEmpty()
                                },
                                label = { Text(k.label) },
                            )
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    km,
                    { km = it.filter(Char::isDigit).take(6) },
                    label = { Text("Every (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    months,
                    { months = it.filter(Char::isDigit).take(3) },
                    label = { Text("Or every (months)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        androidx.compose.material3.Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.apple.red)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete this job", color = MaterialTheme.apple.red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        item.copy(
                            name = name.trim(),
                            kind = kind.name,
                            intervalKm = km.toIntOrNull()?.takeIf { it > 0 },
                            intervalMonths = months.toIntOrNull()?.takeIf { it > 0 },
                            remindedAt = null,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DoneDialog(
    title: String,
    onSave: (Double?, String?) -> Unit,
    onDismiss: () -> Unit,
    name: String? = null,
    onName: ((String) -> Unit)? = null,
) {
    var cost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (name != null && onName != null) {
                    OutlinedTextField(name, onName, label = { Text("What was done") }, singleLine = true)
                }
                OutlinedTextField(
                    cost,
                    { cost = it },
                    label = { Text("Cost (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                Text(
                    "Logged for today at the current odometer reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(parseNumber(cost), note.trim().ifBlank { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
