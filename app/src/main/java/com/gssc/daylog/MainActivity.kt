package com.gssc.daylog

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var p: Palette

    private var BG = 0; private var CARD = 0; private var ACC = 0
    private var FG = 0; private var MUTED = 0; private var HAIR = 0
    private var FOOT = 0; private var ONACC = 0

    private lateinit var statusView: TextView
    private lateinit var trackSwitch: Switch
    private lateinit var body: LinearLayout
    private lateinit var navBar: LinearLayout
    private lateinit var mapView: MapWebView

    private var tab = 0                 // 0 today, 1 history, 2 sites
    private var siteQuery = ""

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 15_000L) }
    }

    private val REQ_FINE = 10
    private val REQ_BG = 11
    private val REQ_NOTIF = 12

    // ---------------- lifecycle ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        applyTheme()
        store.rollOverIfNeeded()
        setContentView(buildShell())
        render()
    }

    override fun onResume() {
        super.onResume()
        store.rollOverIfNeeded()
        autoBackup()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun applyTheme() {
        p = Themes.at(store.themeIndex)
        BG = Themes.color(p.bg); CARD = Themes.color(p.card); ACC = Themes.color(p.acc)
        FG = Themes.color(p.fg); MUTED = Themes.color(p.muted); HAIR = Themes.color(p.hair)
        FOOT = Themes.color(p.foot); ONACC = Themes.color(p.onAcc)
    }

    // ---------------- small builders ----------------

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun card(radius: Int = 14): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(CARD); g.cornerRadius = dp(radius).toFloat(); g.setStroke(dp(1), HAIR)
        return g
    }

    private fun pill(fill: Int, stroke: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill); g.cornerRadius = dp(100).toFloat()
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke)
        return g
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun wide(v: View, top: Int = 4, bottom: Int = 4): View {
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(16), dp(top), dp(16), dp(bottom))
        v.layoutParams = lp
        return v
    }

    private fun makeButton(label: String, solid: Boolean, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(if (solid) ONACC else ACC)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        b.background = if (solid) pill(ACC, Color.TRANSPARENT) else pill(Color.TRANSPARENT, ACC)
        b.stateListAnimator = null
        b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        b.setOnClickListener { action() }
        return b
    }

    // ---------------- shell ----------------

    private fun buildShell(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(BG)
        root.fitsSystemWindows = true

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(18), dp(16), dp(14), dp(14))

        val titleBox = LinearLayout(this)
        titleBox.orientation = LinearLayout.VERTICAL
        titleBox.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        titleBox.addView(text("Daylog", 21f, FG, true))
        statusView = text("off", 12f, MUTED)
        statusView.setPadding(0, dp(5), 0, 0)
        titleBox.addView(statusView)
        header.addView(titleBox)

        val themeBtn = text("\u25D0", 20f, MUTED)
        themeBtn.setPadding(dp(10), dp(6), dp(14), dp(6))
        themeBtn.setOnClickListener { pickTheme() }
        header.addView(themeBtn)

        trackSwitch = Switch(this)
        trackSwitch.setOnCheckedChangeListener { _, c -> if (c) askAndStart() else stopTracking() }
        header.addView(trackSwitch)
        root.addView(header)

        val hr = View(this)
        hr.setBackgroundColor(HAIR)
        hr.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        root.addView(hr)

        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        scroll.isFillViewport = true
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, dp(4), 0, dp(24))
        scroll.addView(body)
        root.addView(scroll)

        mapView = MapWebView(this)

        navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.setBackgroundColor(FOOT)
        navBar.setPadding(0, dp(8), 0, dp(12))
        root.addView(navBar)

        return root
    }

    private fun navButton(label: String, index: Int): View {
        val t = text(label, 12.5f, if (tab == index) ACC else MUTED, tab == index)
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(8), 0, dp(8))
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        t.setOnClickListener { tab = index; render() }
        return t
    }

    // ---------------- render ----------------

    private fun render() {
        val name = store.namer()
        val fixes = store.fixes()
        val stops = Geo.buildStops(fixes, name)
        val running = store.tracking

        NameLookup.fillMissing(this, store)

        if (trackSwitch.isChecked != running) {
            trackSwitch.setOnCheckedChangeListener(null)
            trackSwitch.isChecked = running
            trackSwitch.setOnCheckedChangeListener { _, c -> if (c) askAndStart() else stopTracking() }
        }

        val elapsed = if (fixes.isEmpty()) "0m"
        else Fmt.dur(System.currentTimeMillis() - fixes[0].t)
        statusView.text =
            if (running) "recording - ${fixes.size} fixes - $elapsed" else "off"

        navBar.removeAllViews()
        navBar.addView(navButton("Today", 0))
        navBar.addView(navButton("History", 1))
        navBar.addView(navButton("Sites", 2))

        (mapView.parent as? ViewGroup)?.removeView(mapView)
        body.removeAllViews()

        when (tab) {
            0 -> renderToday(fixes, stops, elapsed)
            1 -> renderHistory()
            2 -> renderSites()
        }
    }

    private fun renderToday(fixes: List<Fix>, stops: List<Stop>, elapsed: String) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230))
        lp.setMargins(dp(16), dp(12), dp(16), 0)
        mapView.layoutParams = lp
        mapView.background = card(16)
        mapView.clipToOutline = true
        body.addView(mapView)
        mapView.update(fixes, stops, p.darkTiles, p.acc)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(16), dp(14), dp(16), dp(6))
        row.addView(statCard(String.format(Locale.US, "%.1f", Geo.dayKm(fixes)), "km today", ACC))
        row.addView(statCard(stops.size.toString(), "stops", FG))
        row.addView(statCard(elapsed, "tracked", FG))
        body.addView(row)

        body.addView(sectionLabel("Today  " + Fmt.today()))
        if (stops.isEmpty()) {
            body.addView(emptyBox("No stops yet. Stay somewhere three minutes and it appears here."))
        } else {
            for (s in stops) body.addView(stopRow(s, true))
        }

        body.addView(spacer(10))
        body.addView(wide(makeButton("Save today to Excel", true) { exportToday() }))
    }

    private fun renderHistory() {
        val keys = store.dayKeys()
        if (keys.isEmpty()) {
            body.addView(sectionLabel("Earlier days"))
            body.addView(emptyBox("Nothing here yet. Each day moves across after midnight."))
        } else {
            for (k in keys) {
                val list = store.stopsFor(k)
                val total = list.sumOf { it.km }
                body.addView(
                    sectionLabel(k + "   " + list.size + " stops - " +
                        String.format(Locale.US, "%.1f km", total))
                )
                for (s in list) body.addView(stopRow(s, false))
            }
        }
        body.addView(spacer(10))
        body.addView(wide(makeButton("Save full history", true) { exportAll() }))
        body.addView(wide(makeButton("Back up to Drive", false) { backupNow() }))
        body.addView(wide(makeButton("Restore from backup", false) { pickRestore() }))

        val last = store.lastBackup
        body.addView(
            wide(text(
                (if (last == 0L) "No backup file written yet."
                else "Last backup file: " + Fmt.dayKey(last) + " " + Fmt.hhmm(last)) +
                    "\n\nA copy is written to Downloads/Daylog/Backups once a day by " +
                    "itself. Back up to Drive sends the newest one to Google Drive, " +
                    "or anywhere else you pick.",
                11.5f, MUTED
            ), 8, 8)
        )
    }

    // ---------------- backup ----------------

    private fun backupFileName() = "daylog_backup_" + Fmt.today() + ".json"

    /** Writes one snapshot a day into Downloads, keeping it out of your way. */
    private fun autoBackup() {
        val now = System.currentTimeMillis()
        if (now - store.lastBackup < 20 * 60 * 60 * 1000L) return
        if (store.fixes().isEmpty() && store.dayKeys().isEmpty()) return
        try {
            writeFile(
                Backup.toJson(store).toByteArray(Charsets.UTF_8),
                backupFileName(), "application/json", "Backups"
            )
            store.lastBackup = now
        } catch (e: Exception) {
            // out of space or storage busy - try again next time
        }
    }

    private fun backupNow() {
        try {
            val uri = writeFile(
                Backup.toJson(store).toByteArray(Charsets.UTF_8),
                backupFileName(), "application/json", "Backups"
            ) ?: run { toast("Could not write the backup."); return }
            store.lastBackup = System.currentTimeMillis()

            val send = Intent(Intent.ACTION_SEND)
            send.type = "application/json"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Save backup to"))
            render()
        } catch (e: Exception) {
            toast("Backup failed: " + e.message)
        }
    }

    private fun pickRestore() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        try {
            startActivityForResult(i, REQ_RESTORE)
        } catch (e: Exception) {
            toast("No file picker on this phone.")
        }
    }

    private fun renderSites() {
        val search = EditText(this)
        search.hint = "Search sites"
        search.setText(siteQuery)
        search.setSingleLine()
        search.setTextColor(FG)
        search.setHintTextColor(MUTED)
        search.background = card(12)
        search.setPadding(dp(14), dp(12), dp(14), dp(12))
        body.addView(wide(search, 12, 4))

        val listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        body.addView(listBox)
        fillSiteList(listBox)

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                siteQuery = s?.toString() ?: ""
                fillSiteList(listBox)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        body.addView(spacer(10))
        body.addView(wide(makeButton("Add site at my location", true) { addSiteHere() }))
        body.addView(wide(makeButton("Import sites from file", false) { importSites() }))
        body.addView(
            wide(text(
                "A site covers 150 m by default - tap one to rename it or widen the " +
                    "radius for a big plant. Stop anywhere inside it and Daylog uses that " +
                    "name by itself.\n\nImport file: one site per line. Either just the " +
                    "name, or name,latitude,longitude. Names imported without coordinates " +
                    "wait in this list until you visit and pin them.",
                12f, MUTED
            ), 8, 8)
        )
    }

    private fun fillSiteList(box: LinearLayout) {
        box.removeAllViews()
        val q = siteQuery.trim().lowercase(Locale.US)
        val list = store.sites()
            .filter { q.isEmpty() || it.name.lowercase(Locale.US).contains(q) }
            .sortedWith(
                compareBy<Site> { if (it.pinned) 0 else 1 }
                    .thenBy { it.name.lowercase(Locale.US) }
            )

        if (list.isEmpty()) {
            box.addView(emptyBox(
                if (store.sites().isEmpty()) "No sites yet. Add one while you are standing there."
                else "Nothing matches that search."
            ))
            return
        }
        for (s in list) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.background = card(14)
            row.setPadding(dp(14), dp(13), dp(15), dp(13))

            val mid = LinearLayout(this)
            mid.orientation = LinearLayout.VERTICAL
            mid.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            mid.addView(text(s.name, 15f, if (s.pinned) FG else MUTED, s.pinned))
            mid.addView(text(
                if (s.pinned)
                    String.format(Locale.US, "%.4f, %.4f  -  %.0f m", s.lat, s.lng, s.radius)
                else "not pinned yet - visit it and tap Add site",
                11.5f, MUTED
            ))
            row.addView(mid)

            val del = text("\u2715", 16f, MUTED)
            del.setPadding(dp(12), dp(4), dp(4), dp(4))
            del.setOnClickListener { confirmDelete(s, box) }
            row.addView(del)

            row.setOnClickListener { editSite(s, box) }
            box.addView(wide(row))
        }
    }

    private fun spacer(h: Int): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
        return v
    }

    private fun sectionLabel(s: String): TextView {
        val t = text(s, 11.5f, MUTED)
        t.setPadding(dp(20), dp(18), dp(20), dp(8))
        return t
    }

    private fun emptyBox(msg: String): View {
        val e = text(msg, 13f, MUTED)
        e.gravity = Gravity.CENTER
        e.setPadding(dp(24), dp(26), dp(24), dp(26))
        e.background = card(16)
        return wide(e)
    }

    private fun statCard(value: String, label: String, valueColor: Int): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = card(14)
        box.setPadding(dp(13), dp(13), dp(13), dp(13))
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(4), 0, dp(4), 0)
        box.layoutParams = lp
        box.addView(text(value, 20f, valueColor, true))
        val l = text(label, 11f, MUTED)
        l.setPadding(0, dp(6), 0, 0)
        box.addView(l)
        return box
    }

    private fun stopRow(s: Stop, editable: Boolean): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.background = card(14)
        row.setPadding(dp(14), dp(14), dp(15), dp(14))

        val time = text(Fmt.hhmm(s.start), 13f, ACC, true)
        time.layoutParams = LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT)
        row.addView(time)

        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val named = s.name.isNotBlank()
        val nm = text(
            if (named) s.name else if (editable) "Tap to name" else "Unnamed stop",
            15f, if (named) FG else MUTED, named
        )
        nm.maxLines = 1
        nm.ellipsize = TextUtils.TruncateAt.END
        mid.addView(nm)
        val sub = text(Fmt.hhmm(s.start) + " - " + Fmt.hhmm(s.end), 12f, MUTED)
        sub.setPadding(0, dp(3), 0, 0)
        mid.addView(sub)
        row.addView(mid)

        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        right.gravity = Gravity.END
        right.addView(text(Fmt.dur(s.end - s.start), 13f, FG, true))
        val km = text(String.format(Locale.US, "+%.1f km", s.km), 11f, MUTED)
        km.setPadding(0, dp(3), 0, 0)
        right.addView(km)
        row.addView(right)

        if (editable) row.setOnClickListener { nameDialog(s) }
        return wide(row)
    }

    // ---------------- dialogs ----------------

    private fun styledInput(value: String, hint: String): EditText {
        val i = EditText(this)
        i.setText(value)
        i.hint = hint
        i.setSingleLine()
        i.setTextColor(FG)
        i.setPadding(dp(20), dp(16), dp(20), dp(16))
        return i
    }

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    private fun pickTheme() {
        val names = Themes.all.map { it.title }.toTypedArray()
        dialog()
            .setTitle("Theme")
            .setSingleChoiceItems(names, store.themeIndex) { d, which ->
                store.themeIndex = which
                d.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Naming a stop creates a 500 m site, so the name sticks next time. */
    private fun nameDialog(s: Stop) {
        val waiting = store.pending()
        if (s.name.isBlank() && waiting.isNotEmpty()) {
            val names = waiting.map { it.name }.toMutableList()
            names.add("Type a new name...")
            dialog()
                .setTitle("Which site is this?")
                .setItems(names.toTypedArray()) { _, which ->
                    if (which < waiting.size) {
                        store.addSite(waiting[which].name, s.lat, s.lng)
                        render()
                    } else {
                        typeStopName(s)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        typeStopName(s)
    }

    private fun typeStopName(s: Stop) {
        val input = styledInput(s.name, "e.g. Jotun Dammam 2")
        dialog()
            .setTitle("Name this place")
            .setMessage(
                Fmt.hhmm(s.start) + " - " + Fmt.hhmm(s.end) + "\n" +
                    String.format(Locale.US, "%.4f, %.4f", s.lat, s.lng) +
                    "\nSaved as a site covering 150 m. Change that in the Sites tab."
            )
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val v = input.text.toString().trim()
                if (v.isNotBlank()) store.addSite(v, s.lat, s.lng)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editSite(s: Site, box: LinearLayout) {
        val nameIn = styledInput(s.name, "Site name")
        val radiusIn = styledInput(
            String.format(Locale.US, "%.0f", s.radius), "Radius in metres"
        )
        radiusIn.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.addView(nameIn)
        wrap.addView(radiusIn)
        val hint = text(
            "Radius is how far from the pin still counts as this site. " +
                "150 m suits a tower or an office. Use 400-600 m for a large plant.",
            11.5f, MUTED
        )
        hint.setPadding(dp(22), dp(6), dp(22), 0)
        wrap.addView(hint)

        dialog()
            .setTitle(if (s.pinned) "Edit site" else "Rename site")
            .setMessage(
                if (s.pinned) String.format(Locale.US, "%.4f, %.4f", s.lat, s.lng)
                else "Not pinned yet - visit it and tap Add site at my location."
            )
            .setView(wrap)
            .setPositiveButton("Save") { _, _ ->
                val v = nameIn.text.toString().trim()
                val r = radiusIn.text.toString().trim().toDoubleOrNull()
                val list = store.sites()
                val target = list.firstOrNull { it.id == s.id }
                if (target != null) {
                    if (v.isNotBlank()) target.name = v
                    if (r != null && r >= 30 && r <= 5000) target.radius = r
                    store.saveSites(list)
                    fillSiteList(box)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(s: Site, box: LinearLayout) {
        dialog()
            .setTitle("Remove ${s.name}?")
            .setMessage("Stops already recorded keep the name. New ones will not use it.")
            .setPositiveButton("Remove") { _, _ ->
                store.deleteSite(s.id)
                fillSiteList(box)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addSiteHere() {
        val fixes = store.fixes()
        if (fixes.isEmpty()) {
            toast("No location yet. Turn tracking on first.")
            return
        }
        val last = fixes[fixes.size - 1]

        val waiting = store.pending()
        if (waiting.isNotEmpty()) {
            val names = waiting.map { it.name }.toMutableList()
            names.add("Type a new name...")
            dialog()
                .setTitle("Which site is this?")
                .setItems(names.toTypedArray()) { _, which ->
                    if (which < waiting.size) {
                        store.addSite(waiting[which].name, last.lat, last.lng)
                        toast(waiting[which].name + " pinned here")
                        render()
                    } else {
                        typeNewSite(last)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        typeNewSite(last)
    }

    private fun typeNewSite(last: Fix) {
        val input = styledInput("", "Site name")
        dialog()
            .setTitle("Add site here")
            .setMessage(
                String.format(Locale.US, "%.4f, %.4f", last.lat, last.lng) +
                    "\nCovers 150 m. Widen it later in the Sites tab."
            )
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val v = input.text.toString().trim()
                if (v.isNotBlank()) {
                    store.addSite(v, last.lat, last.lng)
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- site import ----------------

    private val REQ_IMPORT = 20
    private val REQ_RESTORE = 21

    private fun importSites() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        try {
            startActivityForResult(i, REQ_IMPORT)
        } catch (e: Exception) {
            toast("No file picker on this phone.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        if (requestCode == REQ_RESTORE) {
            try {
                val text = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return
                val msg = Backup.restore(store, text)
                dialog().setTitle("Restore").setMessage(msg)
                    .setPositiveButton("OK", null).show()
                render()
            } catch (e: Exception) {
                toast("Could not read that backup.")
            }
            return
        }
        if (requestCode != REQ_IMPORT) return
        var added = 0
        var skipped = 0
        try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty()) continue
                    val parts = line.split(",")
                    val lat = if (parts.size >= 3) parts[parts.size - 2].trim().toDoubleOrNull() else null
                    val lng = if (parts.size >= 3) parts[parts.size - 1].trim().toDoubleOrNull() else null

                    if (lat != null && lng != null &&
                        lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                    ) {
                        val nm = parts.subList(0, parts.size - 2).joinToString(",").trim()
                        if (nm.isBlank()) { skipped++; continue }
                        store.addSite(nm, lat, lng)
                        added++
                    } else {
                        // no coordinates on this line - keep the name and pin it later
                        if (store.addPending(line)) added++ else skipped++
                    }
                }
            }
            toast("$added sites added" + if (skipped > 0) ", $skipped lines skipped" else "")
            render()
        } catch (e: Exception) {
            toast("Could not read that file.")
        }
    }

    // ---------------- permissions ----------------

    private fun has(perm: String) = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun askAndStart() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION), REQ_FINE
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            return
        }
        if (!has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            dialog()
                .setTitle("Allow all the time")
                .setMessage(
                    "Daylog needs location set to \"Allow all the time\" so it keeps " +
                        "recording when the screen is off. Choose that on the next screen."
                )
                .setPositiveButton("Continue") { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BG)
                }
                .setNegativeButton("Not now") { _, _ -> startTracking() }
                .show()
            return
        }
        startTracking()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQ_FINE -> if (ok) askAndStart() else {
                toast("Location permission is needed to record your day.")
                trackSwitch.isChecked = false
            }
            REQ_NOTIF -> askAndStart()
            REQ_BG -> startTracking()
        }
    }

    private fun askBatteryExemption() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) return
            dialog()
                .setTitle("Stop Android pausing Daylog")
                .setMessage(
                    "Android sleeps apps when the screen is off, which makes Daylog " +
                        "miss most of your route. On the next screen pick Daylog, " +
                        "then Don't optimise."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
                .setNegativeButton("Later", null)
                .show()
        } catch (e: Exception) {
        }
    }

    private fun startTracking() {
        store.tracking = true
        startForegroundService(Intent(this, TrackerService::class.java))
        askBatteryExemption()
        render()
    }

    private fun stopTracking() {
        store.tracking = false
        stopService(Intent(this, TrackerService::class.java))
        render()
    }

    // ---------------- export ----------------

    private fun exportToday() {
        val stops = Geo.buildStops(store.fixes(), store.namer())
        if (stops.isEmpty()) { toast("Nothing recorded today yet."); return }
        val rows = ArrayList<List<Any>>()
        rows.add(Xlsx.HEADER)
        for (s in stops) rows.add(Xlsx.row(Fmt.today(), s))
        write(Xlsx.build(rows, "Daylog"), Fmt.today() + "_daylog.xlsx")
    }

    private fun exportAll() {
        val rows = ArrayList<List<Any>>()
        rows.add(Xlsx.HEADER)
        for (k in store.dayKeys().sorted()) {
            for (s in store.stopsFor(k)) rows.add(Xlsx.row(k, s))
        }
        for (s in Geo.buildStops(store.fixes(), store.namer())) {
            rows.add(Xlsx.row(Fmt.today(), s))
        }
        if (rows.size == 1) { toast("No history saved yet."); return }
        write(Xlsx.build(rows, "Daylog history"), "daylog_history.xlsx")
    }

    private fun writeFile(
        bytes: ByteArray, filename: String, mime: String, subFolder: String
    ): android.net.Uri? {
        val values = ContentValues()
        values.put(MediaStore.Downloads.DISPLAY_NAME, filename)
        values.put(MediaStore.Downloads.MIME_TYPE, mime)
        values.put(
            MediaStore.Downloads.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/Daylog/" + subFolder
        )
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        return uri
    }

    private fun write(bytes: ByteArray, filename: String) {
        try {
            val uri = writeFile(
                bytes, filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Reports"
            )
            if (uri == null) { toast("Could not create the file."); return }

            dialog()
                .setTitle("Saved")
                .setMessage("Downloads/Daylog/Reports/$filename")
                .setPositiveButton("Share") { _, _ ->
                    val send = Intent(Intent.ACTION_SEND)
                    send.type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    send.putExtra(Intent.EXTRA_STREAM, uri)
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, "Send file"))
                }
                .setNegativeButton("Done", null)
                .show()
        } catch (e: Exception) {
            toast("Save failed: " + e.message)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
