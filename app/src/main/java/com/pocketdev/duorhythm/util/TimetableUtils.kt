package com.pocketdev.duorhythm.util

import com.pocketdev.duorhythm.data.local.pref.AppSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class TimetableDayInfo(
    val dbDay: Int,             // 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
    val calendarDay: Int,       // Calendar.SATURDAY, etc.
    val dayName: String,        // "Saturday"
    val shortName: String,      // "Sat"
    val dayOfMonth: Int,        // e.g. 17
    val isToday: Boolean,
    val dateString: String      // formatted yyyy-MM-dd
)

object TimetableUtils {

    fun calendarDayToDbDay(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    fun dbDayToCalendarDay(dbDay: Int): Int {
        return when (dbDay) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }
    }

    fun getDayFullName(dbDay: Int): String {
        return when (dbDay) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Monday"
        }
    }

    fun getDayShortName(dbDay: Int): String {
        return when (dbDay) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> "Mon"
        }
    }

    /**
     * Calculates the current 7-day week range starting from configured start of week day.
     * E.g., if startOfWeek is Calendar.SATURDAY:
     * returns 7 DayInfo objects ordered: Sat -> Sun -> Mon -> Tue -> Wed -> Thu -> Fri
     */
    fun getWeekDays(startOfWeekCalendarDay: Int): List<TimetableDayInfo> {
        val now = Calendar.getInstance()
        val currentCalendarDay = now.get(Calendar.DAY_OF_WEEK)
        val todayDayOfYear = now.get(Calendar.DAY_OF_YEAR)
        val todayYear = now.get(Calendar.YEAR)

        var diff = currentCalendarDay - startOfWeekCalendarDay
        if (diff < 0) {
            diff += 7
        }

        val startCal = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_MONTH, -diff)
        }

        val list = mutableListOf<TimetableDayInfo>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (i in 0 until 7) {
            val calDay = startCal.get(Calendar.DAY_OF_WEEK)
            val dbDay = calendarDayToDbDay(calDay)
            val dayOfMonth = startCal.get(Calendar.DAY_OF_MONTH)
            val isToday = (startCal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear && startCal.get(Calendar.YEAR) == todayYear)

            list.add(
                TimetableDayInfo(
                    dbDay = dbDay,
                    calendarDay = calDay,
                    dayName = getDayFullName(dbDay),
                    shortName = getDayShortName(dbDay),
                    dayOfMonth = dayOfMonth,
                    isToday = isToday,
                    dateString = dateFormat.format(startCal.time)
                )
            )

            startCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return list
    }
}
