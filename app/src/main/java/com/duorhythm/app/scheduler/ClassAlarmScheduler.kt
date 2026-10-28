package com.duorhythm.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.receiver.ClassEndReceiver
import com.duorhythm.app.receiver.ScheduleReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ClassAlarmScheduler {

    const val ACTION_CLASS_PRE_ALERT = "com.duorhythm.app.ACTION_CLASS_PRE_ALERT"
    const val ACTION_CLASS_START = "com.duorhythm.app.ACTION_CLASS_START"
    const val ACTION_CLASS_END = "com.duorhythm.app.ACTION_CLASS_END"

    const val EXTRA_CLASS_ID = "extra_class_id"
    const val EXTRA_SUBJECT_NAME = "extra_subject_name"
    const val EXTRA_ROOM = "extra_room"
    const val EXTRA_TEACHER = "extra_teacher"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_END_TIME = "extra_end_time"
    const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"

    const val PREFS_NAME = "lifesync_prefs"
    const val KEY_IN_CLASS_DND = "in_class_dnd"
    const val KEY_SAVED_RINGER_MODE = "saved_ringer_mode"

    fun scheduleClassAlarms(context: Context, item: ClassScheduleItem) {
        AlarmSyncManager.scheduleClassAlarms(context, item)
    }

    fun cancelAlarmsForClass(context: Context, classId: Long) {
        AlarmSyncManager.cancelClassAlarms(context, classId)
    }

    fun cancelClassAlarms(context: Context, classId: Long) {
        cancelAlarmsForClass(context, classId)
    }

    fun cancelAlarmsForCourse(context: Context, courseId: Long) {
        val db = DatabaseHelper.getInstance(context)
        val course = db.getStudentSubjectById(courseId)
        val classes = db.getAllClasses().filter { cls ->
            course != null && (cls.subjectName.equals(course.name, ignoreCase = true) ||
                    (course.code.isNotBlank() && cls.subjectName.contains(course.code, ignoreCase = true)))
        }
        for (cls in classes) {
            cancelAlarmsForClass(context, cls.id)
        }
    }

    fun rescheduleAllActiveAlarms(context: Context) {
        AlarmSyncManager.resyncAll(context)
    }

    fun rescheduleAll(context: Context) {
        AlarmSyncManager.resyncAll(context)
    }

    fun setExactAlarm(alarmManager: AlarmManager, triggerMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    fun parseTime(timeStr: String): Pair<Int, Int>? {
        val clean = timeStr.trim()
        val formats = listOf("hh:mm a", "h:mm a", "hh:mma", "h:mma", "HH:mm", "H:mm")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val d = sdf.parse(clean)
                if (d != null) {
                    val cal = Calendar.getInstance().apply { time = d }
                    return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun parseMinutes(timeStr: String): Int {
        val pair = parseTime(timeStr) ?: return 0
        return pair.first * 60 + pair.second
    }
}
