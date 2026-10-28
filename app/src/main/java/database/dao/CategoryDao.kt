package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM transaction_categories ORDER BY name ASC")
    fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM transaction_categories ORDER BY name ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM transaction_categories WHERE type = :type OR type = 'BOTH' ORDER BY name ASC")
    fun getCategoriesByType(type: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("DELETE FROM transaction_categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    fun getTransactionCountByCategory(categoryName: String): Int
    suspend fun deleteCategoryAndReassign(id: Long, categoryName: String): Boolean
}
