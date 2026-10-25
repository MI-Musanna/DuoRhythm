package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import database.entities.ProfileEntity

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY slotNumber ASC")
    fun getAllProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    fun getProfileById(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE slotNumber = :slotNumber LIMIT 1")
    fun getProfileBySlot(slotNumber: Int): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    fun getProfileCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProfile(profile: ProfileEntity): Long

    @Update
    fun updateProfile(profile: ProfileEntity)
}
