package com.duorhythm.app.data.local.pref

import android.content.Context

object AppPreferences {
    const val PREFS_NAME = "lifesync_prefs"

    const val KEY_CLASS_LEAD_MINUTES = "pref_class_lead_minutes"
    const val DEFAULT_CLASS_LEAD_MINUTES = 15

    const val KEY_ORDER_LEAD_HOURS = "pref_order_lead_hours"
    const val DEFAULT_ORDER_LEAD_HOURS = 2

    const val KEY_ORDER_LEAD_MODE = "pref_order_lead_mode"
    const val DEFAULT_ORDER_LEAD_MODE = "24_2"

    const val KEY_WALLET_BIOMETRIC_LOCKED = "pref_wallet_biometric_locked"

    const val KEY_MORNING_BRIEFING_TIME = "pref_morning_briefing_time"
    const val DEFAULT_MORNING_BRIEFING_TIME = "08:00 AM"

    const val KEY_EVENING_BRIEFING_TIME = "pref_evening_briefing_time"
    const val DEFAULT_EVENING_BRIEFING_TIME = "08:30 PM"

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
        return when {
            minutes <= 0 -> "Disabled"
            minutes == 15 -> "15 Minutes (Default)"
            else -> "$minutes Minutes"
        }
    }

    fun getOrderLeadHours(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ORDER_LEAD_HOURS, DEFAULT_ORDER_LEAD_HOURS)
    }

    fun setOrderLeadHours(context: Context, hours: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ORDER_LEAD_HOURS, hours).apply()
    }

    fun getOrderLeadHoursDisplayLabel(hours: Int): String {
        return when {
            hours <= 0 -> "Disabled"
            hours == 1 -> "1 Hour before"
            hours == 2 -> "2 Hours before (Default)"
            else -> "$hours Hours before"
        }
    }

    fun isWalletBiometricLocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WALLET_BIOMETRIC_LOCKED, false)
    }

    fun setWalletBiometricLocked(context: Context, locked: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WALLET_BIOMETRIC_LOCKED, locked).apply()
    }

    fun getMorningBriefingTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MORNING_BRIEFING_TIME, DEFAULT_MORNING_BRIEFING_TIME) ?: DEFAULT_MORNING_BRIEFING_TIME
    }

    fun setMorningBriefingTime(context: Context, timeStr: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MORNING_BRIEFING_TIME, timeStr).apply()
    }

    fun getEveningBriefingTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_EVENING_BRIEFING_TIME, DEFAULT_EVENING_BRIEFING_TIME) ?: DEFAULT_EVENING_BRIEFING_TIME
    }

    fun setEveningBriefingTime(context: Context, timeStr: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EVENING_BRIEFING_TIME, timeStr).apply()
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

    const val KEY_MISSIONS_COLLAPSED = "pref_missions_collapsed"
    const val KEY_NOTES_COLLAPSED = "pref_notes_collapsed"

    fun isMissionsCollapsed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MISSIONS_COLLAPSED, false)
    }

    fun setMissionsCollapsed(context: Context, collapsed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MISSIONS_COLLAPSED, collapsed).apply()
    }

    fun isNotesCollapsed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTES_COLLAPSED, false)
    }

    fun setNotesCollapsed(context: Context, collapsed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NOTES_COLLAPSED, collapsed).apply()
    }
}
