package com.example.idepython

import android.graphics.Color

/**
 * Manual light/dark toggle applied programmatically to the key chrome
 * surfaces (not via day/night resource qualifiers) — the app has one
 * always-on look per surface rather than following the system setting.
 * Syntax-highlight colors stay constant across both themes.
 */
object AppTheme {
    data class Palette(
        val chromeBg: Int,
        val editorBg: Int,
        val gutterBg: Int,
        val gutterText: Int,
        val editorText: Int,
        val editorHint: Int,
        val consoleBg: Int,
        val consoleHeaderBg: Int,
        val consoleText: Int,
        val drawerHeaderBg: Int,
        val tabTextInactive: Int,
        val tabTextActive: Int,
        val divider: Int,
        val symbolText: Int,
        val bannerBg: Int,
        val bannerText: Int
    )

    val DARK = Palette(
        chromeBg = Color.parseColor("#21252B"),
        editorBg = Color.parseColor("#282C34"),
        gutterBg = Color.parseColor("#21252B"),
        gutterText = Color.parseColor("#4B5263"),
        editorText = Color.parseColor("#ABB2BF"),
        editorHint = Color.parseColor("#5C6370"),
        consoleBg = Color.parseColor("#1E2127"),
        consoleHeaderBg = Color.parseColor("#161A20"),
        consoleText = Color.parseColor("#D1D5DB"),
        drawerHeaderBg = Color.parseColor("#181A1F"),
        tabTextInactive = Color.parseColor("#7F8792"),
        tabTextActive = Color.WHITE,
        divider = Color.parseColor("#3E4451"),
        symbolText = Color.parseColor("#ABB2BF"),
        bannerBg = Color.parseColor("#4A1F1F"),
        bannerText = Color.parseColor("#E06C75")
    )

    val LIGHT = Palette(
        chromeBg = Color.parseColor("#EDEDED"),
        editorBg = Color.parseColor("#FFFFFF"),
        gutterBg = Color.parseColor("#F0F0F0"),
        gutterText = Color.parseColor("#A0A0A0"),
        editorText = Color.parseColor("#24292E"),
        editorHint = Color.parseColor("#9AA0A6"),
        consoleBg = Color.parseColor("#FAFAFA"),
        consoleHeaderBg = Color.parseColor("#E4E4E4"),
        consoleText = Color.parseColor("#24292E"),
        drawerHeaderBg = Color.parseColor("#DCDCDC"),
        tabTextInactive = Color.parseColor("#6A6A6A"),
        tabTextActive = Color.parseColor("#000000"),
        divider = Color.parseColor("#D0D0D0"),
        symbolText = Color.parseColor("#3A3A3A"),
        bannerBg = Color.parseColor("#FBE4E4"),
        bannerText = Color.parseColor("#B33A3A")
    )

    fun palette(isLight: Boolean): Palette = if (isLight) LIGHT else DARK
}
