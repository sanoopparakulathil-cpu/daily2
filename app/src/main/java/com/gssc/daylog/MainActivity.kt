package com.gssc.daylog

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
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

    private var BG = 0
    private var CARD = 0
    private var ACC = 0
    private var FG = 0
    private var MUTED = 0
    private var DEEP = 0
    private var HAIR = 0
    private var MAPBG = 0
    private var FOOT = 0

    private fun applyTheme() {
        if (store.lightTheme) {
            BG = Color.parseColor("#F4F6F2")
            CARD = Color.parseColor("#FFFFFF")
            ACC = Color.parseColor("#4C7A0B")
            FG = Color.parseColor("#151A14")
            MUTED = Color.parseColor("#6B7268")
            DEEP = Color.parseColor("#FFFFFF")
            HAIR = Color.parseColor("#DDE1D8")
            MAPBG = Color.parseColor("#EAEFE4")
            FOOT = Color.parseColor("#ECEFE8")
        } else {
            BG = Color.parseColor("#101413")
            CARD = Color.parseColor("#171C1A")
            ACC = Color.parseColor("#C6F24E")
            FG = Color.parseColor("#F2F5F1")
            MUTED = Color.parseColor("#7A807B")
            DEEP = Color.parseColor("#0B0D0C")
            HAIR = Color.parseColor("#2A2F2D")
            MAPBG = Color.parseColor("#0D1614")
            FOOT = Color.parseColor("#0D1110")
        }
    }

    private lateinit var store: Store
    private lateinit var statusView: TextView
    private lateinit var trackSwitch: Switch
    private lateinit var statRow: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var mapView: MapWebView

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 15_000L)
        }
    }

    private val REQ_FINE = 10
    private val REQ_BG = 11
    private val REQ_NOTIF = 12

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        applyTheme()
        store.rollOverIfNeeded()
        setContentView(buildUi())
        render()
    }

    override fun onResume() {
        super.onResume()
        store.rollOverIfNeeded()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------- ui construction ----------

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun card(radius: Int = 14): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(CARD)
        g.cornerRadius = dp(radius).toFloat()
        g.setStroke(dp(1), HAIR)
        return g
    }

    private fun pill(fill: Int, stroke: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(100).toFloat()
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke)
        return g
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        return t
    }

    private fun buildUi(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(BG)
        root.fitsSystemWindows = true

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(18), dp(16), dp(14), dp(14))

        val titleBox = LinearLayout(this)
        titleBox.orientation = LinearLayout.VERTICAL
        titleBox.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        titleBox.addView(text("Daylog", 21f, FG, true))
        statusView = text("off - nothing is being recorded", 12f, MUTED)
        statusView.setPadding(0, dp(5), 0, 0)
        titleBox.addView(statusView)
        header.addView(titleBox)

        val themeBtn = TextView(this)
        themeBtn.text = if (store.lightTheme) "\u2600" else "\u263D"
        themeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        themeBtn.setTextColor(MUTED)
        themeBtn.setPadding(dp(10), dp(6), dp(14), dp(6))
        themeBtn.setOnClickListener {
            store.lightTheme = !store.lightTheme
            recreate()
        }
        header.addView(themeBtn)

        trackSwitch = Switch(this)
        trackSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) askAndStart() else stopTracking()
        }
        header.addView(trackSwitch)
        root.addView(header)

        val divider = View(this)
        divider.setBackgroundColor(HAIR)
        divider.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        root.addView(divider)

        // scroll body
        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, dp(4), 0, dp(28))

        mapView = MapWebView(this)
        val mapLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(210)
        )
        mapLp.setMargins(dp(16), dp(12), dp(16), 0)
        mapView.layoutParams = mapLp
        mapView.background = card(16)
        mapView.clipToOutline = true
        body.addView(mapView)

        statRow = LinearLayout(this)
        statRow.orientation = LinearLayout.HORIZONTAL
        statRow.setPadding(dp(16), dp(14), dp(16), dp(6))
        body.addView(statRow)

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        body.addView(listBox)

        scroll.addView(body)
        root.addView(scroll)

        // footer buttons
        val footer = LinearLayout(this)
        footer.orientation = LinearLayout.VERTICAL
        footer.setPadding(dp(16), dp(10), dp(16), dp(18))
        footer.setBackgroundColor(FOOT)

        footer.addView(makeButton("Save today to Excel", ACC, DEEP, true) { exportToday() })
        val gap = View(this)
        gap.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10))
        footer.addView(gap)
        footer.addView(makeButton("Save full history", Color.TRANSPARENT, ACC, false) { exportAll() })
        root.addView(footer)

        return root
    }

    private fun makeButton(
        label: String, fill: Int, textColor: Int, solid: Boolean, action: () -> Unit
    ): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(textColor)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        b.background = pill(fill, if (solid) Color.TRANSPARENT else ACC)
        b.stateListAnimator = null
        b.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        )
        b.setOnClickListener { action() }
        return b
    }

    // ---------- rendering ----------

    private fun render() {
        val fixes = store.fixes()
        val stops = Geo.buildStops(fixes, store.allNames())
        val running = store.tracking

        NameLookup.fillMissing(this, store)
        mapView.update(fixes, stops, !store.lightTheme)

        if (trackSwitch.isChecked != running) {
            trackSwitch.setOnCheckedChangeListener(null)
            trackSwitch.isChecked = running
            trackSwitch.setOnCheckedChangeListener { _, checked ->
                if (checked) askAndStart() else stopTracking()
            }
        }

        val elapsed = if (fixes.isEmpty()) "0m"
        else Fmt.dur(System.currentTimeMillis() - fixes[0].t)

        statusView.text = if (running)
            "recording - ${fixes.size} fixes - $elapsed"
        else
            "off - nothing is being recorded"

        // stats
        statRow.removeAllViews()
        statRow.addView(statCard(String.format(Locale.US, "%.1f", Geo.dayKm(fixes)), "km today", ACC))
        statRow.addView(statCard(stops.size.toString(), "stops", FG))
        statRow.addView(statCard(elapsed, "tracked", FG))

        // stop list
        listBox.removeAllViews()
        listBox.addView(sectionLabel("Today  " + Fmt.today()))

        if (stops.isEmpty()) {
            val e = text(
                "No stops yet. Turn tracking on and stay in one place for three minutes.\nNames fill in on their own.",
                13f, MUTED
            )
            e.gravity = Gravity.CENTER
            e.setPadding(dp(28), dp(26), dp(28), dp(26))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(16), dp(4), dp(16), dp(4))
            e.layoutParams = lp
            e.background = card(16)
            listBox.addView(e)
        } else {
            for (s in stops) listBox.addView(stopRow(s, true))
        }

        // history
        val keys = store.dayKeys()
        if (keys.isNotEmpty()) {
            listBox.addView(sectionLabel("Earlier days"))
            for (k in keys) {
                val list = store.stopsFor(k)
                val total = list.sumOf { it.km }
                listBox.addView(
                    sectionLabel(
                        "$k   ${list.size} stops - " + String.format(Locale.US, "%.1f km", total)
                    )
                )
                for (s in list) listBox.addView(stopRow(s, false))
            }
        }
    }

    private fun sectionLabel(s: String): TextView {
        val t = text(s, 11.5f, MUTED)
        t.setPadding(dp(20), dp(18), dp(20), dp(8))
        return t
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
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(16), dp(4), dp(16), dp(4))
        row.layoutParams = lp

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
        return row
    }

    private fun nameDialog(s: Stop) {
        val input = EditText(this)
        input.setText(s.name)
        input.hint = if (s.name.isBlank()) "e.g. Jotun Dammam 2" else s.name
        input.setTextColor(FG)
        input.setPadding(dp(20), dp(16), dp(20), dp(16))

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Name this place")
            .setMessage(
                Fmt.hhmm(s.start) + " - " + Fmt.hhmm(s.end) + "\n" +
                    String.format(Locale.US, "%.4f, %.4f", s.lat, s.lng)
            )
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                store.setPlace(Geo.key(s.lat, s.lng), input.text.toString())
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- permissions and service ----------

    private fun has(p: String) =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun askAndStart() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), REQ_FINE
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            return
        }
        if (!has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Allow all the time")
                .setMessage(
                    "Daylog needs location access set to \"Allow all the time\" so it keeps " +
                        "recording when your screen is off. On the next screen choose that option."
                )
                .setPositiveButton("Continue") { _, _ ->
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BG
                    )
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

    private fun startTracking() {
        store.tracking = true
        startForegroundService(Intent(this, TrackerService::class.java))
        askBatteryExemption()
        render()
    }

    /**
     * Without this the phone parks the app in Doze after the screen has been
     * off a while and readings drop to a handful per night.
     */
    private fun askBatteryExemption() {
        try {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) return
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Stop Android pausing Daylog")
                .setMessage(
                    "Android puts apps to sleep when the screen is off, which makes " +
                        "Daylog miss most of your route. On the next screen choose " +
                        "Daylog, then Don't optimise."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(
                        Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }
                .setNegativeButton("Later", null)
                .show()
        } catch (e: Exception) {
            // some phones hide this screen; the battery menu still works
        }
    }

    private fun stopTracking() {
        store.tracking = false
        stopService(Intent(this, TrackerService::class.java))
        render()
    }

    // ---------- export ----------

    private fun exportToday() {
        val stops = Geo.buildStops(store.fixes(), store.allNames())
        if (stops.isEmpty()) {
            toast("Nothing recorded today yet.")
            return
        }
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
        for (s in Geo.buildStops(store.fixes(), store.allNames())) {
            rows.add(Xlsx.row(Fmt.today(), s))
        }
        if (rows.size == 1) {
            toast("No history saved yet.")
            return
        }
        write(Xlsx.build(rows, "Daylog history"), "daylog_history.xlsx")
    }

    private fun write(bytes: ByteArray, filename: String) {
        try {
            val values = ContentValues()
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename)
            values.put(
                MediaStore.Downloads.MIME_TYPE,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            values.put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Daylog"
            )
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                toast("Could not create the file.")
                return
            }
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }

            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Saved")
                .setMessage("Downloads/Daylog/$filename")
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
