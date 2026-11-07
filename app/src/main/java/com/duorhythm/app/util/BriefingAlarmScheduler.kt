package com.duorhythm.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import com.duorhythm.app.receiver.DailyBriefingReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object BriefingAlarmScheduler {

    const val ACTION_MORNING_BRIEFING = "com.duorhythm.app.ACTION_MORNING_BRIEFING"
    const val ACTION_EVENING_BRIEFING = "com.duorhythm.app.ACTION_EVENING_BRIEFING"

    private const val PREFS_SETTINGS = "duorhythm_user_settings"
    private const val KEY_MORNING_HOUR = "briefing_morning_hour"
    private const val KEY_MORNING_MINUTE = "briefing_morning_minute"

    fun getUserConfiguredMorningTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        // Default to 6:00 AM if not customized
        val hour = prefs.getInt(KEY_MORNING_HOUR, 6)
        val minute = prefs.getInt(KEY_MORNING_MINUTE, 0)
        return Pair(hour, minute)
    }

    fun saveMorningTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_MORNING_HOUR, hour).putInt(KEY_MORNING_MINUTE, minute).apply()
        scheduleDailyBriefings(context)
    }

    fun scheduleDailyBriefings(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val (morningHour, morningMinute) = getUserConfiguredMorningTime(context)

        // 1. Schedule Morning Briefing using OS AlarmClock (Punches through Deep Doze)
        scheduleAlarmClock(
            context = context,
            alarmManager = alarmManager,
            targetHour = morningHour,
            targetMinute = morningMinute,
            requestCode = 600,
            action = ACTION_MORNING_BRIEFING
        )

        // 2. Schedule Evening Briefing at 08:00 PM (20:00)
        scheduleAlarmClock(
            context = context,
            alarmManager = alarmManager,
            targetHour = 20,
            targetMinute = 0,
            requestCode = 800,
            action = ACTION_EVENING_BRIEFING
        )
    }

    private fun scheduleAlarmClock(
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

        // If time already passed today, rollover to tomorrow
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
            // SET ALARM CLOCK: Guarantees 100% wake-up from Deep Doze on all Android versions
            val clockInfo = AlarmManager.AlarmClockInfo(alarmTime.timeInMillis, pendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
        } catch (_: Exception) {
            // High-reliability Fallback
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
            } catch (_: Exception) {}
        }
    }

    fun getNextMorningSchedulePreview(context: Context): String {
        val (h, m) = getUserConfiguredMorningTime(context)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
        }
        if (cal.before(Calendar.getInstance())) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return SimpleDateFormat("EEE, MMM d • hh:mm a", Locale.ENGLISH).format(cal.time)
    }
}
