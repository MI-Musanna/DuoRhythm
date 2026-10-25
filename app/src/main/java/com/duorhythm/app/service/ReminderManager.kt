package com.duorhythm.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.SubjectWorkItem
import com.duorhythm.app.receiver.ReminderReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReminderManager {

    const val CHANNEL_ID = "lifesync_reminders_channel"
    const val CHANNEL_NAME = "Class & Exam Reminders"
    const val ACTION_REMINDER = "com.duorhythm.app.ACTION_REMINDER"

    const val EXTRA_TYPE = "extra_reminder_type"
    const val EXTRA_ID = "extra_reminder_id"
    const val EXTRA_TITLE = "extra_reminder_title"
    const val EXTRA_MESSAGE = "extra_reminder_message"

    private const val PREFS_NAME = "lifesync_prefs"
    private const val KEY_REMINDERS_ENABLED = "key_reminders_enabled"

    fun isRemindersEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMINDERS_ENABLED, true)
    }

    fun setRemindersEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REMINDERS_ENABLED, enabled)
            .apply()

        if (enabled) {
            rescheduleAll(context)
        } else {
            cancelAll(context)
        }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority notifications for upcoming classes and exam deadlines"
                enableLights(true)
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun scheduleClassReminder(context: Context, classItem: ClassScheduleItem) {
        if (!isRemindersEnabled(context)) return
        com.duorhythm.app.scheduler.ClassAlarmScheduler.scheduleClassAlarms(context, classItem)
    }

    fun cancelClassReminder(context: Context, classId: Long) {
        com.duorhythm.app.scheduler.ClassAlarmScheduler.cancelClassAlarms(context, classId)
    }

    fun scheduleExamReminder(context: Context, examItem: SubjectWorkItem, subjectName: String) {
        if (!isRemindersEnabled(context) || examItem.status == "DONE") return
        if (examItem.endDate.isBlank()) return

        val triggerMs = calculateExamTriggerMs(examItem.endDate)
        if (triggerMs <= System.currentTimeMillis()) return

        val message = "Subject: $subjectName • Due: ${examItem.endDate}" +
                if (examItem.description.isNotBlank()) " • ${examItem.description}" else ""

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_TYPE, "EXAM")
            putExtra(EXTRA_ID, examItem.id)
            putExtra(EXTRA_TITLE, "Exam Reminder: ${examItem.title}")
            putExtra(EXTRA_MESSAGE, message)
        }

        val requestCode = (200000 + examItem.id).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        }
    }

    fun cancelExamReminder(context: Context, examId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        val requestCode = (200000 + examId).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun rescheduleAll(context: Context) {
        createNotificationChannel(context)
        if (!isRemindersEnabled(context)) return

        val db = DatabaseHelper.getInstance(context)
        val classes = db.getAllClasses()
        for (c in classes) {
            scheduleClassReminder(context, c)
        }

        val subjects = db.getAllStudentSubjects()
        for (s in subjects) {
            val exams = db.getItemsForSubject(s.id, "EXAM")
            for (exam in exams) {
                if (exam.status != "DONE") {
                    scheduleExamReminder(context, exam, s.name)
                }
            }
        }
    }

    private fun cancelAll(context: Context) {
        val db = DatabaseHelper.getInstance(context)
        val classes = db.getAllClasses()
        for (c in classes) {
            cancelClassReminder(context, c.id)
        }
        val subjects = db.getAllStudentSubjects()
        for (s in subjects) {
            val exams = db.getItemsForSubject(s.id, "EXAM")
            for (exam in exams) {
                cancelExamReminder(context, exam.id)
            }
        }
    }

    fun parseTimeString(timeStr: String): Pair<Int, Int>? {
        val clean = timeStr.trim()
        val formats = listOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")
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

    private fun calculateNextClassTriggerMs(dayOfWeek: Int, hourOfDay: Int, minute: Int): Long {
        val targetCal = Calendar.getInstance()
        targetCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        targetCal.set(Calendar.MINUTE, minute)
        targetCal.set(Calendar.SECOND, 0)
        targetCal.set(Calendar.MILLISECOND, 0)

        // 5 minutes before class starts
        targetCal.add(Calendar.MINUTE, -5)

        val targetCalendarDay = when (dayOfWeek) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }

        val currentDayOfWeek = targetCal.get(Calendar.DAY_OF_WEEK)
        var daysUntil = targetCalendarDay - currentDayOfWeek
        if (daysUntil < 0 || (daysUntil == 0 && targetCal.timeInMillis <= System.currentTimeMillis())) {
            daysUntil += 7
        }
        targetCal.add(Calendar.DAY_OF_YEAR, daysUntil)
        return targetCal.timeInMillis
    }

    private fun calculateExamTriggerMs(dateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = sdf.parse(dateStr.trim()) ?: return 0L
            val cal = Calendar.getInstance().apply {
                time = d
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (_: Exception) {
            0L
        }
    }
}
