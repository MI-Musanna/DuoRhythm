package com.duorhythm.app

import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.data.model.WalletBucketItem
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(DatabaseHelper::class)
class ShadowDatabaseHelper {

    companion object {
        private val buckets = mutableMapOf<Long, WalletBucketItem>()
        private var nextBucketId = 1L
        private var nextTxId = 1L

        fun reset() {
            buckets.clear()
            nextBucketId = 1L
            nextTxId = 1L
        }
    }

    @Implementation
    fun insertWalletBucket(bucket: WalletBucketItem): Long {
        val id = nextBucketId++
        buckets[id] = bucket.copy(id = id)
        return id
    }

    @Implementation
    fun getWalletBucketById(id: Long): WalletBucketItem? {
        return buckets[id]
    }

    @Implementation
    fun insertTransaction(tx: TransactionItem): Long {
        val id = nextTxId++
        val delta = if (tx.type == "INCOME") tx.amount else -tx.amount
        val targetId = tx.bucketId
        val bucket = buckets[targetId]
        if (bucket != null) {
            buckets[targetId] = bucket.copy(balance = bucket.balance + delta)
        }
        return id
    }

    @Implementation
    fun executeTransfer(
        fromBucketId: Long,
        toBucketId: Long,
        amount: Double,
        date: String,
        currency: String,
        note: String,
        title: String
    ): Boolean {
        if (fromBucketId <= 0L || toBucketId <= 0L || fromBucketId == toBucketId || amount <= 0.0) {
            return false
        }
        val fromBucket = buckets[fromBucketId] ?: return false
        val toBucket = buckets[toBucketId] ?: return false

        buckets[fromBucketId] = fromBucket.copy(balance = fromBucket.balance - amount)
        buckets[toBucketId] = toBucket.copy(balance = toBucket.balance + amount)
        return true
    }
}
