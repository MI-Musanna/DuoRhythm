package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.entities.ClassTypeEntity

@Dao
interface ClassTypeDao {
    @Query("SELECT * FROM class_types WHERE profile_id = :profileId ORDER BY name ASC")
    fun getTypesByProfile(profileId: Long): List<ClassTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertType(type: ClassTypeEntity): Long

    @Query("DELETE FROM class_types WHERE id = :id")
    fun deleteType(id: Long)

    @Query("UPDATE class_types SET name = :newName WHERE id = :id")
    fun updateClassTypeName(id: Long, newName: String)

    fun ensureDefaultTypes(profileId: Long)
}
