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
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, System.currentTimeMillis())
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Untitled Task"
        val taskDueDate = intent.getStringExtra(EXTRA_TASK_DUE_DATE) ?: "Today"
        val taskPriority = intent.getStringExtra(EXTRA_TASK_PRIORITY) ?: "MEDIUM"
        val taskPersona = intent.getStringExtra(EXTRA_TASK_PERSONA) ?: "STUDENT"

        TaskAlarmScheduler.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "tasks")
        }
        val requestCode = (500000 + (taskId % 100000)).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1. Extract persona attribute
        val personaBadge = if (taskPersona.equals("WORK", ignoreCase = true)) "[WORK]" else "[STUDENT]"
        val notificationTitle = "$personaBadge $taskTitle"
        val notificationBody = "Due: $taskDueDate | Priority: ${taskPriority.uppercase()}"

        // 2. Notification Styling
        val builder = NotificationCompat.Builder(context, TaskAlarmScheduler.CHANNEL_ID_TASKS)
            .setSmallIcon(if (taskPersona.equals("WORK", ignoreCase = true)) R.drawable.ic_work else R.drawable.ic_academic)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setColor(if (taskPersona.equals("WORK", ignoreCase = true)) 0xFF10B981.toInt() else 0xFF00E5FF.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        nm.notify(requestCode, builder.build())
    }
}
