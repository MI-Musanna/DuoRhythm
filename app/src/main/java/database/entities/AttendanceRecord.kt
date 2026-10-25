package database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1,
    val subjectName: String,
    val date: String, // format: "yyyy-MM-dd"
    val dayOfWeek: String, // e.g. "Sunday"
    val status: String // "PRESENT" or "ABSENT"
)
