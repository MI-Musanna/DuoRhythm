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
}
