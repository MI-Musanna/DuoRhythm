package database.dao

import androidx.room.Dao
import androidx.room.Query
import com.duorhythm.app.data.model.ClassScheduleItem

@Dao
interface TimetableDao {
    @Query("SELECT * FROM classes WHERE day_of_week = :dayOfWeek ORDER BY start_time ASC")
    fun getClassesForDay(dayOfWeek: Int): List<ClassScheduleItem>

    fun getClassesForDay(dayOfWeekName: String): List<ClassScheduleItem>

    fun getScheduleForDay(dayOfWeek: Int): List<ClassScheduleItem>

    fun getScheduleForDay(dayOfWeekName: String): List<ClassScheduleItem>
}
