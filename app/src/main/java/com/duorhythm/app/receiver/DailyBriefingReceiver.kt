package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.duorhythm.app.util.BriefingAlarmScheduler
import com.duorhythm.app.util.NotificationHelper
import java.util.Calendar

class DailyBriefingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DuoRhythm:DailyBriefingExecutionWakeLock"
        )

        // Hold CPU awake for up to 45 seconds to guarantee DB queries & notification render finish
        wakeLock?.acquire(45_000L)

        try {
            val action = intent?.action

            when (action) {
                BriefingAlarmScheduler.ACTION_MORNING_BRIEFING -> {
                    // Morning Briefing for Today
                    NotificationHelper.showUnifiedDailyBriefing(context, isEveningReview = false)
                }
                BriefingAlarmScheduler.ACTION_EVENING_BRIEFING -> {
                    // Evening Review for Tomorrow
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

            // Always reschedule the next cycle
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
