package com.gssc.daylog

import android.graphics.Color

data class Palette(
    val title: String,
    val bg: String,
    val card: String,
    val acc: String,
    val fg: String,
    val muted: String,
    val hair: String,
    val foot: String,
    val onAcc: String,
    val darkTiles: Boolean
)

object Themes {

    val all = listOf(
        Palette("Midnight lime", "#101413", "#171C1A", "#C6F24E", "#F2F5F1",
            "#7A807B", "#2A2F2D", "#0D1110", "#0B0D0C", true),
        Palette("Daylight", "#F4F6F2", "#FFFFFF", "#4C7A0B", "#151A14",
            "#6B7268", "#DDE1D8", "#ECEFE8", "#FFFFFF", false),
        Palette("Deep ocean", "#0C1620", "#132232", "#4FC3F7", "#EAF4FB",
            "#7E93A5", "#22384C", "#0A121A", "#04121C", true),
        Palette("Desert sand", "#FBF6EC", "#FFFFFF", "#B45309", "#1F1A12",
            "#7A6E5C", "#E7DCC7", "#F3EADA", "#FFFFFF", false),
        Palette("Graphite", "#1A1A1C", "#242427", "#FF7A45", "#F5F5F6",
            "#8A8A90", "#343438", "#151517", "#1A1A1C", true),
        Palette("High contrast", "#000000", "#111111", "#FFD400", "#FFFFFF",
            "#B0B0B0", "#3A3A3A", "#000000", "#000000", true)
    )

    fun at(i: Int): Palette = all[i.coerceIn(0, all.size - 1)]

    fun color(hex: String): Int = Color.parseColor(hex)
}
