package com.dannylombardo.gpstracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.dannylombardo.gpstracker.data.FuelUp
import java.util.Calendar

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
        icon = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
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
