package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.entities.ClassTypeEntity

@Dao
interface ClassTypeDao {
    @Query("SELECT * FROM class_types WHERE profile_id = :profileId ORDER BY is_default DESC, name ASC")
    fun getTypesByProfile(profileId: Long): List<ClassTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertType(type: ClassTypeEntity): Long

    @Query("DELETE FROM class_types WHERE id = :id AND is_default = 0")
    fun deleteType(id: Long)

    fun ensureDefaultTypes(profileId: Long)
}
