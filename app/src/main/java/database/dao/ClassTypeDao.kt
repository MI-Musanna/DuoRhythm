package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.entities.ClassTypeEntity

@Dao
interface ClassTypeDao {
    @Query("SELECT * FROM class_types WHERE (profile_id = :profileId OR profile_id = 0 OR profile_id IS NULL) ORDER BY name ASC")
    fun getTypesByProfile(profileId: Long): List<ClassTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertType(type: ClassTypeEntity): Long

    @Query("DELETE FROM class_types WHERE id = :id")
    fun deleteType(id: Long)

    @Query("UPDATE class_types SET name = :newName WHERE id = :id")
    fun updateClassTypeName(id: Long, newName: String)

    @Query("UPDATE class_types SET name = :newName, color_hex = :newColorHex WHERE id = :id")
    fun updateClassType(id: Long, newName: String, newColorHex: String)

    @Query("UPDATE class_types SET color_hex = :newColorHex WHERE id = :id")
    fun updateClassTypeColor(id: Long, newColorHex: String)

    fun ensureDefaultTypes(profileId: Long)
}
