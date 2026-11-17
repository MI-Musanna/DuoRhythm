package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.scheduler.TaskAlarmScheduler

class TaskAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TASK_ALERT = "com.duorhythm.app.ACTION_TASK_ALERT"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DUE_DATE = "extra_task_due_date"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_TASK_PERSONA = "extra_task_persona"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("pref_task_reminders_enabled", true)) return

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, System.currentTimeMillis())
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Untitled Task"
        val taskDueDate = intent.getStringExtra(EXTRA_TASK_DUE_DATE) ?: "Today"
        val taskPriority = intent.getStringExtra(EXTRA_TASK_PRIORITY) ?: "MEDIUM"
        val taskPersona = intent.getStringExtra(EXTRA_TASK_PERSONA) ?: "STUDENT"

        TaskAlarmScheduler.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val requestCode = (500000 + (taskId % 100000)).toInt()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "tasks")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_TASK_DONE
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, requestCode)
            putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, taskId)
        }
        val donePi = PendingIntent.getBroadcast(
            context,
            requestCode + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_ALERT
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, requestCode)
            putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, taskId)
            putExtra(NotificationActionReceiver.EXTRA_ALERT_TYPE, "TASK")
            putExtra(NotificationActionReceiver.EXTRA_TITLE, taskTitle)
            putExtra(NotificationActionReceiver.EXTRA_BODY, taskDueDate)
            putExtra(NotificationActionReceiver.EXTRA_SNOOZE_MINUTES, 10)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context,
            requestCode + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val personaBadge = if (taskPersona.equals("WORK", ignoreCase = true)) "[WORK]" else "[STUDENT]"
        val notificationTitle = "$personaBadge $taskTitle"
        val notificationBody = "Due: $taskDueDate • Priority: ${taskPriority.uppercase()}"

        val builder = NotificationCompat.Builder(context, TaskAlarmScheduler.CHANNEL_ID_TASKS)
            .setSmallIcon(R.drawable.ic_stat_duorhythm)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBody))
            .setColor(if (taskPersona.equals("WORK", ignoreCase = true)) 0xFF10B981.toInt() else 0xFF00E5FF.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_check, "Mark Done", donePi)
            .addAction(R.drawable.ic_schedule, "Snooze 10m", snoozePi)

        nm.notify(requestCode, builder.build())
    }
}
