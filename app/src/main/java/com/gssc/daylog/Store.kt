package com.gssc.daylog

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Everything lives on the phone in SharedPreferences as JSON. No server, no account. */
class Store(ctx: Context) {

    private val sp = ctx.applicationContext
        .getSharedPreferences("daylog", Context.MODE_PRIVATE)

    // ---- today's raw fixes ----
    fun fixes(): MutableList<Fix> {
        val out = ArrayList<Fix>()
        val a = JSONArray(sp.getString("fixes", "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(Fix(o.getLong("t"), o.getDouble("lat"), o.getDouble("lng"), o.getInt("acc")))
        }
        return out
    }

    fun saveFixes(list: List<Fix>) {
        val a = JSONArray()
        for (f in list) {
            a.put(
                JSONObject().put("t", f.t).put("lat", f.lat)
                    .put("lng", f.lng).put("acc", f.acc)
            )
        }
        sp.edit().putString("fixes", a.toString()).apply()
    }

    fun addFix(f: Fix) {
        val l = fixes()
        l.add(f)
        saveFixes(l)
    }

    // ---- saved place names ----
    fun places(): MutableMap<String, String> {
        val m = HashMap<String, String>()
        val o = JSONObject(sp.getString("places", "{}"))
        for (k in o.keys()) m[k] = o.getString(k)
        return m
    }

    fun setPlace(key: String, name: String) {
        val m = places()
        if (name.isBlank()) m.remove(key) else m[key] = name.trim()
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        sp.edit().putString("places", o.toString()).apply()
    }

    // ---- sites you have named yourself ----
    fun sites(): MutableList<Site> {
        val out = ArrayList<Site>()
        val a = JSONArray(sp.getString("sites", "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(
                Site(
                    o.optLong("id", i.toLong()), o.getString("name"),
                    o.getDouble("lat"), o.getDouble("lng"),
                    o.optDouble("radius", 150.0), o.optBoolean("pinned", true)
                )
            )
        }
        return out
    }

    fun saveSites(list: List<Site>) {
        val a = JSONArray()
        for (s in list) {
            a.put(
                JSONObject().put("id", s.id).put("name", s.name)
                    .put("lat", s.lat).put("lng", s.lng)
                    .put("radius", s.radius).put("pinned", s.pinned)
            )
        }
        sp.edit().putString("sites", a.toString()).apply()
    }

    /** Re-uses an existing site if you are already standing inside one. */
    fun addSite(name: String, lat: Double, lng: Double, radius: Double = 150.0) {
        val list = sites()
        val clean = name.trim()

        // already standing inside a pinned site - just rename that one
        for (s in list) {
            if (s.pinned && Geo.haversine(lat, lng, s.lat, s.lng) <= s.radius) {
                s.name = clean
                saveSites(list)
                return
            }
        }
        // an imported name waiting for a coordinate - pin it here
        val waiting = list.firstOrNull { !it.pinned && it.name.equals(clean, true) }
        if (waiting != null) {
            waiting.lat = lat; waiting.lng = lng
            waiting.radius = radius; waiting.pinned = true
            saveSites(list)
            return
        }
        list.add(Site(System.currentTimeMillis(), clean, lat, lng, radius))
        saveSites(list)
    }

    /** A site name imported from a list, with no coordinate yet. */
    fun addPending(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        val list = sites()
        if (list.any { it.name.equals(clean, true) }) return false
        list.add(Site(System.currentTimeMillis() + list.size, clean, 0.0, 0.0, 150.0, false))
        saveSites(list)
        return true
    }

    fun pending(): List<Site> = sites().filter { !it.pinned }

    fun deleteSite(id: Long) = saveSites(sites().filter { it.id != id })

    // ---- automatic names from the address lookup ----
    fun autoNames(): MutableMap<String, String> {
        val m = HashMap<String, String>()
        val o = JSONObject(sp.getString("auto", "{}"))
        for (k in o.keys()) m[k] = o.getString(k)
        return m
    }

    fun setAuto(key: String, name: String) {
        val m = autoNames()
        m[key] = name
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        sp.edit().putString("auto", o.toString()).apply()
    }

    /**
     * Decides what a set of coordinates is called. A site you named yourself
     * wins, and it covers everything inside its radius - 500 m by default - so
     * you only ever have to name a place once. Otherwise the automatic address
     * is used, and failing that it stays blank.
     */
    fun namer(): (Double, Double) -> String {
        val mine = sites()
        val auto = autoNames()
        val typed = places()
        return { lat, lng ->
            var best = ""
            var bestDist = Double.MAX_VALUE
            for (s in mine) {
                if (!s.pinned) continue
                val d = Geo.haversine(lat, lng, s.lat, s.lng)
                if (d <= s.radius && d < bestDist) {
                    bestDist = d
                    best = s.name
                }
            }
            if (best.isNotBlank()) best
            else typed[Geo.key(lat, lng)] ?: auto[Geo.key(lat, lng)] ?: ""
        }
    }

    // ---- archived days ----
    fun days(): JSONObject = JSONObject(sp.getString("days", "{}"))

    fun dayKeys(): List<String> = days().keys().asSequence().sortedDescending().toList()

    fun stopsFor(key: String): List<Stop> {
        val a = days().optJSONArray(key) ?: return emptyList()
        val name = namer()
        val out = ArrayList<Stop>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val lat = o.getDouble("lat")
            val lng = o.getDouble("lng")
            out.add(
                Stop(
                    lat, lng, o.getLong("start"), o.getLong("end"),
                    o.optInt("acc", 0), o.optInt("n", 0), o.optDouble("km", 0.0),
                    name(lat, lng)
                )
            )
        }
        return out
    }

    fun putDays(all: JSONObject) {
        sp.edit().putString("days", all.toString()).apply()
    }

    var lastBackup: Long
        get() = sp.getLong("lastBackup", 0L)
        set(v) = sp.edit().putLong("lastBackup", v).apply()

    fun archive(key: String, stops: List<Stop>) {
        if (stops.isEmpty()) return
        val d = days()
        val a = JSONArray()
        for (s in stops) {
            a.put(
                JSONObject().put("lat", s.lat).put("lng", s.lng)
                    .put("start", s.start).put("end", s.end)
                    .put("acc", s.acc).put("n", s.n).put("km", s.km)
            )
        }
        d.put(key, a)
        sp.edit().putString("days", d.toString()).apply()
    }

    /** If the stored fixes belong to a previous day, file them and start fresh. */
    fun rollOverIfNeeded() {
        val l = fixes()
        if (l.isEmpty()) return
        val k = Fmt.dayKey(l[0].t)
        if (k == Fmt.today()) return
        archive(k, Geo.buildStops(l, namer()))
        saveFixes(emptyList())
    }

    var themeIndex: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    var tracking: Boolean
        get() = sp.getBoolean("tracking", false)
        set(v) = sp.edit().putBoolean("tracking", v).apply()
}
