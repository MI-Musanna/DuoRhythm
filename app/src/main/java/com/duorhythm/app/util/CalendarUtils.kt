package com.duorhythm.app.util

import android.content.Context
import com.duorhythm.app.data.local.pref.AppPreferences
import java.util.Calendar

object CalendarUtils {
    fun getFirstDayOfWeekConstant(context: Context): Int {
        return try {
            val prefs = AppPreferences(context)
            when (prefs.firstDayOfWeek.lowercase()) {
                "saturday" -> Calendar.SATURDAY
                "sunday" -> Calendar.SUNDAY
                "monday" -> Calendar.MONDAY
                else -> Calendar.SATURDAY
            }
        } catch (_: Throwable) {
            Calendar.SATURDAY
        }
    }

    fun getAppConfiguredCalendar(context: Context): Calendar {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = getFirstDayOfWeekConstant(context)
        return cal
    }
}
