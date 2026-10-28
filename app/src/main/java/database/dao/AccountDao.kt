package database.dao

import com.duorhythm.app.data.model.WalletBucketItem

interface AccountDao {
    fun getAccounts(profileId: Long): List<WalletBucketItem>
    fun getAccountById(id: Long): WalletBucketItem?
}
