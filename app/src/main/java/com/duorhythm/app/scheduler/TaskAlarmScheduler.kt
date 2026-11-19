package com.duorhythm.app.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import com.duorhythm.app.data.model.TaskItem
import com.duorhythm.app.receiver.TaskAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object TaskAlarmScheduler {

    const val CHANNEL_ID_TASKS = "duorhythm_tasks_channel"
    const val CHANNEL_NAME_TASKS = "Task & Milestone Reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_TASKS,
                CHANNEL_NAME_TASKS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts for upcoming tasks, milestones, and deliverables"
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun scheduleTaskReminder(context: Context, task: TaskItem) {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("pref_task_reminders_enabled", true)) {
            cancelTaskReminder(context, task.id)
            return
        }
        if (task.isCompleted) {
            cancelTaskReminder(context, task.id)
            return
        }

        val dueDateStr = task.dueDate?.trim().orEmpty()
        if (dueDateStr.isEmpty()) return

        val triggerTime = parseDueDateTime(dueDateStr) ?: return
        if (triggerTime <= System.currentTimeMillis()) return

        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_TASK_ALERT
            putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_DUE_DATE, task.dueDate ?: "")
            putExtra(TaskAlarmReceiver.EXTRA_TASK_PRIORITY, task.priority)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_PERSONA, task.persona)
        }

        val requestCode = (500000 + (task.id % 100000)).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (_: Exception) {}
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_TASK_ALERT
        }
        val requestCode = (500000 + (taskId % 100000)).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun parseDueDateTime(dueDateStr: String): Long? {
        val clean = dueDateStr.trim()
        if (clean.isBlank()) return null
        val now = System.currentTimeMillis()

        // 1. Timed formats (15m heads-up or short-window fallback)
        val timedFormats = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd h:mm a",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy hh:mm a",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy hh:mm a"
        )
        for (fmt in timedFormats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val date = sdf.parse(clean)
                if (date != null) {
                    val exactMs = date.time
                    val headsUpMs = exactMs - (15 * 60 * 1000L)
                    return when {
                        headsUpMs > now -> headsUpMs
                        exactMs > now -> if (exactMs - 60_000L > now) exactMs - 60_000L else exactMs
                        else -> null
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Date-only formats (9:00 AM date trigger with 6:00 PM same-day fallback)
        val dateOnlyFormats = listOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "MM/dd/yyyy"
        )
        for (fmt in dateOnlyFormats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val date = sdf.parse(clean)
                if (date != null) {
                    val cal = Calendar.getInstance().apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val at9Am = cal.timeInMillis
                    if (at9Am > now) {
                        return at9Am
                    }

                    // Check if date is today -> fallback to 6:00 PM (18:00)
                    val todayCal = Calendar.getInstance()
                    val isToday = (cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR))
                    if (isToday) {
                        cal.set(Calendar.HOUR_OF_DAY, 18)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        val at6Pm = cal.timeInMillis
                        if (at6Pm > now) {
                            return at6Pm
                        }
                    }
                    return null
                }
            } catch (_: Exception) {}
        }

        return null
    }

    fun resyncAllTaskReminders(context: Context) {
        try {
            val db = com.duorhythm.app.data.local.db.DatabaseHelper.getInstance(context)
            val enabled = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
                .getBoolean("pref_task_reminders_enabled", true)
            val tasks = db.getAllTasks("ALL")
            for (t in tasks) {
                if (enabled && !t.isCompleted) {
                    scheduleTaskReminder(context, t)
                } else {
                    cancelTaskReminder(context, t.id)
                }
            }
        } catch (_: Exception) {}
    }
}
