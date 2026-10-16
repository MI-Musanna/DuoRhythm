package com.pocketdev.duorhythm.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.util.CurrencyUtils

/**
 * HomeViewModel providing direct 1:1 binding of raw local currency database values
 * without any currency exchange division or math conversions.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = DatabaseHelper.getInstance(application)

    /**
     * Pure 100% raw database total balance (e.g. 22460.0).
     * Strictly eliminates any USD or exchange rate division logic.
     */
    fun getTotalRawBalance(): Double {
        return dbHelper.getTotalBalance()
    }

    /**
     * Pure 100% raw monthly income (e.g. 2000.0).
     */
    fun getMonthlyIncome(): Double {
        return dbHelper.getMonthlyIncome()
    }

    /**
     * Pure 100% raw monthly expense (e.g. 1700.0).
     */
    fun getMonthlyExpense(): Double {
        return dbHelper.getMonthlyExpense()
    }

    /**
     * Formats total net balance to standard currency string (e.g. "৳22,460.00").
     */
    fun getFormattedBalance(): String {
        return CurrencyUtils.formatRawCurrency(getTotalRawBalance(), "৳")
    }

    /**
     * Formats monthly income/expense brief (e.g. "+৳2,000.00 / -৳1,700.00").
     */
    fun getFormattedSubtitle(): String {
        return CurrencyUtils.formatIncomeExpenseBrief(getMonthlyIncome(), getMonthlyExpense(), "৳")
    }
}
