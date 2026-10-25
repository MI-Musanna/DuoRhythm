package database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holiday_records")
data class HolidayRecord(
    @PrimaryKey val date: String, // format "yyyy-MM-dd"
    val profileId: Long = 1,
    val title: String = "Holiday"
)
