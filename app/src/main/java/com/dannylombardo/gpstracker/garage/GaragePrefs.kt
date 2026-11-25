package com.dannylombardo.gpstracker.garage

import android.content.Context
import androidx.core.content.edit

/** Garage settings: the fuel budget and which reminders are on. */
object GaragePrefs {
    private const val FILE = "garage"
    private const val KEY_BUDGET = "monthly_fuel_budget"
    private const val KEY_LOW_FUEL = "low_fuel_reminder"
    private const val KEY_MAINTENANCE = "maintenance_reminder"
    private const val KEY_BUDGET_ALERT = "budget_alert"
    private const val KEY_LOW_FUEL_SENT_FOR = "low_fuel_sent_for"
    private const val KEY_BUDGET_ALERT_SENT = "budget_alert_sent"
    private const val KEY_LIVE_KEEP_ON = "live_keep_screen_on"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Monthly fuel budget in your currency, or null when there isn't one. */
    fun monthlyBudget(context: Context): Double? =
        prefs(context).getFloat(KEY_BUDGET, -1f).takeIf { it > 0 }?.toDouble()

    fun setMonthlyBudget(context: Context, amount: Double?) =
        prefs(context).edit { putFloat(KEY_BUDGET, amount?.toFloat() ?: -1f) }

    fun lowFuelReminder(context: Context) = prefs(context).getBoolean(KEY_LOW_FUEL, true)

    fun setLowFuelReminder(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_LOW_FUEL, on) }

    fun maintenanceReminder(context: Context) = prefs(context).getBoolean(KEY_MAINTENANCE, true)

    fun setMaintenanceReminder(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_MAINTENANCE, on) }

    fun budgetAlert(context: Context) = prefs(context).getBoolean(KEY_BUDGET_ALERT, true)

    fun setBudgetAlert(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_BUDGET_ALERT, on) }

    fun keepScreenOnWhileLive(context: Context) = prefs(context).getBoolean(KEY_LIVE_KEEP_ON, true)

    fun setKeepScreenOnWhileLive(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_LIVE_KEEP_ON, on) }

    /** The full fill-up the last low-fuel reminder was about, so each tank only gets one. */
    fun lowFuelSentFor(context: Context): Long = prefs(context).getLong(KEY_LOW_FUEL_SENT_FOR, -1)

    fun setLowFuelSentFor(context: Context, fuelUpId: Long) = prefs(context).edit { putLong(KEY_LOW_FUEL_SENT_FOR, fuelUpId) }

    /** "2026-11:80" style marks for budget alerts already sent this month. */
    fun budgetAlertSent(context: Context): String = prefs(context).getString(KEY_BUDGET_ALERT_SENT, "") ?: ""

    fun setBudgetAlertSent(context: Context, mark: String) = prefs(context).edit { putString(KEY_BUDGET_ALERT_SENT, mark) }

    fun export(context: Context): Map<String, Any?> = buildMap {
        monthlyBudget(context)?.let { put(KEY_BUDGET, it) }
        put(KEY_LOW_FUEL, lowFuelReminder(context))
        put(KEY_MAINTENANCE, maintenanceReminder(context))
        put(KEY_BUDGET_ALERT, budgetAlert(context))
        put(KEY_LIVE_KEEP_ON, keepScreenOnWhileLive(context))
    }

    fun import(context: Context, values: Map<String, Any?>) {
        (values[KEY_BUDGET] as? Number)?.let { setMonthlyBudget(context, it.toDouble()) }
        (values[KEY_LOW_FUEL] as? Boolean)?.let { setLowFuelReminder(context, it) }
        (values[KEY_MAINTENANCE] as? Boolean)?.let { setMaintenanceReminder(context, it) }
        (values[KEY_BUDGET_ALERT] as? Boolean)?.let { setBudgetAlert(context, it) }
        (values[KEY_LIVE_KEEP_ON] as? Boolean)?.let { setKeepScreenOnWhileLive(context, it) }
        prefs(context).edit { remove(KEY_LOW_FUEL_SENT_FOR) }
    }
}
