package com.duorhythm.app.util

import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateRollValidator {
    fun getClampedDay(targetDay: Int, calendar: Calendar): Int {
        val maxDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        return targetDay.coerceIn(1, maxDaysInMonth)
    }

    fun isDueToday(sub: SubscriptionEntity, calendar: Calendar): Boolean {
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.time)

        // Already paid this month check
        if (sub.lastPaidMonthYear == currentMonthYear) return false

        return if (sub.isRange) {
            val effectiveStart = getClampedDay(sub.startDay, calendar)
            val effectiveEnd = getClampedDay(sub.endDay, calendar)
            if (effectiveStart <= effectiveEnd) {
                currentDay in effectiveStart..effectiveEnd
            } else {
                // Month wrap-around fallback
                currentDay >= effectiveStart || currentDay <= effectiveEnd
            }
        } else {
            val effectiveDue = getClampedDay(sub.dueDay, calendar)
            currentDay == effectiveDue
        }
    }
}
