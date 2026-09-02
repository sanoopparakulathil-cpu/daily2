package com.gssc.daylog

import android.content.Context
import android.location.Geocoder
import java.util.Locale

/**
 * Turns coordinates into a readable place name automatically, using the address
 * book built into Android. No API key. Runs off the main thread.
 * A name the user typed themselves is never overwritten.
 */
object NameLookup {

    private val busy = HashSet<String>()

    fun fillMissing(ctx: Context, store: Store) {
        if (!Geocoder.isPresent()) return

        val stops = Geo.buildStops(store.fixes(), store.allNames())
        val known = store.allNames()

        for (s in stops) {
            val key = Geo.key(s.lat, s.lng)
            if (known.containsKey(key)) continue
            synchronized(busy) {
                if (busy.contains(key)) return@synchronized
                busy.add(key)
                Thread { lookup(ctx, store, key, s.lat, s.lng) }.start()
            }
        }
    }

    private fun lookup(ctx: Context, store: Store, key: String, lat: Double, lng: Double) {
        try {
            val g = Geocoder(ctx, Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = g.getFromLocation(lat, lng, 1)
            if (!list.isNullOrEmpty()) {
                val a = list[0]
                val parts = ArrayList<String>()
                val first = a.featureName ?: a.thoroughfare
                if (!first.isNullOrBlank() && !first.all { it.isDigit() }) parts.add(first)
                val area = a.subLocality ?: a.locality ?: a.subAdminArea
                if (!area.isNullOrBlank() && !parts.contains(area)) parts.add(area)
                val name = parts.joinToString(", ")
                if (name.isNotBlank()) store.setAuto(key, name)
            }
        } catch (e: Exception) {
            // no network or no address for this spot - leave it unnamed
        } finally {
            synchronized(busy) { busy.remove(key) }
        }
    }
}
