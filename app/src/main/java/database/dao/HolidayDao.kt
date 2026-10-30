package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.entities.HolidayRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
    @Query("SELECT COUNT(*) > 0 FROM holiday_records WHERE date = :date AND (profile_id = :currentProfileId OR profile_id = 0 OR profile_id IS NULL)")
    fun isHoliday(date: String, currentProfileId: Long): Flow<Boolean>

    @Query("SELECT COUNT(*) > 0 FROM holiday_records WHERE date = :date AND (profile_id = :profileId OR profile_id = 0 OR profile_id IS NULL)")
    fun isHoliday(profileId: Long, date: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM holiday_records WHERE date = :date")
    fun isHoliday(date: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoliday(record: HolidayRecord)

    suspend fun setHoliday(date: String) {
        insertHoliday(HolidayRecord(date = date))
    }

    suspend fun setHoliday(record: HolidayRecord) {
        insertHoliday(record)
    }

    @Query("DELETE FROM holiday_records WHERE date = :date AND (profile_id = :currentProfileId OR profile_id = 0 OR profile_id IS NULL)")
    suspend fun removeHoliday(date: String, currentProfileId: Long)

    @Query("DELETE FROM holiday_records WHERE date = :date")
    suspend fun removeHoliday(date: String)

    @Query("DELETE FROM holiday_records WHERE profile_id = :profileId")
    fun deleteByProfileId(profileId: Long)
}
