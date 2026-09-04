package com.gssc.daylog

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale

/**
 * Names a stop automatically from the phone's address book.
 * Always asks for English, and throws away Plus Codes and bare numbers,
 * which are useless in a report.
 */
object NameLookup {

    private val busy = HashSet<String>()

    // "C4G2+53G" and friends
    private val plusCode = Regex("^[A-Z0-9]{4,8}\\+[A-Z0-9]{2,4}$", RegexOption.IGNORE_CASE)

    private fun usable(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        val t = s.trim()
        if (plusCode.matches(t)) return false
        if (t.count { it.isDigit() } > t.count { it.isLetter() }) return false
        if (t.none { it.isLetter() }) return false
        // reject anything not written in the Latin alphabet, so no Arabic
        if (t.any { it.isLetter() && it.code > 0x24F }) return false
        return true
    }

    fun fillMissing(ctx: Context, store: Store) {
        if (!Geocoder.isPresent()) return
        val name = store.namer()
        val stops = Geo.buildStops(store.fixes(), name)

        for (s in stops) {
            if (s.name.isNotBlank()) continue
            val key = Geo.key(s.lat, s.lng)
            synchronized(busy) {
                if (busy.contains(key)) return@synchronized
                busy.add(key)
                Thread { lookup(ctx, store, key, s.lat, s.lng) }.start()
            }
        }
    }

    private fun lookup(ctx: Context, store: Store, key: String, lat: Double, lng: Double) {
        try {
            val g = Geocoder(ctx, Locale.ENGLISH)
            @Suppress("DEPRECATION")
            val list = g.getFromLocation(lat, lng, 1)
            if (!list.isNullOrEmpty()) {
                val name = compose(list[0])
                if (name.isNotBlank()) store.setAuto(key, name)
            }
        } catch (e: Exception) {
            // no network, or nothing on record for this spot
        } finally {
            synchronized(busy) { busy.remove(key) }
        }
    }

    private fun compose(a: Address): String {
        val parts = ArrayList<String>()
        for (c in listOf(a.featureName, a.thoroughfare, a.subLocality)) {
            if (usable(c) && !parts.contains(c!!.trim())) {
                parts.add(c.trim())
                break
            }
        }
        for (c in listOf(a.subLocality, a.locality, a.subAdminArea, a.adminArea)) {
            if (usable(c) && !parts.contains(c!!.trim())) {
                parts.add(c.trim())
                break
            }
        }
        return parts.joinToString(", ")
    }
}
