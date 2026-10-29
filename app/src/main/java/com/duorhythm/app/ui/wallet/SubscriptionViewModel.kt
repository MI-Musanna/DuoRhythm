package com.duorhythm.app.ui.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.duorhythm.app.data.repository.SubscriptionRepository
import database.entities.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SubscriptionRepository(application)

    fun getSubscriptionsFlow(profileId: Long): Flow<List<SubscriptionEntity>> =
        repository.getSubscriptionsFlow(profileId)
}
