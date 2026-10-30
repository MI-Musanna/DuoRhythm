package database.dao

import androidx.room.*
import database.entities.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE (profile_id = :profileId OR profile_id = 0 OR profile_id IS NULL) ORDER BY id DESC")
    fun getSubscriptions(profileId: Long): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE (profile_id = :profileId OR profile_id = 0 OR profile_id IS NULL) ORDER BY id DESC")
    fun getSubscriptionsSync(profileId: Long): List<SubscriptionEntity> = getSubscriptions(profileId)

    @Query("SELECT * FROM subscriptions WHERE (profile_id = :profileId OR profile_id = 0 OR profile_id IS NULL) ORDER BY id DESC")
    fun getSubscriptionsFlow(profileId: Long): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE (profile_id = :profileId OR profile_id = 0 OR profile_id IS NULL)")
    fun getActiveSubscriptions(profileId: Long): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    fun getSubscriptionById(id: Long): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSubscription(subscription: SubscriptionEntity): Long

    @Update
    fun updateSubscription(subscription: SubscriptionEntity): Int

    @Query("UPDATE subscriptions SET lastPaidMonthYear = :monthYear WHERE id = :id")
    fun updatePaidMonth(id: Long, monthYear: String?): Int

    @Query("DELETE FROM subscriptions WHERE id = :id")
    fun deleteSubscription(id: Long): Int

    @Query("DELETE FROM subscriptions WHERE profile_id = :profileId")
    fun deleteByProfileId(profileId: Long): Int
}
