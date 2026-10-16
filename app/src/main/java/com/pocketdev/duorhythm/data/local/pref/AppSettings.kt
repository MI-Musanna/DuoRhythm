package com.pocketdev.duorhythm.data.local.pref

import android.content.Context
import java.util.Calendar

object AppSettings {
    const val PREFS_NAME = "lifesync_prefs"
    const val KEY_START_OF_WEEK = "start_of_week"

    const val START_OF_WEEK_SATURDAY = Calendar.SATURDAY // 7
    const val START_OF_WEEK_SUNDAY = Calendar.SUNDAY     // 1
    const val START_OF_WEEK_MONDAY = Calendar.MONDAY     // 2

    val START_OF_WEEK_OPTIONS = listOf(
        START_OF_WEEK_SATURDAY,
        START_OF_WEEK_SUNDAY,
        START_OF_WEEK_MONDAY
    )

    val START_OF_WEEK_LABELS = arrayOf(
        "Saturday",
        "Sunday",
        "Monday"
    )

    fun getStartOfWeek(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_START_OF_WEEK, START_OF_WEEK_SATURDAY)
    }

    fun setStartOfWeek(context: Context, startOfWeek: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_START_OF_WEEK, startOfWeek).apply()
    }

    fun getStartOfWeekDisplayLabel(startOfWeek: Int): String {
        return when (startOfWeek) {
            Calendar.SATURDAY -> "Saturday (Default)"
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            else -> "Saturday (Default)"
        }
    }

    const val KEY_AUTO_TRACK_ATTENDANCE = "key_auto_track_attendance"

    fun isAutoTrackAttendanceEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_TRACK_ATTENDANCE, false) ||
               prefs.getBoolean("KEY_AUTO_TRACK_ATTENDANCE", false)
    }

    fun setAutoTrackAttendanceEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTO_TRACK_ATTENDANCE, enabled)
            .putBoolean("KEY_AUTO_TRACK_ATTENDANCE", enabled)
            .apply()
    }

    const val KEY_SHOW_RUNNING_BALANCE = "key_show_running_balance"

    fun isShowRunningBalanceEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_RUNNING_BALANCE, true)
    }

    fun setShowRunningBalanceEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SHOW_RUNNING_BALANCE, enabled)
            .apply()
    }
}
