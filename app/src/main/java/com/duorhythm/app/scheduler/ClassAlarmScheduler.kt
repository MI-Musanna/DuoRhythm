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
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val startTimePair = parseTime(item.startTime) ?: return
        val endTimePair = parseTime(item.endTime) ?: return

        val startHour = startTimePair.first
        val startMin = startTimePair.second
        val endHour = endTimePair.first
        val endMin = endTimePair.second

        val startMinutes = startHour * 60 + startMin
        var endMinutes = endHour * 60 + endMin
        if (endMinutes < startMinutes) {
            endMinutes += 24 * 60
        }
        val durationMs = (endMinutes - startMinutes) * 60 * 1000L

        val targetCalendarDay = when (item.dayOfWeek) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentDayOfWeek = targetCal.get(Calendar.DAY_OF_WEEK)
        var daysUntil = targetCalendarDay - currentDayOfWeek
        if (daysUntil < 0) {
            daysUntil += 7
        }
        targetCal.add(Calendar.DAY_OF_YEAR, daysUntil)

        var startMs = targetCal.timeInMillis
        var endMs = startMs + durationMs

        // If today's class has already ended, schedule for next week
        if (endMs <= System.currentTimeMillis()) {
            startMs += 7 * 24 * 60 * 60 * 1000L
            endMs += 7 * 24 * 60 * 60 * 1000L
        }

        val preAlertMs = startMs - (5 * 60 * 1000L)

        // 1. Pre-alert Alarm (5 mins before)
        // Request code: (classId * 10 + 1)
        if (preAlertMs > System.currentTimeMillis()) {
            val preAlertIntent = Intent(context, ScheduleReceiver::class.java).apply {
                action = ACTION_CLASS_PRE_ALERT
                putExtra(EXTRA_CLASS_ID, item.id)
                putExtra(EXTRA_SUBJECT_NAME, item.subjectName)
                putExtra(EXTRA_ROOM, item.room)
                putExtra(EXTRA_TEACHER, item.teacher)
                putExtra(EXTRA_START_TIME, item.startTime)
                putExtra(EXTRA_END_TIME, item.endTime)
                putExtra(EXTRA_DAY_OF_WEEK, item.dayOfWeek)
            }
            val preAlertPi = PendingIntent.getBroadcast(
                context,
                (item.id * 10 + 1).toInt(),
                preAlertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setExactAlarm(alarmManager, preAlertMs, preAlertPi)
        }

        // 2. Class Start Alarm (Mute / DND)
        // Request code: (classId * 10 + 2)
        if (startMs > System.currentTimeMillis()) {
            val startIntent = Intent(context, ScheduleReceiver::class.java).apply {
                action = ACTION_CLASS_START
                putExtra(EXTRA_CLASS_ID, item.id)
                putExtra(EXTRA_SUBJECT_NAME, item.subjectName)
                putExtra(EXTRA_ROOM, item.room)
                putExtra(EXTRA_TEACHER, item.teacher)
                putExtra(EXTRA_START_TIME, item.startTime)
                putExtra(EXTRA_END_TIME, item.endTime)
                putExtra(EXTRA_DAY_OF_WEEK, item.dayOfWeek)
            }
            val startPi = PendingIntent.getBroadcast(
                context,
                (item.id * 10 + 2).toInt(),
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setExactAlarm(alarmManager, startMs, startPi)
        }

        // 3. Class End Alarm (Unmute check & back-to-back transition)
        // Request code: (classId * 10 + 3)
        if (endMs > System.currentTimeMillis()) {
            val endIntent = Intent(context, ClassEndReceiver::class.java).apply {
                action = ACTION_CLASS_END
                putExtra(EXTRA_CLASS_ID, item.id)
                putExtra(EXTRA_SUBJECT_NAME, item.subjectName)
                putExtra(EXTRA_ROOM, item.room)
                putExtra(EXTRA_TEACHER, item.teacher)
                putExtra(EXTRA_START_TIME, item.startTime)
                putExtra(EXTRA_END_TIME, item.endTime)
                putExtra(EXTRA_DAY_OF_WEEK, item.dayOfWeek)
            }
            val endPi = PendingIntent.getBroadcast(
                context,
                (item.id * 10 + 3).toInt(),
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setExactAlarm(alarmManager, endMs, endPi)
        }
    }

    fun cancelAlarmsForClass(context: Context, classId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCodes = listOf(
            (classId * 10 + 1).toInt(),
            (classId * 10 + 2).toInt(),
            (classId * 10 + 3).toInt()
        )
        for (code in requestCodes) {
            val receiverClass = if (code % 10 == 3) ClassEndReceiver::class.java else ScheduleReceiver::class.java
            val intent = Intent(context, receiverClass)
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    fun cancelClassAlarms(context: Context, classId: Long) {
        cancelAlarmsForClass(context, classId)
    }

    fun rescheduleAllActiveAlarms(context: Context) {
        val db = DatabaseHelper.getInstance(context)
        val classes = db.getAllClasses()
        for (item in classes) {
            cancelAlarmsForClass(context, item.id)
            scheduleClassAlarms(context, item)
        }
    }

    fun rescheduleAll(context: Context) {
        rescheduleAllActiveAlarms(context)
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
