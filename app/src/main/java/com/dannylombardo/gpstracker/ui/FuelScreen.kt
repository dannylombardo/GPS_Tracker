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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.ui.theme.HeroColors
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.time.YearMonth

@Composable
internal fun FuelScreen(viewModel: MainViewModel, listState: LazyListState, padding: PaddingValues) {
    val economyByCar by viewModel.economyByCar.collectAsStateWithLifecycle()
    val fuelUpStats by viewModel.fuelUpStats.collectAsStateWithLifecycle()
    val fuelUps by viewModel.fuelUps.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val viewedCarId by viewModel.viewedCarId.collectAsStateWithLifecycle()
    // With one car, "all cars" is that car.
    val shownCarId = viewedCarId ?: cars.singleOrNull()?.id
    val carNames = cars.associate { it.id to it.name }
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
        item { CarFilterRow(cars, viewedCarId, onSelect = viewModel::viewCar, onManage = viewModel::openCars) }
        item {
            if (shownCarId != null) {
                EconomyCard(
                    economyByCar[shownCarId] ?: FuelEconomy.Summary(emptyList(), null),
                    fuelUps.firstOrNull(),
                    title = if (cars.size > 1) carNames[shownCarId] ?: "Fuel economy" else "Fuel economy",
                )
            } else {
                AllCarsEconomyCard(cars, economyByCar, fuelUps)
            }
        }
        item {
            Button(onClick = { viewModel.openFuelUp() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add fill-up")
            }
        }
        val shownEconomy = shownCarId?.let { economyByCar[it] }
        if (shownEconomy != null && shownEconomy.fuelUps.isNotEmpty()) {
            item(key = "stats") { FuelStatsSection(shownEconomy) }
        }
        item { StationSpottingCard(stationSpotting, viewModel::setStationSpotting) }

        val thisMonth = YearMonth.now()
        val monthFuelUps = fuelUps.filter { YearMonth.from(localDateOf(it.time)) == thisMonth }
        item(key = "month") {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionHeader("This month", Modifier.weight(1f))
                if (monthFuelUps.isNotEmpty()) {
                    Text(
                        formatMoney(monthFuelUps.sumOf { it.totalCost }),
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        }
        if (monthFuelUps.isEmpty()) {
            item(key = "month-empty") {
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
                    Text(
                        if (fuelUps.isEmpty()) "No fill-ups yet" else "No fill-ups yet this month",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (fuelUps.isEmpty()) "Log each visit to the pump and your L/100km works itself out."
                        else "Earlier ones are under All fill-ups.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(monthFuelUps, key = { "fuel-${it.id}" }) { fuelUp ->
                SwipeToBin(onBin = { viewModel.moveFuelUpToBin(fuelUp.id) }, modifier = Modifier.animateItem()) {
                    FuelUpRow(
                        fuelUp,
                        fuelUpStats[fuelUp.id],
                        carName = fuelUp.carId?.let { carNames[it] }.takeIf { shownCarId == null },
                        onClick = { viewModel.openFuelUp(fuelUp) },
                    )
                }
            }
        }
        item(key = "see-all") {
            FilledTonalButton(onClick = viewModel::openFuelHistory, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("See all fill-ups")
            }
        }
    }
}

@Composable
private fun EconomyCard(economy: FuelEconomy.Summary, latest: FuelUp?, title: String) {
    val onHero = HeroColors.content
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(HeroColors.fuel)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = onHero)
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
            Row(Modifier.fillMaxWidth()) {
                HeroStat(
                    "Cost per km",
                    economy.averageCostPerKm?.let(::formatCostPerKm) ?: "–",
                    onHero,
                    Modifier.weight(1f),
                )
                HeroStat(
                    "Km per fill-up",
                    economy.averageDistanceBetweenMeters?.let(::formatKm) ?: "–",
                    onHero,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** Every car's own L/100km side by side; they're never mixed, since cars burn fuel differently. */
@Composable
private fun AllCarsEconomyCard(cars: List<Car>, economyByCar: Map<Long, FuelEconomy.Summary>, fuelUps: List<FuelUp>) {
    val onHero = HeroColors.content
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(HeroColors.fuel)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Fuel economy by car", style = MaterialTheme.typography.titleMedium, color = onHero)
            cars.forEach { car ->
                val economy = economyByCar[car.id]
                val average = economy?.averageLitresPer100Km
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                        Text(
                            car.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = onHero,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val details = listOfNotNull(
                            economy?.averageCostPerKm?.let(::formatCostPerKm),
                            economy?.averageDistanceBetweenMeters?.let { "${formatKm(it)} per fill-up" },
                        )
                        if (details.isNotEmpty()) {
                            Text(
                                details.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = onHero.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (average != null) {
                        BigNumber(consumptionNumber(average), "L/100km", color = onHero, style = MaterialTheme.typography.headlineLarge)
                    } else {
                        Text(
                            "Needs 2 full tanks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onHero.copy(alpha = 0.75f),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                HeroStat("Spent, all cars", formatMoney(fuelUps.sumOf { it.totalCost }), onHero, Modifier.weight(1f))
                HeroStat("Litres, all cars", formatLitres(fuelUps.sumOf { it.litres }), onHero, Modifier.weight(1f))
            }
        }
    }
}

/** The shown car's all-time fuel numbers, as a grid of tiles under the economy card. */
@Composable
private fun FuelStatsSection(economy: FuelEconomy.Summary) {
    val fuelUps = economy.fuelUps
    val accent = MaterialTheme.colorScheme.tertiary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("All-time stats")
        StatRow {
            StatTile(Icons.Rounded.Payments, "Spent on fuel", formatMoney(fuelUps.sumOf { it.totalCost }), Modifier.weight(1f), accent)
            StatTile(Icons.Rounded.WaterDrop, "Litres bought", formatLitres(fuelUps.sumOf { it.litres }), Modifier.weight(1f), accent)
        }
        StatRow {
            StatTile(
                Icons.Rounded.Sell,
                "Average price",
                economy.averagePricePerLitre?.let(::formatPricePerLitre) ?: "–",
                Modifier.weight(1f),
                accent,
            )
            StatTile(
                Icons.Rounded.Receipt,
                "Average fill-up",
                formatMoney(fuelUps.sumOf { it.totalCost } / fuelUps.size),
                Modifier.weight(1f),
                accent,
            )
        }
        StatRow {
            StatTile(
                Icons.Rounded.Event,
                "Between fill-ups",
                economy.averageMillisBetween?.let(::formatGap) ?: "–",
                Modifier.weight(1f),
                accent,
            )
            StatTile(
                Icons.Rounded.LocalGasStation,
                "Fill-ups",
                fuelUps.size.toString(),
                Modifier.weight(1f),
                accent,
            )
        }
        if (economy.intervals.count { it.litresPer100Km != null } >= 2) {
            StatRow {
                StatTile(
                    Icons.Rounded.Eco,
                    "Best tank",
                    economy.bestLitresPer100Km?.let(::formatConsumption) ?: "–",
                    Modifier.weight(1f),
                    accent,
                )
                StatTile(
                    Icons.Rounded.LocalFireDepartment,
                    "Worst tank",
                    economy.worstLitresPer100Km?.let(::formatConsumption) ?: "–",
                    Modifier.weight(1f),
                    accent,
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
internal fun FuelUpRow(fuelUp: FuelUp, stats: FuelEconomy.FuelUpStats?, carName: String? = null, onClick: (() -> Unit)?) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = colors) { FuelUpRowContent(fuelUp, stats, carName) }
    } else {
        Card(modifier = Modifier.fillMaxWidth(), colors = colors) { FuelUpRowContent(fuelUp, stats, carName) }
    }
}

@Composable
private fun FuelUpRowContent(fuelUp: FuelUp, stats: FuelEconomy.FuelUpStats?, carName: String?) {
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
                "${formatDayAndDate(fuelUp.time)}, ${formatTime(fuelUp.time)}" + (carName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatLitres(fuelUp.litres)} at ${formatPricePerLitre(fuelUp.pricePerLitre)}" +
                    if (fuelUp.isFullTank) "" else " · part fill",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stats?.distanceSincePreviousMeters?.let {
                Text(
                    "${formatKm(it)} since last fill-up",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val litresPer100Km = stats?.litresPer100Km
            val costPerKm = stats?.costPerKm
            if (litresPer100Km != null || costPerKm != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    litresPer100Km?.let {
                        Pill(formatConsumption(it), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    costPerKm?.let {
                        Pill(formatCostPerKm(it), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatMoney(fuelUp.totalCost), style = MaterialTheme.typography.titleMedium.tabular())
    }
}
