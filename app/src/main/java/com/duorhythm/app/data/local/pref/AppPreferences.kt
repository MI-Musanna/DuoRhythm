package com.duorhythm.app.data.local.pref

import android.content.Context

object AppPreferences {
    const val PREFS_NAME = "lifesync_prefs"

    const val KEY_CLASS_LEAD_MINUTES = "pref_class_lead_minutes"
    const val DEFAULT_CLASS_LEAD_MINUTES = 15

    const val KEY_ORDER_LEAD_MODE = "pref_order_lead_mode"
    const val DEFAULT_ORDER_LEAD_MODE = "24_2"

    val CLASS_LEAD_MINUTES_OPTIONS = listOf(5, 10, 15, 30, 0)
    val CLASS_LEAD_MINUTES_LABELS = arrayOf(
        "5 Minutes",
        "10 Minutes",
        "15 Minutes (Default)",
        "30 Minutes",
        "Disabled"
    )

    val ORDER_LEAD_MODE_OPTIONS = listOf("24_2", "12_1", "2", "DISABLED")
    val ORDER_LEAD_MODE_LABELS = arrayOf(
        "24h & 2h before (Default)",
        "12h & 1h before",
        "2h before only",
        "Disabled"
    )

    fun getClassLeadMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CLASS_LEAD_MINUTES, DEFAULT_CLASS_LEAD_MINUTES)
    }

    fun setClassLeadMinutes(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CLASS_LEAD_MINUTES, minutes).apply()
    }

    fun getClassLeadMinutesDisplayLabel(minutes: Int): String {
        return when (minutes) {
            5 -> "5 Minutes"
            10 -> "10 Minutes"
            15 -> "15 Minutes (Default)"
            30 -> "30 Minutes"
            0, -1 -> "Disabled"
            else -> "$minutes Minutes"
        }
    }

    fun getOrderLeadMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ORDER_LEAD_MODE, DEFAULT_ORDER_LEAD_MODE) ?: DEFAULT_ORDER_LEAD_MODE
    }

    fun setOrderLeadMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ORDER_LEAD_MODE, mode).apply()
    }

    fun getOrderLeadModeDisplayLabel(mode: String): String {
        return when (mode) {
            "24_2" -> "24h & 2h before (Default)"
            "12_1" -> "12h & 1h before"
            "2" -> "2h before only"
            "DISABLED" -> "Disabled"
            else -> "24h & 2h before (Default)"
        }
    }
}
