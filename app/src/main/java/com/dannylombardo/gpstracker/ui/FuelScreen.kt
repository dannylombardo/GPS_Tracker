package com.dannylombardo.gpstracker.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.ui.theme.HeroColors
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun FuelScreen(viewModel: MainViewModel, listState: LazyListState, padding: PaddingValues) {
    val economy by viewModel.fuelEconomy.collectAsStateWithLifecycle()
    val fuelUps by viewModel.allFuelUps.collectAsStateWithLifecycle()
    val stationSpotting by viewModel.stationSpotting.collectAsStateWithLifecycle()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Fuel", "Your real consumption, full tank to full tank") }
        item { EconomyCard(economy, fuelUps.firstOrNull()) }
        item {
            Button(onClick = { viewModel.openFuelUp() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add fill-up")
            }
        }
        item { StationSpottingCard(stationSpotting, viewModel::setStationSpotting) }

        if (fuelUps.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconBadge(
                        Icons.Rounded.LocalGasStation,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        size = 64.dp,
                    )
                    Text("No fill-ups yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Log each visit to the pump and your L/100km works itself out.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
            fuelUps.groupBy { YearMonth.from(localDateOf(it.time)) }.forEach { (month, inMonth) ->
                item(key = "month-$month") {
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionHeader(month.format(monthFormat), Modifier.weight(1f))
                        Text(
                            formatMoney(inMonth.sumOf { it.totalCost }),
                            style = MaterialTheme.typography.titleSmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
                items(inMonth, key = { "fuel-${it.id}" }) { fuelUp ->
                    FuelUpRow(fuelUp, economy.byFuelUp[fuelUp.id], onClick = { viewModel.openFuelUp(fuelUp) })
                }
            }
        }
    }
}

@Composable
private fun EconomyCard(economy: FuelEconomy.Summary, latest: FuelUp?) {
    val onHero = HeroColors.content
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(HeroColors.fuel)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fuel economy", style = MaterialTheme.typography.titleMedium, color = onHero)
            val average = economy.averageLitresPer100Km
            if (average != null) {
                BigNumber(consumptionNumber(average), "L/100km", color = onHero, style = MaterialTheme.typography.displayLarge)
                val stretches = economy.intervals.count { it.litresPer100Km != null }
                Text(
                    "Measured over $stretches full " + if (stretches == 1) "tank" else "tanks",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.75f),
                )
            } else {
                Text(
                    "Fill the tank right up twice and your real L/100km shows here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onHero,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                HeroStat(
                    "Since full tank",
                    economy.distanceSinceLastFullMeters?.let(::formatKm) ?: "–",
                    onHero,
                    Modifier.weight(1f),
                )
                HeroStat(
                    "Last price",
                    latest?.let { formatPricePerLitre(it.pricePerLitre) } ?: "–",
                    onHero,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StationSpottingCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                Icons.Rounded.NearMe,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Spot gas station stops", style = MaterialTheme.typography.titleSmall)
                Text(
                    "After a drive, checks OpenStreetMap for a gas station where you stopped and " +
                        "offers to log the fill-up. Only the stop locations are sent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FuelUpRow(fuelUp: FuelUp, litresPer100Km: Double?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                Icons.Rounded.LocalGasStation,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 44.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fuelUp.stationName ?: "Fill-up", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatDayAndDate(fuelUp.time)}, ${formatTime(fuelUp.time)}",
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
                    Pill(formatConsumption(it), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(formatMoney(fuelUp.totalCost), style = MaterialTheme.typography.titleMedium.tabular())
        }
    }
}
