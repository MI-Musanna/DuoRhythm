package com.duorhythm.app.data.repository

import android.content.Context
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.scheduler.AssessmentAlarmScheduler
import com.duorhythm.app.scheduler.ClassAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CourseRepository(private val context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    suspend fun deleteCourseCascade(courseId: Long, courseCode: String, profileId: Long): Boolean =
        withContext(Dispatchers.IO) {
            ClassAlarmScheduler.cancelAlarmsForCourse(context, courseId)
            AssessmentAlarmScheduler.cancelAlarmsForCourse(context, courseId)
            dbHelper.deleteCourseCascade(courseId, courseCode, profileId)
        }

    fun deleteCourseCascadeSync(courseId: Long, courseCode: String, profileId: Long): Boolean {
        ClassAlarmScheduler.cancelAlarmsForCourse(context, courseId)
        AssessmentAlarmScheduler.cancelAlarmsForCourse(context, courseId)
        return dbHelper.deleteCourseCascade(courseId, courseCode, profileId)
    }

    fun updateCourseAndSyncDependencies(
        oldCourse: com.duorhythm.app.data.model.StudentSubject,
        updatedCourse: com.duorhythm.app.data.model.StudentSubject
    ): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            dbHelper.updateStudentSubject(updatedCourse)
            dbHelper.timetableDao.syncCourseDetailsToClasses(
                profileId = updatedCourse.profileId,
                courseCode = oldCourse.code,
                courseName = updatedCourse.name,
                teacherName = updatedCourse.instructorName,
                room = updatedCourse.room
            )
            dbHelper.attendanceDao.syncCourseDetailsToAttendance(
                profileId = updatedCourse.profileId,
                oldCourseName = oldCourse.name,
                newCourseName = updatedCourse.name
            )
            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            db.endTransaction()
        }
    }

    suspend fun updateCourseAndSyncDependenciesAsync(
        oldCourse: com.duorhythm.app.data.model.StudentSubject,
        updatedCourse: com.duorhythm.app.data.model.StudentSubject
    ): Boolean = withContext(Dispatchers.IO) {
        updateCourseAndSyncDependencies(oldCourse, updatedCourse)
    }
}
