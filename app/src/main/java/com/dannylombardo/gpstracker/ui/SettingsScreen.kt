package com.dannylombardo.gpstracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Bluetooth
import com.dannylombardo.gpstracker.tracking.Permissions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.data.DriveBin
import com.dannylombardo.gpstracker.ui.theme.ThemeMode
import com.dannylombardo.gpstracker.ui.theme.apple

/** Appearance, gas station spotting, cars and Recently deleted, in iOS Settings-style groups. */
@Composable
internal fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val stationSpotting by viewModel.stationSpotting.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val binnedTrips by viewModel.binnedTrips.collectAsStateWithLifecycle()
    val binnedFuelUps by viewModel.binnedFuelUps.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refresh()
    }
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    PageScaffold(title = "Settings", scrolled = scroll.value > 0, showTitle = scroll.value > 120, onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            LargeTitle("Settings")

            GroupedSection(
                header = "Appearance",
                footer = when (themeMode) {
                    ThemeMode.System -> "Matches your phone's light or dark setting."
                    ThemeMode.Light -> "Always light."
                    ThemeMode.Dark -> "Always dark."
                },
            ) {
                Column(Modifier.padding(12.dp)) {
                    SegmentedControl(
                        options = listOf("Automatic", "Light", "Dark"),
                        selected = themeMode.ordinal,
                        onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                        icons = listOf(Icons.Rounded.BrightnessAuto, Icons.Rounded.LightMode, Icons.Rounded.DarkMode),
                    )
                }
            }

            GroupedSection(
                header = "Tracking",
                footer = "With Nearby devices allowed, a drive starts the moment your phone connects to your car's " +
                    "Bluetooth. Without it, drives still start on their own once the phone notices you're driving.",
            ) {
                GroupedRow(
                    "Car Bluetooth",
                    icon = Icons.Rounded.Bluetooth,
                    iconColor = MaterialTheme.apple.blue,
                    subtitle = if (permissions.bluetooth) "Starts drives when your car connects" else "Nearby devices not allowed",
                    value = if (permissions.bluetooth) "On" else null,
                    trailing = if (permissions.bluetooth) {
                        null
                    } else {
                        {
                            TintedButton(
                                "Allow",
                                onClick = { askBluetooth.launch(Permissions.bluetoothPermissions) },
                                modifier = Modifier.height(36.dp),
                            )
                        }
                    },
                )
            }

            GroupedSection(
                header = "Fuel",
                footer = "After a drive, checks OpenStreetMap for a gas station where you stopped and offers " +
                    "to log the fill-up. Only the stop locations are sent.",
            ) {
                GroupedRow(
                    "Spot gas station stops",
                    icon = Icons.Rounded.NearMe,
                    iconColor = MaterialTheme.apple.teal,
                    trailing = { AppSwitch(checked = stationSpotting, onCheckedChange = viewModel::setStationSpotting) },
                )
            }

            GroupedSection(
                header = "Your data",
                footer = "Deleted drives and fill-ups wait in Recently deleted for ${DriveBin.KEEP_DAYS} days.",
            ) {
                GroupedRow(
                    "Your cars",
                    icon = Icons.Rounded.Garage,
                    iconColor = MaterialTheme.apple.blue,
                    value = cars.size.toString(),
                    showChevron = true,
                    onClick = viewModel::openCars,
                )
                RowDivider()
                GroupedRow(
                    "Recently deleted",
                    icon = Icons.Outlined.Delete,
                    iconColor = MaterialTheme.apple.red,
                    value = (binnedTrips.size + binnedFuelUps.size).takeIf { it > 0 }?.toString(),
                    showChevron = true,
                    onClick = viewModel::openBin,
                )
                RowDivider()
                val garage: com.dannylombardo.gpstracker.ui.garage.GarageViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                GroupedRow(
                    "Export & backup",
                    icon = Icons.Rounded.Backup,
                    iconColor = MaterialTheme.apple.indigo,
                    subtitle = "CSV, PDF report, backup and restore",
                    showChevron = true,
                    onClick = { garage.open(com.dannylombardo.gpstracker.ui.garage.GaragePage.Data) },
                )
            }

            GroupedSection(header = "About") {
                GroupedRow(
                    "Privacy",
                    icon = Icons.Rounded.Lock,
                    iconColor = MaterialTheme.apple.green,
                    subtitle = "Everything stays on this phone. No account, no server.",
                )
                RowDivider()
                GroupedRow(
                    "Version",
                    icon = Icons.Rounded.Info,
                    iconColor = MaterialTheme.apple.secondaryLabel,
                    value = version ?: "–",
                )
            }
        }
    }
}
