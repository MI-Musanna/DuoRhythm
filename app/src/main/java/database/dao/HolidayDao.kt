package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.entities.HolidayRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
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

    @Query("DELETE FROM holiday_records WHERE date = :date")
    suspend fun removeHoliday(date: String)
}
