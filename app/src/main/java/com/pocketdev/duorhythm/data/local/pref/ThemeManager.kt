package com.pocketdev.duorhythm.data.local.pref

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    const val PREFS_NAME = "lifesync_prefs"
    const val KEY_THEME_MODE = "app_theme_mode"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    /**
     * Reads the persisted theme preference from SharedPreferences.
     * Supports String ("dark", "light", "system", etc.) or Int (AppCompatDelegate constants).
     */
    fun getSavedThemeMode(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = try {
                prefs.getString(KEY_THEME_MODE, null)
            } catch (_: Exception) {
                try {
                    when (prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) {
                        AppCompatDelegate.MODE_NIGHT_YES -> THEME_DARK
                        AppCompatDelegate.MODE_NIGHT_NO -> THEME_LIGHT
                        else -> THEME_SYSTEM
                    }
                } catch (_: Exception) {
                    null
                }
            }

            when (raw?.lowercase()?.trim()) {
                THEME_DARK, "night", "mode_night_yes", "2" -> THEME_DARK
                THEME_LIGHT, "day", "mode_night_no", "1" -> THEME_LIGHT
                THEME_SYSTEM, "system_default", "follow_system", "mode_night_follow_system", "-1" -> THEME_SYSTEM
                else -> THEME_SYSTEM
            }
        } catch (_: Throwable) {
            THEME_SYSTEM
        }
    }

    /**
     * Maps theme mode string to AppCompatDelegate night mode constant.
     */
    fun getNightMode(themeMode: String): Int {
        return when (themeMode.lowercase().trim()) {
            THEME_DARK, "night", "mode_night_yes", "2" -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_LIGHT, "day", "mode_night_no", "1" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    /**
     * Sets and persists the theme mode and applies it immediately via AppCompatDelegate.
     */
    fun setThemeMode(context: Context, themeMode: String) {
        val normalizedMode = when (themeMode.lowercase().trim()) {
            THEME_DARK, "night", "mode_night_yes", "2" -> THEME_DARK
            THEME_LIGHT, "day", "mode_night_no", "1" -> THEME_LIGHT
            else -> THEME_SYSTEM
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_THEME_MODE, normalizedMode).apply()
        } catch (_: Throwable) {}

        val nightMode = getNightMode(normalizedMode)
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    /**
     * Overload for setting theme mode using AppCompatDelegate integer constants.
     */
    fun setThemeMode(context: Context, nightMode: Int) {
        val mode = when (nightMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> THEME_DARK
            AppCompatDelegate.MODE_NIGHT_NO -> THEME_LIGHT
            else -> THEME_SYSTEM
        }
        setThemeMode(context, mode)
    }

    /**
     * Called in Application.onCreate() to read persisted theme preference and apply night mode
     * before the main window is inflated to avoid theme flash / blinking on cold start.
     */
    fun applySavedTheme(context: Context) {
        try {
            val savedMode = getSavedThemeMode(context)
            val nightMode = getNightMode(savedMode)
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (_: Throwable) {}
    }
}
