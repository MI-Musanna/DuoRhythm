package com.duorhythm.app.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import com.duorhythm.app.receiver.SubscriptionAlarmReceiver
import java.util.Calendar

object SubscriptionAlarmScheduler {

    const val CHANNEL_FINANCE_ALERTS = "duorhythm_finance_alerts_channel"
    const val CHANNEL_NAME_FINANCE_ALERTS = "Subscriptions & Financial Alerts"

    const val REQ_CODE_MORNING = 83001
    const val REQ_CODE_EVENING = 83002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_FINANCE_ALERTS,
                CHANNEL_NAME_FINANCE_ALERTS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recurring subscription and financial due reminders"
                enableLights(true)
                lightColor = Color.parseColor("#0284C7")
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules dual daily alarms:
     * 1) 08:30 AM
     * 2) 08:00 PM (20:00)
     */
    fun scheduleDailyAlarms(context: Context) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // 1. 08:30 AM
        scheduleSlot(context, alarmManager, 8, 30, REQ_CODE_MORNING)

        // 2. 08:00 PM (20:00)
        scheduleSlot(context, alarmManager, 20, 0, REQ_CODE_EVENING)
    }

    private fun scheduleSlot(
        context: Context,
        alarmManager: AlarmManager,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, SubscriptionAlarmReceiver::class.java).apply {
            action = SubscriptionAlarmReceiver.ACTION_CHECK_SUBSCRIPTIONS
            putExtra(SubscriptionAlarmReceiver.EXTRA_SLOT_HOUR, hour)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (_: Exception) {}
    }
}
