package database.dao

import androidx.room.Dao
import androidx.room.Query
import com.duorhythm.app.data.model.ClassScheduleItem

@Dao
interface TimetableDao {
    @Query("SELECT * FROM classes WHERE (profile_id = :currentProfileId OR profile_id = 0 OR profile_id IS NULL) AND day_of_week = :dayOfWeek ORDER BY start_time ASC")
    fun getClassesForDay(dayOfWeek: Int, currentProfileId: Long): List<ClassScheduleItem>

    @Query("SELECT * FROM classes WHERE day_of_week = :dayOfWeek ORDER BY start_time ASC")
    fun getClassesForDay(dayOfWeek: Int): List<ClassScheduleItem>

    fun getClassesForDay(dayOfWeekName: String): List<ClassScheduleItem>

    fun getScheduleForDay(dayOfWeek: Int): List<ClassScheduleItem>

    fun getScheduleForDay(dayOfWeekName: String): List<ClassScheduleItem>

    @Query("DELETE FROM classes WHERE profile_id = :profileId")
    fun deleteByProfileId(profileId: Long)

    fun syncCourseDetailsToClasses(
        profileId: Long,
        courseCode: String,
        courseName: String,
        teacherName: String,
        room: String
    ): Int

    fun getNextImmediateClass(profileId: Long, dayOfWeek: Int, currentClassEndTime: String): ClassScheduleItem?
}
