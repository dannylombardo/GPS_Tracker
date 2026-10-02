package com.dannylombardo.gpstracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun FuelCard(
    economy: FuelEconomy.Summary,
    stationSpotting: Boolean,
    onAddFuelUp: () -> Unit,
    onStationSpottingChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fuel economy", style = MaterialTheme.typography.titleMedium)
            val average = economy.averageLitresPer100Km
            if (average != null) {
                Text(
                    formatConsumption(average),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                val stretches = economy.intervals.count { it.litresPer100Km != null }
                Text(
                    "Measured full tank to full tank over $stretches " +
                        if (stretches == 1) "fill-up" else "fill-ups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                Text(
                    "Fill the tank right up twice and your real L/100km shows here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            economy.distanceSinceLastFullMeters?.let { meters ->
                Text(
                    "${formatKm(meters)} driven since your last full tank",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onAddFuelUp) { Text("Add fill-up") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Spot gas station stops", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "After a drive, checks OpenStreetMap for a gas station where you stopped and " +
                            "offers to log the fill-up. Only the stop locations are sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = stationSpotting, onCheckedChange = onStationSpottingChange)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FuelUpRow(fuelUp: FuelUp, litresPer100Km: Double?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fuelUp.stationName ?: "Fill-up", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatDate(fuelUp.time)}, ${formatTime(fuelUp.time)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatLitres(fuelUp.litres)} at ${formatPricePerLitre(fuelUp.pricePerLitre)}" +
                        if (fuelUp.isFullTank) "" else " · part fill",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                litresPer100Km?.let {
                    Text(
                        "${formatConsumption(it)} since the previous full tank",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(formatMoney(fuelUp.totalCost), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Adds a new fill-up, or edits or deletes an existing one (non-zero id). */
@Composable
internal fun FuelUpDialog(
    draft: FuelUp,
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
        title = { Text(if (isNew) "Add fill-up" else "Edit fill-up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** Accepts either a comma or a dot as the decimal separator. */
private fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

private fun plainNumber(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()

internal fun formatMoney(amount: Double): String = NumberFormat.getCurrencyInstance().format(amount)

private fun formatPricePerLitre(price: Double): String =
    NumberFormat.getCurrencyInstance().apply { maximumFractionDigits = 3 }.format(price) + "/L"

internal fun formatLitres(litres: Double): String = String.format(Locale.getDefault(), "%.1f L", litres)

private fun formatConsumption(litresPer100Km: Double): String =
    String.format(Locale.getDefault(), "%.1f L/100km", litresPer100Km)
