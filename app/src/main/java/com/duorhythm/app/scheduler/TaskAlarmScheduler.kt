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

    private fun parseDueDateTime(dueDateStr: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val date = sdf.parse(dueDateStr)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    // If only date was provided, default reminder to 9:00 AM on due day
                    if (fmt == "yyyy-MM-dd") {
                        cal.set(Calendar.HOUR_OF_DAY, 9)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                    }
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
