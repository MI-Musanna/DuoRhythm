package com.pocketdev.duorhythm.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {

    /**
     * Reusable NumberFormat ensuring 2 decimal places with comma separation (e.g. 22,460.00).
     */
    fun createNumberFormat(): NumberFormat {
        return NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    /**
     * Formats raw balance/amount to standard currency string with 2 decimal places.
     * E.g. 22460.0 -> "৳22,460.00"
     * Completely eliminates any USD or exchange rate division logic.
     */
    fun formatRawCurrency(amount: Double, symbol: String = "৳"): String {
        val nf = createNumberFormat()
        return if (amount < 0) {
            "-$symbol" + nf.format(kotlin.math.abs(amount))
        } else {
            symbol + nf.format(amount)
        }
    }

    /**
     * Formats monthly income and expense brief for the Finance card.
     * E.g. (2000.0, 1700.0) -> "+৳2,000.00 / -৳1,700.00"
     */
    fun formatIncomeExpenseBrief(income: Double, expense: Double, symbol: String = "৳"): String {
        val nf = createNumberFormat()
        val incStr = nf.format(income)
        val expStr = nf.format(expense)
        return "+$symbol$incStr / -$symbol$expStr"
    }

    /**
     * Formats a raw number without currency symbol with 2 decimal places.
     */
    fun formatRawAmount(amount: Double): String {
        return createNumberFormat().format(amount)
    }
}
