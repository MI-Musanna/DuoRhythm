package com.duorhythm.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.CarouselClassItem
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.data.model.ClassStatus
import com.duorhythm.app.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * HomeViewModel providing direct 1:1 binding of raw local currency database values
 * and real-time daily class schedule carousel state mapping.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = DatabaseHelper.getInstance(application)

    fun getTotalRawBalance(): Double {
        return dbHelper.getTotalBalance()
    }

    fun getMonthlyIncome(): Double {
        return dbHelper.getMonthlyIncome()
    }

    fun getMonthlyExpense(): Double {
        return dbHelper.getMonthlyExpense()
    }

    fun getFormattedBalance(): String {
        return CurrencyUtils.formatRawCurrency(getTotalRawBalance(), "৳")
    }

    fun getFormattedSubtitle(): String {
        return CurrencyUtils.formatIncomeExpenseBrief(getMonthlyIncome(), getMonthlyExpense(), "৳")
    }

    /**
     * Loads all classes for the active day and maps each class to its real-time state:
     * - State 1: IN_SESSION_NOW (currentTime between startTime and endTime)
     * - State 2: UPCOMING_NEXT (first class where currentTime < startTime)
     * - State 3: COMPLETED (currentTime > endTime)
     */
    fun getDailyScheduleCarouselItems(): List<CarouselClassItem> {
        val calendar = Calendar.getInstance()
        var dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 1=Mon ... 7=Sun
        if (dayOfWeek <= 0) dayOfWeek = 7

        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val allClasses = dbHelper.getAllClasses()
        val todayClasses = allClasses.filter { it.dayOfWeek == dayOfWeek }
            .sortedBy { parseMinutes(it.startTime) }

        if (todayClasses.isEmpty()) {
            return listOf(
                CarouselClassItem(
                    classItem = null,
                    status = ClassStatus.COMPLETED,
                    totalCount = 0,
                    positionIndex = 0,
                    isPlaceholder = true
                )
            )
        }

        var foundUpcomingNext = false
        val items = mutableListOf<CarouselClassItem>()

        for (item in todayClasses) {
            val startMin = parseMinutes(item.startTime)
            var endMin = parseMinutes(item.endTime)
            if (endMin < startMin) {
                endMin += 24 * 60
            }

            val status = when {
                currentMinutes in startMin..endMin -> ClassStatus.IN_SESSION_NOW
                currentMinutes > endMin -> ClassStatus.COMPLETED
                !foundUpcomingNext && currentMinutes < startMin -> {
                    foundUpcomingNext = true
                    ClassStatus.UPCOMING_NEXT
                }
                else -> ClassStatus.UPCOMING_NEXT
            }

            items.add(
                CarouselClassItem(
                    classItem = item,
                    status = status,
                    totalCount = todayClasses.size,
                    positionIndex = items.size
                )
            )
        }

        return items
    }

    private fun parseMinutes(timeStr: String): Int {
        val clean = timeStr.trim()
        val formats = listOf("hh:mm a", "h:mm a", "hh:mma", "h:mma", "HH:mm", "H:mm")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val d = sdf.parse(clean)
                if (d != null) {
                    val cal = Calendar.getInstance().apply { time = d }
                    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                }
            } catch (_: Exception) {}
        }
        return 0
    }
}
