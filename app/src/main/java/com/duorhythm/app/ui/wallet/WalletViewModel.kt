package com.duorhythm.app.ui.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TransactionItem
import database.dao.SubscriptionDao
import database.entities.SubscriptionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class WalletViewModel(application: Application = com.duorhythm.app.LifeSyncApp.instance) : AndroidViewModel(application) {

    private val dbHelper = DatabaseHelper.getInstance(application)
    private val subscriptionDao: SubscriptionDao = dbHelper.subscriptionDao

    private val vmJob = SupervisorJob()
    val viewModelScope = CoroutineScope(Dispatchers.Main + vmJob)

    private val _subscriptionsFlow = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
    val subscriptionsFlow: StateFlow<List<SubscriptionEntity>> = _subscriptionsFlow.asStateFlow()

    private val _monthlyTransactionsFlow = MutableStateFlow<List<TransactionItem>>(emptyList())
    val monthlyTransactionsFlow: StateFlow<List<TransactionItem>> = _monthlyTransactionsFlow.asStateFlow()

    fun loadSubscriptions(profileId: Long) {
        viewModelScope.launch {
            try {
                subscriptionDao.getSubscriptionsFlow(profileId).collect { list ->
                    _subscriptionsFlow.value = list
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun addSubscription(subscription: SubscriptionEntity) {
        viewModelScope.launch {
            try {
                subscriptionDao.insertSubscription(subscription)
                val freshList = subscriptionDao.getSubscriptionsSync(subscription.profileId)
                _subscriptionsFlow.value = freshList
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun loadMonthlyTransactions(profileId: Long, monthYear: String) {
        viewModelScope.launch {
            try {
                dbHelper.getMonthlyTransactionsFlow(profileId, monthYear).collect { list ->
                    _monthlyTransactionsFlow.value = list
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Calculates projected monthly outflow by combining recorded expenses
     * and unpaid recurring subscriptions for the month.
     */
    fun calculateProjectedMonthlyOutflow(recordedExpenses: Double, unpaidSubscriptionsTotal: Double): Double {
        return recordedExpenses + unpaidSubscriptionsTotal
    }

    override fun onCleared() {
        super.onCleared()
        vmJob.cancel()
    }
}
