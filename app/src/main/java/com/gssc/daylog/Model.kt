package com.gssc.daylog

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS reading. */
data class Fix(val t: Long, val lat: Double, val lng: Double, val acc: Int)

/** A place the user actually stayed. */
/** A place you told Daylog about. Anything within [radius] metres is this site. */
data class Site(
    val id: Long,
    var name: String,
    var lat: Double,
    var lng: Double,
    var radius: Double = 150.0,
    /** False until you have stood there once and pinned it to a coordinate. */
    var pinned: Boolean = true
)

data class Stop(
    var lat: Double,
    var lng: Double,
    var start: Long,
    var end: Long,
    var acc: Int,
    var n: Int,
    var km: Double = 0.0,
    var name: String = "",
    var fromIdx: Int = 0,
    var toIdx: Int = 0
)

object Fmt {
    fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(t))
    fun dayKey(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))
    fun today(): String = dayKey(System.currentTimeMillis())

    fun dur(ms: Long): String {
        val m = (ms / 60000L).toInt()
        return if (m < 60) "${m}m" else String.format(Locale.US, "%dh %02dm", m / 60, m % 60)
    }
}

object Geo {
    const val CLUSTER_M = 150.0       // radius of one stop
    const val MIN_MS = 3 * 60 * 1000L // 3 minutes standing still = a stop
    const val MIN_FIX = 5

    fun haversine(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = Math.PI / 180.0
        val dLat = (bLat - aLat) * r
        val dLng = (bLng - aLng) * r
        val s = sin(dLat / 2).pow(2.0) +
                cos(aLat * r) * cos(bLat * r) * sin(dLng / 2).pow(2.0)
        return 2.0 * 6371000.0 * asin(sqrt(s))
    }

    fun key(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.3f,%.3f", lat, lng)

    /**
     * Anchor stays fixed for the life of a cluster. Comparing against a drifting
     * mean lets a slow walk merge into one giant "stop".
     */
    fun buildStops(fixes: List<Fix>, namer: (Double, Double) -> String): List<Stop> {
        if (fixes.isEmpty()) return emptyList()

        // Group readings into circles. The anchor is the first reading of the
        // circle and never moves, so a slow walk cannot drag one stop across
        // the whole site.
        val ranges = ArrayList<IntArray>()
        var anchor = fixes[0]
        var startIdx = 0
        for (i in 1 until fixes.size) {
            val f = fixes[i]
            if (haversine(anchor.lat, anchor.lng, f.lat, f.lng) > CLUSTER_M) {
                ranges.add(intArrayOf(startIdx, i - 1))
                anchor = f
                startIdx = i
            }
        }
        ranges.add(intArrayOf(startIdx, fixes.size - 1))

        val stops = ArrayList<Stop>()
        for (r in ranges) {
            val a = r[0]
            val b = r[1]
            val held = fixes[b].t - fixes[a].t
            val count = b - a + 1
            if (held < MIN_MS && count < MIN_FIX) continue

            var sumLat = 0.0
            var sumLng = 0.0
            var bestAcc = Int.MAX_VALUE
            for (i in a..b) {
                sumLat += fixes[i].lat
                sumLng += fixes[i].lng
                if (fixes[i].acc < bestAcc) bestAcc = fixes[i].acc
            }
            stops.add(
                Stop(
                    lat = sumLat / count,
                    lng = sumLng / count,
                    start = fixes[a].t,
                    end = fixes[b].t,
                    acc = bestAcc,
                    n = count,
                    fromIdx = a,
                    toIdx = b
                )
            )
        }

        for (i in stops.indices) {
            val s = stops[i]
            // Distance actually driven to get here: the GPS track between
            // leaving the last stop and arriving at this one, not a straight line.
            val from = if (i == 0) 0 else stops[i - 1].toIdx
            s.km = trackKm(fixes, from, s.fromIdx)
            s.name = namer(s.lat, s.lng)
        }
        return stops
    }

    /** Adds up every step of the recorded track between two readings. */
    fun trackKm(fixes: List<Fix>, from: Int, to: Int): Double {
        var km = 0.0
        for (i in from + 1..to) {
            val d = haversine(
                fixes[i - 1].lat, fixes[i - 1].lng, fixes[i].lat, fixes[i].lng
            ) / 1000.0
            if (d < 5.0) km += d   // a single bad fix cannot inflate the total
        }
        return km
    }

    fun dayKm(fixes: List<Fix>): Double {
        var km = 0.0
        for (i in 1 until fixes.size) {
            val d = haversine(
                fixes[i - 1].lat, fixes[i - 1].lng, fixes[i].lat, fixes[i].lng
            ) / 1000.0
            if (d < 5.0) km += d   // drop single bad-fix jumps
        }
        return km
    }
}
