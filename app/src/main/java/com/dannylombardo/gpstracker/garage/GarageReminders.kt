package com.dannylombardo.gpstracker.garage

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dannylombardo.gpstracker.R
import com.dannylombardo.gpstracker.data.AppDatabase
import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.tracking.Permissions
import com.dannylombardo.gpstracker.ui.MainActivity
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The garage's nudges: maintenance coming due, a tank running low, and the fuel budget
 * filling up. Each is sent once per occasion and can be turned off in the Garage.
 */
object GarageReminders {
    const val CHANNEL = "garage"
    private const val ID_LOW_FUEL = 7_100
    private const val ID_BUDGET = 7_200
    private const val ID_MAINTENANCE_BASE = 7_300

    /** How few kilometres left counts as low. */
    const val LOW_RANGE_KM = 80.0

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Garage reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Maintenance coming due, low fuel and the monthly fuel budget"
            },
        )
    }

    /** Runs after every drive: analyse it, then check what needs a nudge. */
    suspend fun afterDrive(context: Context, carId: Long?) {
        val repo = GarageRepository.get(context)
        runCatching { repo.analyseAll() }
        val main = AppDatabase.get(context)
        val trips = main.tripDao().observeFinishedTrips().first()
        val fuelUps = main.fuelUpDao().observeAll().first()
        checkMaintenance(context, trips)
        if (carId != null) checkLowFuel(context, carId, trips, fuelUps)
        checkBudget(context, fuelUps)
        GarageWidget.updateAll(context)
    }

    suspend fun checkMaintenance(context: Context, trips: List<com.dannylombardo.gpstracker.data.Trip>) {
        if (!GaragePrefs.maintenanceReminder(context)) return
        val dao = GarageDatabase.get(context).dao()
        val now = System.currentTimeMillis()
        val cars = AppDatabase.get(context).carDao().all().associate { it.id to it.name }
        for (item in dao.items()) {
            if (item.remindedAt != null) continue
            val status = Maintenance.status(item, trips, now, ZoneId.systemDefault())
            if (status.state == Maintenance.State.OK) continue
            val left = when {
                status.state == Maintenance.State.DUE -> "Due now"
                status.kmLeft != null && (status.daysLeft == null || status.kmLeft < 1_000) -> "Due in ${status.kmLeft.roundToInt()} km"
                status.daysLeft != null -> "Due in ${status.daysLeft} days"
                else -> "Due soon"
            }
            val car = cars[item.carId]?.let { " on $it" } ?: ""
            notify(context, ID_MAINTENANCE_BASE + (item.id % 500).toInt(), "${item.name}$car", "$left. Tap to log it when it's done.")
            dao.setReminded(item.id, now)
        }
    }

    private suspend fun checkLowFuel(
        context: Context,
        carId: Long,
        trips: List<com.dannylombardo.gpstracker.data.Trip>,
        fuelUps: List<FuelUp>,
    ) {
        if (!GaragePrefs.lowFuelReminder(context)) return
        val spec = GarageDatabase.get(context).dao().spec(carId) ?: return
        val carFills = fuelUps.filter { it.carId == carId }
        val lastFull = carFills.filter { it.isFullTank }.maxByOrNull { it.time } ?: return
        if (GaragePrefs.lowFuelSentFor(context) == lastFull.id) return
        val summary = FuelEconomy.summarise(carFills, trips.filter { it.carId == carId })
        val range = FuelPlan.range(spec.fuelTankLitres, summary.averageLitresPer100Km, summary.distanceSinceLastFullMeters?.div(1000)) ?: return
        if (range.kmLeft > LOW_RANGE_KM) return
        notify(
            context,
            ID_LOW_FUEL,
            "Time to fill up",
            "About ${range.kmLeft.roundToInt()} km left in the tank, going by your usual consumption.",
        )
        GaragePrefs.setLowFuelSentFor(context, lastFull.id)
    }

    /** Nudges once at 80% of the month's fuel budget and once when it's used up. */
    fun checkBudget(context: Context, fuelUps: List<FuelUp>) {
        if (!GaragePrefs.budgetAlert(context)) return
        val amount = GaragePrefs.monthlyBudget(context) ?: return
        val budget = FuelPlan.budget(fuelUps, amount, LocalDate.now(), ZoneId.systemDefault())
        val month = YearMonth.now().toString()
        val sent = GaragePrefs.budgetAlertSent(context)
        val money = NumberFormat.getCurrencyInstance()
        val level = when {
            budget.progress >= 1.0 -> 100
            budget.progress >= 0.8 -> 80
            else -> return
        }
        val mark = "$month:$level"
        if (sent == mark || (level == 80 && sent == "$month:100")) return
        if (level == 100) {
            notify(context, ID_BUDGET, "Fuel budget used up", "You've spent ${money.format(budget.spent)} of ${money.format(amount)} this month.")
        } else {
            notify(context, ID_BUDGET, "80% of your fuel budget", "${money.format(budget.left)} left for the next ${budget.daysLeft} days.")
        }
        GaragePrefs.setBudgetAlertSent(context, mark)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (!Permissions.hasNotifications(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).putExtra(GarageIntents.EXTRA_OPEN, GarageIntents.GARAGE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
