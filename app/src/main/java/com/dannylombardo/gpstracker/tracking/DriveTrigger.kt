package com.dannylombardo.gpstracker.tracking

/** What made us think a drive might be starting, which sets how long GPS may take to confirm it. */
enum class DriveTrigger(val confirmWithinMillis: Long) {
    /** Android's "entered a vehicle" transition. */
    ACTIVITY(5 * 60_000L),

    /** A single strong "in a vehicle" sensor reading: quick to arrive, so checked more briefly. */
    READING(4 * 60_000L),

    /** The car's Bluetooth connected. Allows for warming up or clearing the windows before setting off. */
    BLUETOOTH(10 * 60_000L),

    /** Started by hand, or Android restarting tracking mid-drive: no check needed. */
    MANUAL(0L),
    ;

    companion object {
        /** Sensor confidence (0 to 100) that a reading needs before GPS is switched on. */
        const val READING_MIN_CONFIDENCE = 75

        /** After GPS finds we weren't driving, readings wait this long before trying again. */
        const val FALSE_START_COOLDOWN_MS = 10 * 60_000L

        fun shouldStartFromReading(inVehicleConfidence: Int, nowMillis: Long, lastFalseStartMillis: Long): Boolean =
            inVehicleConfidence >= READING_MIN_CONFIDENCE &&
                nowMillis - lastFalseStartMillis >= FALSE_START_COOLDOWN_MS

        fun fromName(name: String?): DriveTrigger = entries.firstOrNull { it.name == name } ?: ACTIVITY
    }
}
