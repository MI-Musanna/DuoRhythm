package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.entities.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE date = :date AND (profile_id = :currentProfileId OR profile_id = 0 OR profile_id IS NULL)")
    fun getRecordsByDate(date: String, currentProfileId: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getRecordsByDate(date: String): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordAttendance(record: AttendanceRecord)

    @Query("DELETE FROM attendance_records WHERE subjectName = :subjectName AND date = :date AND (profile_id = :currentProfileId OR profile_id = 0 OR profile_id IS NULL)")
    suspend fun deleteRecord(subjectName: String, date: String, currentProfileId: Long)

    @Query("DELETE FROM attendance_records WHERE subjectName = :subjectName AND date = :date")
    suspend fun deleteRecord(subjectName: String, date: String)

    @Query("DELETE FROM attendance_records WHERE profile_id = :profileId")
    fun deleteByProfileId(profileId: Long)

    fun syncCourseDetailsToAttendance(
        profileId: Long,
        oldCourseName: String,
        newCourseName: String
    ): Int
}
