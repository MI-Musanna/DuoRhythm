package com.duorhythm.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.duorhythm.app.receiver.DailyBriefingAlarmReceiver
import java.util.Calendar

object BriefingScheduler {

    fun scheduleDailyBriefing(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = PreferencesManager.getInstance(context)

        // Read exact user setting (Defaults to 8:00 AM)
        val briefingHour = prefs.getInt("pref_morning_briefing_hour", 8)
        val briefingMinute = prefs.getInt("pref_morning_briefing_minute", 0)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, briefingHour)
            set(Calendar.MINUTE, briefingMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time has already passed today, schedule for tomorrow
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, DailyBriefingAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
            Log.d("DUO_SCHEDULER", "Synced exact alarm scheduled for: ${cal.time}")
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }
    }
}
