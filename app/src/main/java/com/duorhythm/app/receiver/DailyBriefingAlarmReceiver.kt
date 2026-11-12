package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.duorhythm.app.scheduler.BriefingAlarmScheduler
import com.duorhythm.app.util.NotificationHelper

class DailyBriefingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DuoRhythm:DailyBriefingExecutionWakeLock"
        )

        wakeLock?.acquire(45_000L)

        try {
            val action = intent?.action
            Log.d("DUO_ALARM", "Daily Briefing alarm receiver fired with action: $action")

            when (action) {
                BriefingAlarmScheduler.ACTION_MORNING_BRIEFING,
                "com.duorhythm.app.ACTION_DAILY_BRIEFING",
                "com.duorhythm.app.ACTION_DAILY_BRIEFING_MODERN" -> {
                    NotificationHelper.showUnifiedDailyBriefing(context, isEveningReview = false)
                }
                BriefingAlarmScheduler.ACTION_EVENING_BRIEFING -> {
                    NotificationHelper.showUnifiedDailyBriefing(context, isEveningReview = true)
                }
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.QUICKBOOT_POWERON" -> {
                    BriefingAlarmScheduler.scheduleDailyBriefings(context)
                    return
                }
                else -> {
                    // Do not fire unexpected notification on app install/update or unknown action
                    return
                }
            }

            // Always reschedule next cycle
            BriefingAlarmScheduler.scheduleDailyBriefings(context)
        } finally {
            if (wakeLock?.isHeld == true) {
                try {
                    wakeLock.release()
                } catch (_: Exception) {}
            }
        }
    }
}
