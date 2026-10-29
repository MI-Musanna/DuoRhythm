package com.duorhythm.app.data.repository

import android.content.Context
import com.duorhythm.app.data.local.db.DatabaseHelper
import database.dao.SubscriptionDao
import database.entities.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

class SubscriptionRepository(private val context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)
    val subscriptionDao: SubscriptionDao = dbHelper.subscriptionDao

    fun getSubscriptions(profileId: Long): List<SubscriptionEntity> =
        subscriptionDao.getSubscriptions(profileId)

    fun getSubscriptionsFlow(profileId: Long): Flow<List<SubscriptionEntity>> =
        subscriptionDao.getSubscriptionsFlow(profileId)

    fun getActiveSubscriptions(profileId: Long): List<SubscriptionEntity> =
        subscriptionDao.getActiveSubscriptions(profileId)

    fun getSubscriptionById(id: Long): SubscriptionEntity? =
        subscriptionDao.getSubscriptionById(id)

    fun insertSubscription(subscription: SubscriptionEntity): Long =
        subscriptionDao.insertSubscription(subscription)

    fun updateSubscription(subscription: SubscriptionEntity): Int =
        subscriptionDao.updateSubscription(subscription)

    fun updatePaidMonth(id: Long, monthYear: String?): Int =
        subscriptionDao.updatePaidMonth(id, monthYear)

    fun deleteSubscription(id: Long): Int =
        subscriptionDao.deleteSubscription(id)

    fun deleteByProfileId(profileId: Long): Int =
        subscriptionDao.deleteByProfileId(profileId)
}
