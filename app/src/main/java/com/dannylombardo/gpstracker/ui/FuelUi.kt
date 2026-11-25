package com.dannylombardo.gpstracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat as AndroidDateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.ui.theme.apple
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.util.Calendar

/**
 * The fill-up form as an iOS-style sheet that slides up over the page: Cancel on the left,
 * Save on the right, the fields in grouped rows, and Delete at the bottom for one already
 * logged. Drawn in the page rather than a dialog window so it gets the real system bar
 * and keyboard insets. Shown while [draft] isn't null.
 */
@Composable
internal fun FuelUpSheet(
    draft: FuelUp?,
    stats: FuelEconomy.FuelUpStats?,
    cars: List<Car>,
    onSave: (FuelUp) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Keeps the last form on screen while it slides away.
    var shown by remember { mutableStateOf(draft) }
    if (draft != null) shown = draft
    val visible = draft != null
    val scrim by animateFloatAsState(if (visible) 0.4f else 0f, tween(300), label = "scrim")

    if (scrim > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(380)) { it },
        exit = slideOutVertically(tween(300)) { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        val current = shown ?: return@AnimatedVisibility
        BackHandler(enabled = visible, onBack = onDismiss)
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp), contentAlignment = Alignment.BottomCenter) {
            FuelUpForm(current, stats, cars, onSave, onDelete, onDismiss)
        }
    }
}

@Composable
private fun FuelUpForm(
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
    val canSave = litres != null && price != null

    fun save() {
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
    }

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

    val sheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .shadow(24.dp, sheetShape)
            .clip(sheetShape)
            .background(MaterialTheme.apple.groupedBackground)
            // Swallows taps so they don't fall through to the scrim and close the sheet.
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding(),
    ) {
        // The grabber and the bar: Cancel, title, Save.
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(width = 36.dp, height = 5.dp)
                .background(MaterialTheme.apple.tertiaryLabel.copy(alpha = 0.6f), CircleShape)
                .align(Alignment.CenterHorizontally),
        )
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            SheetTextButton("Cancel", onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                if (isNew) "Add Fill-up" else "Fill-up",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
            SheetTextButton(
                "Save",
                onClick = ::save,
                enabled = canSave,
                bold = true,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (!isNew && stats != null) FuelUpStatsPanel(draft, stats)

            if (cars.size > 1) {
                Column {
                    Text(
                        "CAR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                    CarChoiceChips(cars, selectedId = carId, onSelect = { carId = it })
                }
            }

            GroupedSection(
                header = "Fill-up",
                footer = if (canSave) "Total ${formatMoney(litres!! * price!!)}" else null,
            ) {
                NumberRow("Litres", litresText, onChange = { litresText = it }, placeholder = "0.0", unit = "L")
                RowDivider(inset = 16.dp)
                NumberRow("Price per litre", priceText, onChange = { priceText = it }, placeholder = "0.000", unit = "/L")
                RowDivider(inset = 16.dp)
                GroupedRow(
                    "Filled the tank right up",
                    subtitle = "Turn off for a part fill",
                    trailing = { AppSwitch(checked = fullTank, onCheckedChange = { fullTank = it }) },
                )
            }

            GroupedSection(header = "Details") {
                TextRow("Station", station, onChange = { station = it }, placeholder = "Optional")
                RowDivider(inset = 16.dp)
                GroupedRow(
                    "Date",
                    value = "${formatDate(time)}, ${formatTime(time)}",
                    onClick = ::pickDateTime,
                    showChevron = true,
                )
            }

            if (!isNew) {
                GroupedSection {
                    GroupedRow(
                        "Delete Fill-up",
                        titleColor = MaterialTheme.apple.red,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

/** A word-only bar button on the sheet, like iOS's Cancel and Save. */
@Composable
private fun SheetTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = false,
) {
    Text(
        text,
        style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.35f),
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** A grouped row with its label on the left and a right-aligned number field. */
@Composable
private fun NumberRow(label: String, value: String, onChange: (String) -> Unit, placeholder: String, unit: String) {
    FieldRow(label, value, onChange, placeholder, KeyboardType.Decimal, unit)
}

@Composable
private fun TextRow(label: String, value: String, onChange: (String) -> Unit, placeholder: String) {
    FieldRow(label, value, onChange, placeholder, KeyboardType.Text, null)
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType,
    unit: String?,
) {
    val textStyle = MaterialTheme.typography.bodyLarge.tabular().copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle.copy(color = MaterialTheme.apple.tertiaryLabel))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (unit != null) {
            Text(
                unit,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** What this fill-up tells you, above the form when you open one you've already logged. */
@Composable
private fun FuelUpStatsPanel(fuelUp: FuelUp, stats: FuelEconomy.FuelUpStats) {
    val since = stats.distanceSincePreviousMeters
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
