package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duorhythm.app.util.BriefingAlarmScheduler
import com.duorhythm.app.util.NotificationHelper
import java.util.Calendar

class DailyBriefingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action

        when (action) {
            BriefingAlarmScheduler.ACTION_EVENING_BRIEFING -> {
                // 8:00 PM Evening Briefing (Previews Tomorrow's Schedule)
                NotificationHelper.showUnifiedDailyBriefing(context, isEveningReview = true)
            }
            BriefingAlarmScheduler.ACTION_MORNING_BRIEFING -> {
                // 6:00 AM Morning Briefing (Shows Today's Routine)
                NotificationHelper.showUnifiedDailyBriefing(context, isEveningReview = false)
            }
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                // Device rebooted -> Reschedule alarms
                BriefingAlarmScheduler.scheduleDailyBriefings(context)
                return
            }
            else -> {
                // Fallback check based on system clock
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isEvening = hour >= 18
                NotificationHelper.showUnifiedDailyBriefing(context, isEveningReview = isEvening)
            }
        }

        // Always reschedule to ensure the alarm cycle never dies
        BriefingAlarmScheduler.scheduleDailyBriefings(context)
    }
}
