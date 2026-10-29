package com.duorhythm.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.local.pref.AppPreferences
import com.duorhythm.app.receiver.DailyAutomationReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object BriefingAlarmScheduler {

    const val REQUEST_CODE_MORNING = 1001
    const val REQUEST_CODE_EVENING = 1002

    fun resyncBriefings(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (!SmartAutomationManager.isSmartBriefingsEnabled(context)) {
            cancelAlarm(context, am, DailyAutomationReceiver.ACTION_MORNING_BRIEFING, REQUEST_CODE_MORNING)
            cancelAlarm(context, am, DailyAutomationReceiver.ACTION_EVENING_BRIEFING, REQUEST_CODE_EVENING)
            return
        }

        val morningTimeStr = AppPreferences.getMorningBriefingTime(context)
        val eveningTimeStr = AppPreferences.getEveningBriefingTime(context)

        val morningPair = parseTime(morningTimeStr) ?: Pair(8, 0)
        val eveningPair = parseTime(eveningTimeStr) ?: Pair(20, 30)

        scheduleDailyAlarm(
            context,
            am,
            DailyAutomationReceiver.ACTION_MORNING_BRIEFING,
            morningPair.first,
            morningPair.second,
            REQUEST_CODE_MORNING
        )

        scheduleDailyAlarm(
            context,
            am,
            DailyAutomationReceiver.ACTION_EVENING_BRIEFING,
            eveningPair.first,
            eveningPair.second,
            REQUEST_CODE_EVENING
        )
    }

    fun scheduleMorningBriefing(context: Context) {
        if (!SmartAutomationManager.isSmartBriefingsEnabled(context)) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val morningTimeStr = AppPreferences.getMorningBriefingTime(context)
        val morningPair = parseTime(morningTimeStr) ?: Pair(8, 0)

        scheduleDailyAlarm(
            context,
            am,
            DailyAutomationReceiver.ACTION_MORNING_BRIEFING,
            morningPair.first,
            morningPair.second,
            REQUEST_CODE_MORNING
        )
    }

    fun scheduleEveningBriefing(context: Context) {
        if (!SmartAutomationManager.isSmartBriefingsEnabled(context)) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val eveningTimeStr = AppPreferences.getEveningBriefingTime(context)
        val eveningPair = parseTime(eveningTimeStr) ?: Pair(20, 30)

        scheduleDailyAlarm(
            context,
            am,
            DailyAutomationReceiver.ACTION_EVENING_BRIEFING,
            eveningPair.first,
            eveningPair.second,
            REQUEST_CODE_EVENING
        )
    }

    private fun scheduleDailyAlarm(
        context: Context,
        am: AlarmManager,
        action: String,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, DailyAutomationReceiver::class.java).apply {
            this.action = action
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (_: Exception) {}
    }

    private fun cancelAlarm(context: Context, am: AlarmManager, action: String, requestCode: Int) {
        val intent = Intent(context, DailyAutomationReceiver::class.java).apply {
            this.action = action
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    fun parseTime(timeStr: String): Pair<Int, Int>? {
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
