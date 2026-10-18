package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.service.ReminderManager

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderManager.rescheduleAll(context)
            com.duorhythm.app.automation.SmartAutomationManager.init(context)
            return
        }

        if (action == ReminderManager.ACTION_REMINDER) {
            if (!ReminderManager.isRemindersEnabled(context)) return

            val title = intent.getStringExtra(ReminderManager.EXTRA_TITLE) ?: "LifeSync Reminder"
            val message = intent.getStringExtra(ReminderManager.EXTRA_MESSAGE) ?: "Upcoming schedule item"
            val type = intent.getStringExtra(ReminderManager.EXTRA_TYPE) ?: "CLASS"
            val itemId = intent.getLongExtra(ReminderManager.EXTRA_ID, -1L)

            ReminderManager.createNotificationChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 100000).toInt(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_schedule)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notificationId = if (itemId > 0) (itemId.toInt() + if (type == "CLASS") 10000 else 20000) else 1001
            nm?.notify(notificationId, notification)

            // For repeating weekly classes, schedule the occurrence for next week
            if (type == "CLASS" && itemId > 0) {
                val db = DatabaseHelper.getInstance(context)
                val classes = db.getAllClasses()
                val matched = classes.firstOrNull { it.id == itemId }
                if (matched != null) {
                    ReminderManager.scheduleClassReminder(context, matched)
                }
            }
        }
    }
}
