package com.duorhythm.app.ui.wallet

import androidx.lifecycle.ViewModel

class WalletViewModel : ViewModel() {

    /**
     * Calculates projected monthly outflow by combining recorded expenses
     * and unpaid recurring subscriptions for the month.
     */
    fun calculateProjectedMonthlyOutflow(recordedExpenses: Double, unpaidSubscriptionsTotal: Double): Double {
        return recordedExpenses + unpaidSubscriptionsTotal
    }
}
