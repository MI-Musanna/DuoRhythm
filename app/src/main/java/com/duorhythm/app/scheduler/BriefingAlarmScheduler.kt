package com.duorhythm.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duorhythm.app.data.local.pref.AppPreferences
import com.duorhythm.app.receiver.DailyBriefingAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object BriefingAlarmScheduler {

    const val ACTION_MORNING_BRIEFING = "com.duorhythm.app.ACTION_MORNING_BRIEFING"
    const val ACTION_EVENING_BRIEFING = "com.duorhythm.app.ACTION_EVENING_BRIEFING"

    const val REQUEST_CODE_MORNING = 1001
    const val REQUEST_CODE_EVENING = 1002

    private const val PREFS_SETTINGS = "duorhythm_user_settings"
    private const val KEY_MORNING_HOUR = "briefing_morning_hour"
    private const val KEY_MORNING_MINUTE = "briefing_morning_minute"
    private const val KEY_EVENING_HOUR = "briefing_evening_hour"
    private const val KEY_EVENING_MINUTE = "briefing_evening_minute"

    fun getUserConfiguredMorningTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_MORNING_HOUR) && prefs.contains(KEY_MORNING_MINUTE)) {
            return Pair(prefs.getInt(KEY_MORNING_HOUR, 8), prefs.getInt(KEY_MORNING_MINUTE, 0))
        }
        val prefStr = AppPreferences.getMorningBriefingTime(context)
        return parseTime(prefStr) ?: Pair(8, 0)
    }

    fun getUserConfiguredEveningTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_EVENING_HOUR) && prefs.contains(KEY_EVENING_MINUTE)) {
            return Pair(prefs.getInt(KEY_EVENING_HOUR, 20), prefs.getInt(KEY_EVENING_MINUTE, 0))
        }
        val prefStr = AppPreferences.getEveningBriefingTime(context)
        return parseTime(prefStr) ?: Pair(20, 0)
    }

    fun saveMorningTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_MORNING_HOUR, hour).putInt(KEY_MORNING_MINUTE, minute).apply()

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val formatted = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
        AppPreferences.setMorningBriefingTime(context, formatted)
        scheduleDailyBriefings(context)
    }

    fun saveEveningTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_EVENING_HOUR, hour).putInt(KEY_EVENING_MINUTE, minute).apply()

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val formatted = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
        AppPreferences.setEveningBriefingTime(context, formatted)
        scheduleDailyBriefings(context)
    }

    fun scheduleDailyBriefings(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val (mHour, mMinute) = getUserConfiguredMorningTime(context)
        val (eHour, eMinute) = getUserConfiguredEveningTime(context)

        scheduleSingleExactAlarm(context, alarmManager, mHour, mMinute, REQUEST_CODE_MORNING, ACTION_MORNING_BRIEFING)
        scheduleSingleExactAlarm(context, alarmManager, eHour, eMinute, REQUEST_CODE_EVENING, ACTION_EVENING_BRIEFING)
    }

    fun resyncBriefings(context: Context) {
        scheduleDailyBriefings(context)
    }

    fun scheduleMorningBriefing(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val (mHour, mMinute) = getUserConfiguredMorningTime(context)
        scheduleSingleExactAlarm(context, alarmManager, mHour, mMinute, REQUEST_CODE_MORNING, ACTION_MORNING_BRIEFING)
    }

    fun scheduleEveningBriefing(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val (eHour, eMinute) = getUserConfiguredEveningTime(context)
        scheduleSingleExactAlarm(context, alarmManager, eHour, eMinute, REQUEST_CODE_EVENING, ACTION_EVENING_BRIEFING)
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

        // Strictly schedule for the FUTURE (tomorrow if today's slot has already passed)
        if (alarmTime.timeInMillis <= now.timeInMillis + 30_000L) {
            alarmTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, DailyBriefingAlarmReceiver::class.java).apply {
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
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime.timeInMillis, pendingIntent)
        } catch (_: Exception) {}
    }

    fun getNextMorningSchedulePreview(context: Context): String {
        val (h, m) = getUserConfiguredMorningTime(context)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
        }
        if (cal.timeInMillis <= Calendar.getInstance().timeInMillis + 30_000L) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return SimpleDateFormat("EEE, MMM d • hh:mm a", Locale.ENGLISH).format(cal.time)
    }

    fun parseTime(timeStr: String?): Pair<Int, Int>? {
        if (timeStr.isNullOrBlank()) return null
        val formats = listOf(
            SimpleDateFormat("hh:mm a", Locale.US),
            SimpleDateFormat("h:mm a", Locale.US),
            SimpleDateFormat("HH:mm", Locale.US),
            SimpleDateFormat("H:mm", Locale.US)
        )
        for (fmt in formats) {
            try {
                val parsed: Date? = fmt.parse(timeStr.trim())
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply { time = parsed }
                    return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
