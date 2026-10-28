package database.dao

import com.duorhythm.app.data.model.StudentSubject

interface CourseDao {
    fun getCoursesByProfile(profileId: Long): List<StudentSubject>
    fun getCourseById(id: Long): StudentSubject?
    fun deleteCourseById(id: Long)
}
