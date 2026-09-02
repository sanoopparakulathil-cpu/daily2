package com.gssc.daylog

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max

/** Draws today's route and the stops on it. No Maps SDK, no API key needed. */
class TrackView(ctx: Context) : View(ctx) {

    private var fixes: List<Fix> = emptyList()
    private var stops: List<Stop> = emptyList()

    private var accent = Color.parseColor("#C6F24E")
    private var surface = Color.parseColor("#0D1614")
    private var muted = Color.parseColor("#7A807B")

    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        line.style = Paint.Style.STROKE
        line.strokeCap = Paint.Cap.ROUND
        line.strokeJoin = Paint.Join.ROUND
        ring.style = Paint.Style.STROKE
        label.textAlign = Paint.Align.CENTER
    }

    fun setColors(accent: Int, surface: Int, muted: Int) {
        this.accent = accent
        this.surface = surface
        this.muted = muted
        invalidate()
    }

    fun setData(fixes: List<Fix>, stops: List<Stop>) {
        this.fixes = fixes
        this.stops = stops
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        canvas.drawColor(surface)

        line.strokeWidth = 3f * d
        ring.strokeWidth = 2.5f * d
        label.textSize = 13f * d

        if (fixes.size < 2) {
            label.color = muted
            canvas.drawText(
                if (fixes.isEmpty()) "Turn tracking on to start"
                else "Waiting for more readings",
                width / 2f, height / 2f, label
            )
            return
        }

        val pad = 26f * d
        val minLat = fixes.minOf { it.lat }
        val maxLat = fixes.maxOf { it.lat }
        val minLng = fixes.minOf { it.lng }
        val maxLng = fixes.maxOf { it.lng }
        val span = max(max(maxLat - minLat, maxLng - minLng), 1e-5)

        val usableW = width - pad * 2
        val usableH = height - pad * 2
        val offX = (usableW - ((maxLng - minLng) / span) * usableW) / 2f
        val offY = (usableH - ((maxLat - minLat) / span) * usableH) / 2f

        fun px(lng: Double) = (pad + offX + ((lng - minLng) / span) * usableW).toFloat()
        fun py(lat: Double) = (height - pad - offY - ((lat - minLat) / span) * usableH).toFloat()

        val path = Path()
        for ((i, f) in fixes.withIndex()) {
            if (i == 0) path.moveTo(px(f.lng), py(f.lat)) else path.lineTo(px(f.lng), py(f.lat))
        }
        line.color = accent
        line.alpha = 220
        canvas.drawPath(path, line)

        // stops
        dot.color = surface
        ring.color = accent
        for (s in stops) {
            canvas.drawCircle(px(s.lng), py(s.lat), 7f * d, dot)
            canvas.drawCircle(px(s.lng), py(s.lat), 7f * d, ring)
        }

        // where you are right now
        val last = fixes[fixes.size - 1]
        dot.color = accent
        dot.alpha = 55
        canvas.drawCircle(px(last.lng), py(last.lat), 17f * d, dot)
        dot.alpha = 255
        canvas.drawCircle(px(last.lng), py(last.lat), 6f * d, dot)
    }
}
