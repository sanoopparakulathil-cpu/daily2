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

    // ---- archived days ----
    fun days(): JSONObject = JSONObject(sp.getString("days", "{}"))

    fun dayKeys(): List<String> = days().keys().asSequence().sortedDescending().toList()

    fun stopsFor(key: String): List<Stop> {
        val a = days().optJSONArray(key) ?: return emptyList()
        val names = places()
        val out = ArrayList<Stop>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val lat = o.getDouble("lat")
            val lng = o.getDouble("lng")
            out.add(
                Stop(
                    lat, lng, o.getLong("start"), o.getLong("end"),
                    o.optInt("acc", 0), o.optInt("n", 0), o.optDouble("km", 0.0),
                    names[Geo.key(lat, lng)] ?: ""
                )
            )
        }
        return out
    }

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
        archive(k, Geo.buildStops(l, places()))
        saveFixes(emptyList())
    }

    var tracking: Boolean
        get() = sp.getBoolean("tracking", false)
        set(v) = sp.edit().putBoolean("tracking", v).apply()
}
