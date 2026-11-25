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
import androidx.compose.ui.graphics.Color
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
import com.dannylombardo.gpstracker.ui.theme.apple
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
        item { ScreenHeader("Fuel", "Full tank to full tank") }
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
        val shownEconomy = shownCarId?.let { economyByCar[it] }
        if (shownEconomy != null && shownEconomy.fuelUps.isNotEmpty()) {
            item(key = "stats") { FuelStatsSection(shownEconomy) }
        }

        val thisMonth = YearMonth.now()
        val monthFuelUps = fuelUps.filter { YearMonth.from(localDateOf(it.time)) == thisMonth }
        item(key = "month") {
            SectionTitle(
                "This month",
                trailing = monthFuelUps.takeIf { it.isNotEmpty() }?.let { formatMoney(it.sumOf { f -> f.totalCost }) },
                action = "See All",
                onAction = viewModel::openFuelHistory,
            )
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
                        content = MaterialTheme.colorScheme.tertiary,
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
                    PrimaryButton(
                        "Add fill-up",
                        onClick = { viewModel.openFuelUp() },
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.padding(top = 8.dp),
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
    }
}

@Composable
private fun EconomyCard(economy: FuelEconomy.Summary, latest: FuelUp?, title: String) {
    val onHero = HeroColors.content
    HeroCard(HeroColors.fuel, watermark = Icons.Rounded.LocalGasStation) {
        run {
            Text(title, style = MaterialTheme.typography.titleMedium, color = onHero)
            val average = economy.averageLitresPer100Km
            if (average != null) {
                val stretches = economy.intervals.count { it.litresPer100Km != null }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // An economy gauge: green when frugal, amber, then red when thirsty.
                    val top = gaugeMax(maxOf(average * 1.4, 15.0), 5.0, 15.0)
                    Gauge(
                        value = average.toFloat(),
                        max = top,
                        valueText = consumptionNumber(average),
                        unit = "L/100km",
                        zones = listOf(
                            GaugeZone(0f, 7f, Color(0xFF7CFFB2).copy(alpha = 0.85f)),
                            GaugeZone(7f, 11f, Color.White.copy(alpha = 0.55f)),
                            GaugeZone(11f, top, Color(0xFF5A0000).copy(alpha = 0.6f)),
                        ),
                        majorStep = top / 5,
                        content = onHero,
                        needle = onHero,
                        modifier = Modifier.weight(1.1f),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Real economy", style = MaterialTheme.typography.labelMedium, color = onHero.copy(alpha = 0.8f))
                        BigNumber(consumptionNumber(average), "L", color = onHero, style = MaterialTheme.typography.displaySmall)
                        Text(
                            "Measured over $stretches full " + if (stretches == 1) "tank" else "tanks",
                            style = MaterialTheme.typography.bodySmall,
                            color = onHero.copy(alpha = 0.75f),
                        )
                    }
                }
            } else {
                Text(
                    "Fill the tank right up twice and your real L/100km shows here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onHero,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    HeroCard(HeroColors.fuel, watermark = Icons.Rounded.LocalGasStation) {
        run {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val apple = MaterialTheme.apple
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("All-time stats")
        StatRow {
            StatTile(Icons.Rounded.Payments, "Spent on fuel", formatMoney(fuelUps.sumOf { it.totalCost }), Modifier.weight(1f), apple.green)
            StatTile(Icons.Rounded.WaterDrop, "Litres bought", formatLitres(fuelUps.sumOf { it.litres }), Modifier.weight(1f), apple.blue)
        }
        StatRow {
            StatTile(
                Icons.Rounded.Sell,
                "Average price",
                economy.averagePricePerLitre?.let(::formatPricePerLitre) ?: "–",
                Modifier.weight(1f),
                apple.green,
            )
            StatTile(
                Icons.Rounded.Receipt,
                "Average fill-up",
                formatMoney(fuelUps.sumOf { it.totalCost } / fuelUps.size),
                Modifier.weight(1f),
                apple.teal,
            )
        }
        StatRow {
            StatTile(
                Icons.Rounded.Event,
                "Between fill-ups",
                economy.averageMillisBetween?.let(::formatGap) ?: "–",
                Modifier.weight(1f),
                apple.orange,
            )
            StatTile(
                Icons.Rounded.LocalGasStation,
                "Fill-ups",
                fuelUps.size.toString(),
                Modifier.weight(1f),
                apple.indigo,
            )
        }
        if (economy.intervals.count { it.litresPer100Km != null } >= 2) {
            StatRow {
                StatTile(
                    Icons.Rounded.Eco,
                    "Best tank",
                    economy.bestLitresPer100Km?.let(::formatConsumption) ?: "–",
                    Modifier.weight(1f),
                    apple.green,
                )
                StatTile(
                    Icons.Rounded.LocalFireDepartment,
                    "Worst tank",
                    economy.worstLitresPer100Km?.let(::formatConsumption) ?: "–",
                    Modifier.weight(1f),
                    apple.red,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FuelUpRow(fuelUp: FuelUp, stats: FuelEconomy.FuelUpStats?, carName: String? = null, onClick: (() -> Unit)?) {
    GlassCard(onClick = onClick, contentPadding = PaddingValues(0.dp)) { FuelUpRowContent(fuelUp, stats, carName) }
}

@Composable
private fun FuelUpRowContent(fuelUp: FuelUp, stats: FuelEconomy.FuelUpStats?, carName: String?) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(
            Icons.Rounded.LocalGasStation,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.tertiary,
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
