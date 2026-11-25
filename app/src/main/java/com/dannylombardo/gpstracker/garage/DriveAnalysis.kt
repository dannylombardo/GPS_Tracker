package com.dannylombardo.gpstracker.garage

import com.dannylombardo.gpstracker.data.RoutePoint
import com.dannylombardo.gpstracker.data.Trip
import java.time.Instant
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Works out a drive's score and performance numbers from its GPS trace.
 *
 * GPS gives a speed every 2 to 5 seconds, so everything here is an estimate: short
 * hard stabs on the pedal get smoothed out, and 0-100 km/h times read a little slow
 * rather than fast. That's fine for spotting habits and bragging rights.
 */
object DriveAnalysis {
    /** Bump when the maths changes so saved results are worked out again. */
    const val VERSION = 1

    /** Harder than this (m/s², about 0.36 g) counts as a hard brake. */
    const val HARD_BRAKE = 3.5

    /** Harder than this (about 0.31 g) counts as hard acceleration. */
    const val HARD_ACCEL = 3.0

    private const val MAX_GAP_MS = 6_000L
    private const val MAX_ACCURACY_M = 40f
    private const val KMH = 3.6

    data class Sample(val time: Long, val speed: Double)

    fun analyse(trip: Trip, points: List<RoutePoint>, zone: ZoneId): DriveInsight {
        val first = points.firstOrNull()
        val last = points.lastOrNull()
        val samples = samples(points)
        var brakes = 0
        var accels = 0
        var inBrake = false
        var inAccel = false
        var over120 = 0L
        var night = 0L
        var peakAccel: Double? = null
        var peakBrake: Double? = null
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            val dt = b.time - a.time
            if (dt <= 0) continue
            val midHour = Instant.ofEpochMilli(a.time + dt / 2).atZone(zone).hour
            val counted = dt.coerceAtMost(10_000L)
            if (midHour >= 22 || midHour < 5) night += counted
            if ((a.speed + b.speed) / 2 * KMH > 120) over120 += counted
            if (dt > MAX_GAP_MS) {
                inBrake = false
                inAccel = false
                continue
            }
            val accel = (b.speed - a.speed) / (dt / 1000.0)
            if (accel > 0) peakAccel = maxOf(peakAccel ?: 0.0, accel)
            if (accel < 0) peakBrake = maxOf(peakBrake ?: 0.0, -accel)
            if (accel <= -HARD_BRAKE) {
                if (!inBrake) brakes++
                inBrake = true
            } else {
                inBrake = false
            }
            if (accel >= HARD_ACCEL) {
                if (!inAccel) accels++
                inAccel = true
            } else {
                inAccel = false
            }
        }
        val duration = ((trip.endTime ?: last?.time ?: trip.startTime) - trip.startTime).coerceAtLeast(1)
        val score = if (trip.distanceMeters >= 1_000 && samples.size >= 5) {
            score(trip.distanceMeters, duration, brakes, accels, over120 / 1000, night)
        } else {
            null
        }
        return DriveInsight(
            tripId = trip.id,
            version = VERSION,
            startLat = first?.latitude,
            startLon = first?.longitude,
            endLat = last?.latitude,
            endLon = last?.longitude,
            score = score,
            hardBrakes = brakes,
            hardAccels = accels,
            secondsOver120 = over120 / 1000,
            nightMillis = night,
            best0to100Millis = bestRun(samples, 0.0, 100.0),
            best80to120Millis = bestRun(samples, 80.0, 120.0),
            peakAccelMs2 = peakAccel,
            peakBrakeMs2 = peakBrake,
        )
    }

    /**
     * 0 to 100: smooth and steady is 100. Points come off for hard brakes and launches
     * (per 100 km, so long drives aren't punished for being long), for time spent over
     * 120 km/h, and a little for night driving.
     */
    fun score(distanceMeters: Double, durationMillis: Long, hardBrakes: Int, hardAccels: Int, secondsOver120: Long, nightMillis: Long): Int {
        val km = maxOf(distanceMeters / 1000, 1.0)
        val eventsPer100Km = (hardBrakes * 1.3 + hardAccels) / km * 100
        val eventPenalty = minOf(40.0, eventsPer100Km * 1.2)
        val fastShare = (secondsOver120 * 1000.0 / durationMillis).coerceIn(0.0, 1.0)
        val fastPenalty = minOf(35.0, fastShare * 150)
        val nightShare = (nightMillis.toDouble() / durationMillis).coerceIn(0.0, 1.0)
        val nightPenalty = nightShare * 8
        return (100 - eventPenalty - fastPenalty - nightPenalty).roundToInt().coerceIn(0, 100)
    }

    /** Speeds in m/s, from the GPS where it gave one, otherwise from the distance between fixes. */
    fun samples(points: List<RoutePoint>): List<Sample> {
        val good = points.filter { (it.accuracyMeters ?: 0f) <= MAX_ACCURACY_M }.sortedBy { it.time }
        val out = ArrayList<Sample>(good.size)
        for (i in good.indices) {
            val p = good[i]
            val gps = p.speedMetersPerSecond?.toDouble()
            val speed = gps ?: if (i > 0) {
                val prev = good[i - 1]
                val dt = (p.time - prev.time) / 1000.0
                if (dt > 0) metersBetween(prev.latitude, prev.longitude, p.latitude, p.longitude) / dt else null
            } else {
                null
            }
            // Over 400 km/h is a GPS glitch, not a drive.
            if (speed != null && speed < 111) out += Sample(p.time, speed)
        }
        return out
    }

    /**
     * The quickest the speed went from [fromKmh] to [toKmh] without easing off, in
     * milliseconds, or null if it never did. From 0 means from a stop (under 5 km/h).
     */
    fun bestRun(samples: List<Sample>, fromKmh: Double, toKmh: Double): Long? {
        val startBelow = if (fromKmh <= 0.0) 5.0 else fromKmh
        var best: Long? = null
        for (i in 1 until samples.size) {
            val hit = samples[i].speed * KMH
            val before = samples[i - 1].speed * KMH
            if (hit < toKmh || before >= toKmh) continue
            if (samples[i].time - samples[i - 1].time > MAX_GAP_MS) continue
            val reachedAt = crossing(samples[i - 1], samples[i], toKmh)
            // Walk back while the speed keeps dropping (allowing a little GPS wobble).
            var j = i - 1
            var startAt: Double? = null
            while (j >= 0) {
                val v = samples[j].speed * KMH
                if (v <= startBelow) {
                    startAt = if (fromKmh <= 0.0) samples[j].time.toDouble() else crossing(samples[j], samples[j + 1], fromKmh)
                    break
                }
                if (j == 0) break
                val prev = samples[j - 1].speed * KMH
                if (prev > v + 4) break
                if (samples[j].time - samples[j - 1].time > MAX_GAP_MS) break
                if (reachedAt - samples[j - 1].time > 40_000) break
                j--
            }
            val start = startAt ?: continue
            val millis = (reachedAt - start).roundToLong()
            // Quicker than 2.5 s is a GPS jump, not a launch.
            if (millis in 2_500..40_000 && (best == null || millis < best)) best = millis
        }
        return best
    }

    private fun crossing(a: Sample, b: Sample, kmh: Double): Double {
        val va = a.speed * KMH
        val vb = b.speed * KMH
        if (vb == va) return b.time.toDouble()
        val f = ((kmh - va) / (vb - va)).coerceIn(0.0, 1.0)
        return a.time + f * (b.time - a.time)
    }

    fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
