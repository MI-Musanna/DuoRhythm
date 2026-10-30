package com.duorhythm.app.ui.wallet

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.data.model.WalletBucketItem
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
    val viewModelScope = CoroutineScope(Dispatchers.Main.immediate + vmJob)

    private val _subscriptionsFlow = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
    val subscriptionsFlow: StateFlow<List<SubscriptionEntity>> = _subscriptionsFlow.asStateFlow()

    private val _monthlyTransactionsFlow = MutableStateFlow<List<TransactionItem>>(emptyList())
    val monthlyTransactionsFlow: StateFlow<List<TransactionItem>> = _monthlyTransactionsFlow.asStateFlow()

    private val _accountsFlow = MutableStateFlow<List<WalletBucketItem>>(emptyList())
    val accountsFlow: StateFlow<List<WalletBucketItem>> = _accountsFlow.asStateFlow()

    fun loadAccounts(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _accountsFlow.value = dbHelper.accountDao.getAccounts(profileId)
            } catch (e: Throwable) {
                Log.e("DUO_WALLET", "Failed loading accounts in ViewModel", e)
            }
        }
    }

    fun loadSubscriptions(profileId: Long) {
        viewModelScope.launch {
            try {
                subscriptionDao.getSubscriptionsFlow(profileId).collect { list ->
                    _subscriptionsFlow.value = list
                }
            } catch (e: Throwable) {
                Log.e("DUO_WALLET", "Failed loading subscriptions in ViewModel", e)
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
                Log.e("DUO_WALLET", "Failed adding subscription in ViewModel", e)
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
                Log.e("DUO_WALLET", "Failed loading transactions in ViewModel", e)
            }
        }
    }

    /**
     * Calculates projected monthly outflow by combining recorded expenses
     * and unpaid recurring subscriptions remaining in the current billing cycle.
     */
    fun calculateProjectedMonthlyOutflow(recordedExpenses: Double, unpaidSubscriptionsTotal: Double): Double {
        return recordedExpenses + unpaidSubscriptionsTotal
    }

    override fun onCleared() {
        super.onCleared()
        vmJob.cancel()
    }
}
