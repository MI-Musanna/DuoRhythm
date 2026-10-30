package database.dao

import androidx.room.*
import database.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE (profile_id = :profileId OR profile_id = 0 OR profile_id IS NULL) AND date LIKE :monthYear || '%' ORDER BY date DESC, id DESC")
    fun getMonthlyTransactionsFlow(profileId: Long, monthYear: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE (profile_id = :profileId OR profile_id = 0 OR profile_id IS NULL) ORDER BY date DESC, id DESC")
    fun getAllTransactionsFlow(profileId: Long): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: TransactionEntity): Long

    @Delete
    fun deleteTransaction(transaction: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    fun deleteTransactionById(id: Long): Int
}
