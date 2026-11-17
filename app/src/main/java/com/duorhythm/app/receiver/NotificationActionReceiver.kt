package com.duorhythm.app.receiver

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.scheduler.AlarmSyncManager
import com.duorhythm.app.scheduler.ClassAlarmScheduler
import com.duorhythm.app.scheduler.TaskAlarmScheduler
import com.duorhythm.app.widget.AcademicHubWidget

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_TASK_DONE = "com.duorhythm.app.ACTION_NOTIF_MARK_TASK_DONE"
        const val ACTION_SNOOZE_ALERT = "com.duorhythm.app.ACTION_NOTIF_SNOOZE_ALERT"

        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ALERT_TYPE = "extra_alert_type" // TASK, EXAM, CLASS
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notifId != -1) {
            nm?.cancel(notifId)
        }

        when (action) {
            ACTION_MARK_TASK_DONE -> {
                val taskId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
                if (taskId > 0L) {
                    try {
                        DatabaseHelper.getInstance(context).updateTaskCompletion(taskId, true)
                        TaskAlarmScheduler.cancelTaskReminder(context, taskId)
                        AcademicHubWidget.pushUpdate(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            ACTION_SNOOZE_ALERT -> {
                val itemId = intent.getLongExtra(EXTRA_ITEM_ID, System.currentTimeMillis())
                val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "TASK"
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "DuoRhythm Reminder"
                val body = intent.getStringExtra(EXTRA_BODY) ?: "Snoozed alert"
                val snoozeMins = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10).coerceIn(1, 60)

                val triggerMs = System.currentTimeMillis() + (snoozeMins * 60_000L)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

                val targetIntent = when (alertType) {
                    "EXAM" -> Intent(context, AssessmentAlarmReceiver::class.java).apply {
                        this.action = AssessmentAlarmReceiver.ACTION_ASSESSMENT_ALERT
                        putExtra(AssessmentAlarmReceiver.EXTRA_ASSESSMENT_ID, itemId)
                        putExtra(AssessmentAlarmReceiver.EXTRA_TITLE, title)
                        putExtra(AssessmentAlarmReceiver.EXTRA_BODY, body)
                    }
                    "CLASS" -> Intent(context, ClassAlarmReceiver::class.java).apply {
                        this.action = AlarmSyncManager.ACTION_CLASS_LEAD_ALERT
                        putExtra(AlarmSyncManager.EXTRA_CLASS_ID, itemId)
                        putExtra(AlarmSyncManager.EXTRA_SUBJECT_NAME, title)
                        putExtra(AlarmSyncManager.EXTRA_START_TIME, body)
                        putExtra(AlarmSyncManager.EXTRA_LEAD_MINUTES, snoozeMins)
                    }
                    else -> Intent(context, TaskAlarmReceiver::class.java).apply {
                        this.action = TaskAlarmReceiver.ACTION_TASK_ALERT
                        putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, itemId)
                        putExtra(TaskAlarmReceiver.EXTRA_TASK_TITLE, title)
                        putExtra(TaskAlarmReceiver.EXTRA_TASK_DUE_DATE, body)
                    }
                }

                val reqCode = (950000 + (itemId % 40000)).toInt()
                val pi = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    targetIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                ClassAlarmScheduler.setExactAlarm(alarmManager, triggerMs, pi)
            }
        }
    }
}
