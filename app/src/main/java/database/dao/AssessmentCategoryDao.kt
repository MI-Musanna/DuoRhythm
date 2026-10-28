package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.entities.AssessmentCategoryEntity

@Dao
interface AssessmentCategoryDao {
    @Query("SELECT * FROM assessment_categories WHERE profile_id = :profileId ORDER BY name ASC")
    fun getCategoriesByProfile(profileId: Long): List<AssessmentCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategory(category: AssessmentCategoryEntity): Long

    @Query("DELETE FROM assessment_categories WHERE id = :id")
    fun deleteCategory(id: Long)

    @Query("UPDATE assessment_categories SET name = :newName WHERE id = :id")
    fun updateCategoryName(id: Long, newName: String)

    fun ensureDefaultCategories(profileId: Long)
}
