package com.duorhythm.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duorhythm.app.receiver.DailyBriefingReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object BriefingAlarmScheduler {

    const val ACTION_MORNING_BRIEFING = "com.duorhythm.app.ACTION_MORNING_BRIEFING"
    const val ACTION_EVENING_BRIEFING = "com.duorhythm.app.ACTION_EVENING_BRIEFING"

    private const val PREFS_SETTINGS = "duorhythm_user_settings"
    private const val KEY_MORNING_HOUR = "briefing_morning_hour"
    private const val KEY_MORNING_MINUTE = "briefing_morning_minute"

    fun getUserConfiguredMorningTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        return Pair(prefs.getInt(KEY_MORNING_HOUR, 6), prefs.getInt(KEY_MORNING_MINUTE, 0))
    }

    fun saveMorningTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_MORNING_HOUR, hour).putInt(KEY_MORNING_MINUTE, minute).apply()
        scheduleDailyBriefings(context)
    }

    fun scheduleDailyBriefings(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val (morningHour, morningMinute) = getUserConfiguredMorningTime(context)

        scheduleSingleExactAlarm(context, alarmManager, morningHour, morningMinute, 600, ACTION_MORNING_BRIEFING)
        scheduleSingleExactAlarm(context, alarmManager, 20, 0, 800, ACTION_EVENING_BRIEFING)
    }

    private fun scheduleSingleExactAlarm(
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

        // Add buffer so opening the app right at 6:00:01 never triggers an immediate misfire
        if (alarmTime.timeInMillis <= now.timeInMillis + 60_000L) {
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
            alarmManager.cancel(pendingIntent)
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
