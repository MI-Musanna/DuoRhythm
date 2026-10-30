package database.dao

import androidx.room.*
import database.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE (profile_id = :profileId OR profileId = :profileId) AND (strftime('%Y-%m', date / 1000, 'unixepoch') = :monthYear OR date LIKE :monthYear || '%') ORDER BY date DESC, id DESC")
    fun getMonthlyTransactionsFlow(profileId: Long, monthYear: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE profile_id = :profileId ORDER BY date DESC, id DESC")
    fun getAllTransactionsFlow(profileId: Long): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: TransactionEntity): Long

    @Delete
    fun deleteTransaction(transaction: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    fun deleteTransactionById(id: Long): Int
}
