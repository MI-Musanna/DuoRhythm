package com.duorhythm.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duorhythm.app.receiver.DailyBriefingReceiver
import java.util.Calendar

object BriefingAlarmScheduler {

    const val ACTION_MORNING_BRIEFING = "com.duorhythm.app.ACTION_MORNING_BRIEFING"
    const val ACTION_EVENING_BRIEFING = "com.duorhythm.app.ACTION_EVENING_BRIEFING"

    fun scheduleDailyBriefings(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // 1. Schedule Morning Briefing at 06:00 AM
        scheduleExactAlarm(
            context = context,
            alarmManager = alarmManager,
            targetHour = 6,
            targetMinute = 0,
            requestCode = 600,
            action = ACTION_MORNING_BRIEFING
        )

        // 2. Schedule Evening Briefing at 08:00 PM (20:00)
        scheduleExactAlarm(
            context = context,
            alarmManager = alarmManager,
            targetHour = 20,
            targetMinute = 0,
            requestCode = 800,
            action = ACTION_EVENING_BRIEFING
        )
    }

    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        targetHour: Int,
        targetMinute: Int,
        requestCode: Int,
        action: String
    ) {
        val now = Calendar.getInstance()
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If today's time has already elapsed, rollover to tomorrow
        if (alarmTime.before(now)) {
            alarmTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, DailyBriefingReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime.timeInMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime.timeInMillis, pendingIntent)
        } catch (_: Exception) {}
    }
}
