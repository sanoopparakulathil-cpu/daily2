package com.gssc.daylog

import org.json.JSONArray
import org.json.JSONObject

/**
 * The whole app in one JSON file: sites, names, archived days and today's
 * readings. Small enough to keep in Drive and to mail to yourself.
 */
object Backup {

    const val VERSION = 1

    fun toJson(store: Store): String {
        val sites = JSONArray()
        for (s in store.sites()) {
            sites.put(
                JSONObject().put("id", s.id).put("name", s.name)
                    .put("lat", s.lat).put("lng", s.lng)
                    .put("radius", s.radius).put("pinned", s.pinned)
            )
        }
        val fixes = JSONArray()
        for (f in store.fixes()) {
            fixes.put(
                JSONObject().put("t", f.t).put("lat", f.lat)
                    .put("lng", f.lng).put("acc", f.acc)
            )
        }
        val places = JSONObject()
        for ((k, v) in store.places()) places.put(k, v)
        val auto = JSONObject()
        for ((k, v) in store.autoNames()) auto.put(k, v)

        return JSONObject()
            .put("app", "daylog")
            .put("version", VERSION)
            .put("saved", System.currentTimeMillis())
            .put("sites", sites)
            .put("fixes", fixes)
            .put("places", places)
            .put("auto", auto)
            .put("days", store.days())
            .toString()
    }

    /** Merges into what is already there. Nothing is deleted. */
    fun restore(store: Store, text: String): String {
        val o = JSONObject(text)
        if (o.optString("app") != "daylog") return "That is not a Daylog backup."

        var newSites = 0
        val existing = store.sites()
        val arr = o.optJSONArray("sites") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val name = s.getString("name")
            if (existing.any { it.name.equals(name, true) }) continue
            existing.add(
                Site(
                    s.optLong("id", System.currentTimeMillis() + i), name,
                    s.getDouble("lat"), s.getDouble("lng"),
                    s.optDouble("radius", 150.0), s.optBoolean("pinned", true)
                )
            )
            newSites++
        }
        store.saveSites(existing)

        val places = o.optJSONObject("places")
        if (places != null) for (k in places.keys()) store.setPlace(k, places.getString(k))
        val auto = o.optJSONObject("auto")
        if (auto != null) for (k in auto.keys()) store.setAuto(k, auto.getString(k))

        var newDays = 0
        val days = o.optJSONObject("days")
        if (days != null) {
            val have = store.days()
            for (k in days.keys()) {
                if (have.has(k)) continue
                have.put(k, days.getJSONArray(k))
                newDays++
            }
            store.putDays(have)
        }
        return "Restored $newSites sites and $newDays days."
    }
}
