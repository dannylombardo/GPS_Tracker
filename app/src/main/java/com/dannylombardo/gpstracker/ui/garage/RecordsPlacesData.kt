package com.dannylombardo.gpstracker.ui.garage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.garage.PdfReport
import com.dannylombardo.gpstracker.garage.PlaceKind
import com.dannylombardo.gpstracker.garage.Places
import com.dannylombardo.gpstracker.garage.Records
import com.dannylombardo.gpstracker.ui.GlassCard
import com.dannylombardo.gpstracker.ui.GroupedRow
import com.dannylombardo.gpstracker.ui.GroupedSection
import com.dannylombardo.gpstracker.ui.RowDivider
import com.dannylombardo.gpstracker.ui.SectionTitle
import com.dannylombardo.gpstracker.ui.SegmentedControl
import com.dannylombardo.gpstracker.ui.formatDuration
import com.dannylombardo.gpstracker.ui.formatSpeed
import com.dannylombardo.gpstracker.ui.kmNumber
import com.dannylombardo.gpstracker.ui.theme.apple
import java.time.LocalDate
import java.time.YearMonth

/** Personal bests and badges. */
@Composable
internal fun RecordsScreen(vm: GarageViewModel, onBack: () -> Unit, onOpenTrip: (Long) -> Unit) {
    val bests by vm.bests.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val b = bests
    GarageList(title = "Hall of fame", subtitle = "Records and badges", onBack = onBack) {
        if (b != null) {
            item {
                GlassCard {
                    Row {
                        Figure("Total", "${kmNumber(b.totalMeters)} km", Modifier.weight(1f))
                        Figure("Drives", b.driveCount.toString(), Modifier.weight(1f))
                        Figure("Seat time", formatDuration(b.totalMillis, zero = "0 min"), Modifier.weight(1f))
                    }
                }
            }
            item {
                GroupedSection(
                    header = "Personal bests",
                    footer = "0-100 and 80-120 times are estimated from GPS speed every few seconds, so treat them as ballpark. Only your own drives count.",
                ) {
                    RecordRow(Icons.Rounded.Speed, MaterialTheme.apple.red, "Top speed", b.topSpeed?.let { formatSpeed(it.value) }, b.topSpeed, onOpenTrip)
                    RowDivider()
                    RecordRow(Icons.Rounded.Timer, MaterialTheme.colorScheme.primary, "0-100 km/h", b.best0to100?.let { formatSeconds(it.value) }, b.best0to100, onOpenTrip)
                    RowDivider()
                    RecordRow(Icons.Rounded.Timer, MaterialTheme.apple.indigo, "80-120 km/h", b.best80to120?.let { formatSeconds(it.value) }, b.best80to120, onOpenTrip)
                    RowDivider()
                    RecordRow(Icons.Rounded.Route, MaterialTheme.apple.blue, "Longest drive", b.longestDrive?.let { "${kmNumber(it.value)} km" }, b.longestDrive, onOpenTrip)
                    RowDivider()
                    RecordRow(Icons.Rounded.DirectionsCar, MaterialTheme.apple.teal, "Longest time", b.longestTime?.let { formatDuration(it.value) }, b.longestTime, onOpenTrip)
                    RowDivider()
                    RecordRow(Icons.Rounded.LocalFireDepartment, MaterialTheme.apple.orange, "Most in a day", b.mostKmInADay?.let { "${kmNumber(it.value)} km" }, b.mostKmInADay, onOpenTrip)
                    RowDivider()
                    RecordRow(Icons.Rounded.Eco, MaterialTheme.apple.green, "Smoothest drive", b.bestScore?.let { "${it.value} / 100" }, b.bestScore, onOpenTrip)
                    RowDivider()
                    GroupedRow(
                        title = "Daily streak",
                        icon = Icons.Rounded.Flag,
                        iconColor = MaterialTheme.apple.yellow,
                        subtitle = "Best ever: ${b.longestStreakDays} days",
                        value = "${b.currentStreakDays} days",
                    )
                }
            }
        }
        item { SectionTitle("Badges", trailing = "${badges.count { it.unlocked }} of ${badges.size}") }
        items(badges.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { BadgeCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecordRow(
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    title: String,
    value: String?,
    record: Records.Record<*>?,
    onOpenTrip: (Long) -> Unit,
) {
    GroupedRow(
        title = title,
        icon = icon,
        iconColor = color,
        subtitle = record?.let { shortDate(it.time) } ?: "Not yet",
        value = value ?: "–",
        showChevron = record?.tripId != null,
        onClick = record?.tripId?.let { id -> { onOpenTrip(id) } },
    )
}

@Composable
private fun BadgeCard(badge: Records.Badge, modifier: Modifier) {
    val color = when (badge.icon) {
        Records.BadgeIcon.SPEED, Records.BadgeIcon.FIRE -> MaterialTheme.apple.red
        Records.BadgeIcon.MOON -> MaterialTheme.apple.indigo
        Records.BadgeIcon.SUN, Records.BadgeIcon.TROPHY -> MaterialTheme.apple.yellow
        Records.BadgeIcon.LEAF -> MaterialTheme.apple.green
        Records.BadgeIcon.FUEL, Records.BadgeIcon.WRENCH -> MaterialTheme.apple.orange
        Records.BadgeIcon.GLOBE, Records.BadgeIcon.TIMER -> MaterialTheme.apple.teal
        else -> MaterialTheme.apple.blue
    }
    val icon = when (badge.icon) {
        Records.BadgeIcon.FLAG -> Icons.Rounded.Flag
        Records.BadgeIcon.ROAD -> Icons.Rounded.Route
        Records.BadgeIcon.SPEED -> Icons.Rounded.Speed
        Records.BadgeIcon.MOON -> Icons.Rounded.Bedtime
        Records.BadgeIcon.SUN -> Icons.Rounded.WbSunny
        Records.BadgeIcon.LEAF -> Icons.Rounded.Eco
        Records.BadgeIcon.FIRE -> Icons.Rounded.LocalFireDepartment
        Records.BadgeIcon.FUEL -> Icons.Rounded.LocalGasStation
        Records.BadgeIcon.WRENCH -> Icons.Rounded.Build
        Records.BadgeIcon.TROPHY -> Icons.Rounded.EmojiEvents
        Records.BadgeIcon.TIMER -> Icons.Rounded.Timer
        Records.BadgeIcon.GLOBE -> Icons.Rounded.Public
    }
    GlassCard(modifier = modifier) {
        Column(Modifier.alpha(if (badge.unlocked) 1f else 0.55f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressRing(badge.progress, color, size = 54.dp, thickness = 5.dp) {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(2.dp))
            }
            Text(badge.title, style = MaterialTheme.typography.titleSmall)
            Text(badge.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!badge.unlocked) {
                Text("${(badge.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

/** Regular spots, worked out from where drives start and end, and the commute between home and work. */
@Composable
internal fun PlacesScreen(vm: GarageViewModel, onBack: () -> Unit) {
    val places by vm.places.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf<Places.Place?>(null) }
    val p = places
    GarageList(title = "Places", subtitle = "Where you go most", onBack = onBack) {
        if (p == null || p.places.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        "Places show up once you've started or ended at least ${Places.MIN_VISITS} drives in the same spot. " +
                            "They're worked out on your phone from your drives; nothing is looked up online.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@GarageList
        }
        p.toWork?.let { c -> item { CommuteCard("To work", p.home?.name ?: "Home", p.work?.name ?: "Work", c) } }
        p.toHome?.let { c -> item { CommuteCard("Back home", p.work?.name ?: "Work", p.home?.name ?: "Home", c) } }
        if (p.toWork == null && p.toHome == null) {
            item {
                GlassCard {
                    Text(
                        "Mark one spot as Home and one as Work (tap a place) to see commute times.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SectionTitle("Regular spots", trailing = "${p.places.size}") }
        item {
            GroupedSection(footer = "Tap a place to name it. The app guesses Home and Work from when you leave and arrive.") {
                p.places.forEachIndexed { i, place ->
                    if (i > 0) RowDivider()
                    GroupedRow(
                        title = place.name,
                        icon = when (place.kind) {
                            PlaceKind.HOME -> Icons.Rounded.Home
                            PlaceKind.WORK -> Icons.Rounded.Work
                            PlaceKind.OTHER -> Icons.Rounded.Place
                        },
                        iconColor = when (place.kind) {
                            PlaceKind.HOME -> MaterialTheme.apple.green
                            PlaceKind.WORK -> MaterialTheme.apple.blue
                            PlaceKind.OTHER -> MaterialTheme.apple.teal
                        },
                        subtitle = "${place.visits} visits · last ${shortDate(place.lastVisit)}",
                        showChevron = true,
                        onClick = { naming = place },
                    )
                }
            }
        }
    }
    naming?.let { place ->
        var name by remember(place) { mutableStateOf(if (place.savedId != null) place.name else if (place.kind == PlaceKind.OTHER) "" else place.name) }
        var kind by remember(place) { mutableStateOf(place.kind) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("Name this place") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SegmentedControl(
                        options = listOf("Home", "Work", "Other"),
                        selected = kind.ordinal,
                        onSelect = {
                            kind = PlaceKind.entries[it]
                            if (name.isBlank() || name == "Home" || name == "Work") {
                                name = when (kind) {
                                    PlaceKind.HOME -> "Home"
                                    PlaceKind.WORK -> "Work"
                                    PlaceKind.OTHER -> ""
                                }
                            }
                        },
                    )
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Text(
                        String.format(java.util.Locale.US, "%.4f, %.4f", place.latitude, place.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.namePlace(place, name.trim(), kind)
                    naming = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (place.savedId != null) {
                        TextButton(onClick = {
                            vm.forgetPlaceName(place)
                            naming = null
                        }) { Text("Forget name", color = MaterialTheme.apple.red) }
                    }
                    TextButton(onClick = { naming = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun CommuteCard(title: String, from: String, to: String, c: Places.Commute) {
    GlassCard {
        GaugeLabel(title)
        Text("$from → $to", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row {
            Figure("Average", formatDuration(c.averageMillis), Modifier.weight(1f))
            Figure("Fastest", formatDuration(c.fastestMillis), Modifier.weight(1f), color = MaterialTheme.apple.green)
            Figure("Slowest", formatDuration(c.slowestMillis), Modifier.weight(1f), color = MaterialTheme.apple.red)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            buildString {
                append("${c.trips} trips, ${kmNumber(c.averageMeters)} km each.")
                if (c.bestDepartureHour != null && c.bestDepartureMillis != null) {
                    append(" Leaving around ${c.bestDepartureHour}:00 is quickest (${formatDuration(c.bestDepartureMillis)}).")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** CSV and PDF exports, and backup and restore to a file. */
@Composable
internal fun DataScreen(vm: GarageViewModel, onBack: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var pdfRange by remember { mutableStateOf(0) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val today = LocalDate.now()
    val ranges = listOf(
        PdfReport.Range("This month", YearMonth.now().atDay(1), today.plusDays(1)),
        PdfReport.Range("Last month", YearMonth.now().minusMonths(1).atDay(1), YearMonth.now().atDay(1)),
        PdfReport.Range("This year", LocalDate.of(today.year, 1, 1), today.plusDays(1)),
        PdfReport.Range("Last year", LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year, 1, 1)),
    )
    val csv = "text/csv"
    val drives = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(csv)) { uri -> uri?.let { vm.exportCsv(GarageViewModel.CsvKind.DRIVES, it) } }
    val fills = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(csv)) { uri -> uri?.let { vm.exportCsv(GarageViewModel.CsvKind.FILL_UPS, it) } }
    val jobs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(csv)) { uri -> uri?.let { vm.exportCsv(GarageViewModel.CsvKind.MAINTENANCE, it) } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { vm.exportPdf(ranges[pdfRange], it) } }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::backup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { confirmRestore = it } }
    val stamp = today.toString()

    GarageList(title = "Your data", subtitle = "Export and backup", onBack = onBack) {
        if (busy != null) {
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("$busy…", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        item {
            GroupedSection(header = "Spreadsheets", footer = "Opens in Excel, Google Sheets or Numbers. Handy for mileage claims and expenses.") {
                GroupedRow("Drives", icon = Icons.Rounded.TableChart, iconColor = MaterialTheme.apple.blue, subtitle = "Date, car, driver, km, time, speeds, score", showChevron = true, onClick = { drives.launch("drives-$stamp.csv") })
                RowDivider()
                GroupedRow("Fill-ups", icon = Icons.Rounded.TableChart, iconColor = MaterialTheme.apple.orange, subtitle = "Litres, prices, L/100km, cost per km", showChevron = true, onClick = { fills.launch("fill-ups-$stamp.csv") })
                RowDivider()
                GroupedRow("Maintenance", icon = Icons.Rounded.TableChart, iconColor = MaterialTheme.apple.green, subtitle = "Jobs, costs and odometer readings", showChevron = true, onClick = { jobs.launch("maintenance-$stamp.csv") })
            }
        }
        item {
            GroupedSection(header = "PDF report", footer = "Totals, running cost per km, and every drive, fill-up and job in the period.") {
                Column(Modifier.padding(12.dp)) {
                    SegmentedControl(
                        options = ranges.map { it.title.replace("This ", "This ").replace("Last ", "Last ") },
                        selected = pdfRange,
                        onSelect = { pdfRange = it },
                    )
                }
                RowDivider(16.dp)
                GroupedRow(
                    "Make report",
                    icon = Icons.Rounded.PictureAsPdf,
                    iconColor = MaterialTheme.apple.red,
                    subtitle = ranges[pdfRange].title,
                    showChevron = true,
                    onClick = { pdf.launch("driving-report-${ranges[pdfRange].title.lowercase().replace(' ', '-')}-$stamp.pdf") },
                )
            }
        }
        item {
            GroupedSection(
                header = "Backup",
                footer = "One file with every drive and route, fill-up, car, spec sheet and maintenance job. Save it to Google Drive or your computer, " +
                    "then restore it on a new phone. Restoring replaces what's on this phone.",
            ) {
                GroupedRow("Back up now", icon = Icons.Rounded.Backup, iconColor = MaterialTheme.apple.indigo, showChevron = true, onClick = { backup.launch("gps-tracker-backup-$stamp.json") })
                RowDivider()
                GroupedRow("Restore from a backup", icon = Icons.Rounded.Restore, iconColor = MaterialTheme.apple.red, showChevron = true, onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) })
            }
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Replace everything?") },
            text = { Text("All drives, fill-ups, cars and garage data on this phone will be replaced with the backup. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.restore(uri)
                    confirmRestore = null
                }) { Text("Restore", color = MaterialTheme.apple.red) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}
