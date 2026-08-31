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
data class Stop(
    var lat: Double,
    var lng: Double,
    var start: Long,
    var end: Long,
    var acc: Int,
    var n: Int,
    var km: Double = 0.0,
    var name: String = ""
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
    const val CLUSTER_M = 70.0        // radius of one stop
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
    fun buildStops(fixes: List<Fix>, places: Map<String, String>): List<Stop> {
        if (fixes.isEmpty()) return emptyList()

        val clusters = ArrayList<MutableList<Fix>>()
        var anchor: Fix? = null
        for (f in fixes) {
            val a = anchor
            if (a == null || haversine(a.lat, a.lng, f.lat, f.lng) > CLUSTER_M) {
                clusters.add(arrayListOf(f))
                anchor = f
            } else {
                clusters[clusters.size - 1].add(f)
            }
        }

        val stops = ArrayList<Stop>()
        for (c in clusters) {
            val held = c[c.size - 1].t - c[0].t
            if (held < MIN_MS && c.size < MIN_FIX) continue
            stops.add(
                Stop(
                    lat = c.sumOf { it.lat } / c.size,
                    lng = c.sumOf { it.lng } / c.size,
                    start = c[0].t,
                    end = c[c.size - 1].t,
                    acc = c.minOf { it.acc },
                    n = c.size
                )
            )
        }

        for (i in stops.indices) {
            val s = stops[i]
            s.km = if (i == 0) 0.0
            else haversine(stops[i - 1].lat, stops[i - 1].lng, s.lat, s.lng) / 1000.0
            s.name = places[key(s.lat, s.lng)] ?: ""
        }
        return stops
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
