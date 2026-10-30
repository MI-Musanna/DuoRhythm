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

    fun getLeadingEmptyCellCount(year: Int, month: Int, firstDayOfWeek: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfTargetMonth = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sun, 7 = Sat
        return (firstDayOfTargetMonth - firstDayOfWeek + 7) % 7
    }

    fun getAppConfiguredCalendar(context: Context): Calendar {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = getFirstDayOfWeekConstant(context)
        return cal
    }
}
