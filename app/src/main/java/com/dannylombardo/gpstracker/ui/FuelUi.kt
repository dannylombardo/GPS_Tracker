package com.dannylombardo.gpstracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.util.Calendar

/** Adds a new fill-up, or edits or deletes an existing one (non-zero id). */
@Composable
internal fun FuelUpDialog(
    draft: FuelUp,
    stats: FuelEconomy.FuelUpStats?,
    cars: List<Car>,
    onSave: (FuelUp) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isNew = draft.id == 0L
    var litresText by remember(draft) { mutableStateOf(if (isNew) "" else plainNumber(draft.litres)) }
    var priceText by remember(draft) { mutableStateOf(if (isNew) "" else plainNumber(draft.pricePerLitre)) }
    var station by remember(draft) { mutableStateOf(draft.stationName.orEmpty()) }
    var fullTank by remember(draft) { mutableStateOf(draft.isFullTank) }
    var time by remember(draft) { mutableLongStateOf(draft.time) }
    var carId by remember(draft) { mutableStateOf(draft.carId) }

    val litres = parseNumber(litresText)?.takeIf { it > 0 }
    val price = parseNumber(priceText)?.takeIf { it > 0 }

    fun pickDateTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = time }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(year, month, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        time = calendar.timeInMillis
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    AndroidDateFormat.is24HourFormat(context),
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
        title = { Text(if (isNew) "Add fill-up" else "Edit fill-up") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew && stats != null) FuelUpStatsPanel(draft, stats)
                if (cars.size > 1) {
                    CarChoiceChips(cars, selectedId = carId, onSelect = { carId = it })
                }
                OutlinedTextField(
                    value = litresText,
                    onValueChange = { litresText = it },
                    label = { Text("Litres") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price per litre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (litres != null && price != null) {
                    Text("Total ${formatMoney(litres * price)}", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fullTank, onCheckedChange = { fullTank = it })
                    Column {
                        Text("Filled the tank right up", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Leave unticked for a part fill",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = station,
                    onValueChange = { station = it },
                    label = { Text("Station (optional)") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatDate(time)}, ${formatTime(time)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickDateTime() }) { Text("Change") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = litres != null && price != null,
                onClick = {
                    if (litres != null && price != null) {
                        onSave(
                            draft.copy(
                                time = time,
                                litres = litres,
                                pricePerLitre = price,
                                isFullTank = fullTank,
                                stationName = station.trim().ifEmpty { null },
                                carId = carId,
                            ),
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** What this fill-up tells you, above the form when you open one you've already logged. */
@Composable
private fun FuelUpStatsPanel(fuelUp: FuelUp, stats: FuelEconomy.FuelUpStats) {
    val since = stats.distanceSincePreviousMeters
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (since == null) {
                Text(
                    "The first fill-up for this car, so there's nothing to measure from yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                return@Column
            }
            Row {
                PanelStat("Since last fill-up", formatKm(since), Modifier.weight(1f))
                PanelStat("Time since", stats.millisSincePrevious?.let { formatGap(it.toDouble()) } ?: "–", Modifier.weight(1f))
            }
            val interval = stats.interval
            if (interval != null && interval.distanceMeters > 0) {
                Row {
                    PanelStat("This tank", interval.litresPer100Km?.let(::formatConsumption) ?: "–", Modifier.weight(1f))
                    PanelStat("Cost per km", interval.costPerKm?.let(::formatCostPerKm) ?: "–", Modifier.weight(1f))
                }
                if (interval.distanceMeters != since) {
                    Text(
                        "Tank measured over ${formatKm(interval.distanceMeters)}, counting the part fills since your last full tank.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                    )
                }
            } else {
                Text(
                    if (fuelUp.isFullTank) "L/100km and cost per km show once there's a full tank before this one."
                    else "A part fill: its litres count towards the next full tank's L/100km.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun PanelStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium.tabular(), color = MaterialTheme.colorScheme.onTertiaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
    }
}

/** Accepts either a comma or a dot as the decimal separator. */
private fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

private fun plainNumber(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()
