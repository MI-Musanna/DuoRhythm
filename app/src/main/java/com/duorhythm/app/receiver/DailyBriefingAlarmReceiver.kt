package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.duorhythm.app.util.BriefingScheduler
import com.duorhythm.app.util.NotificationHelper

class DailyBriefingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("DUO_ALARM", "Daily Briefing & Subscription exact alarm fired at user-configured time.")

        // Reschedule for next day at exact briefing hour
        try {
            BriefingScheduler.scheduleDailyBriefing(context)
        } catch (_: Exception) {}

        // Exclusively trigger the modern dual-view briefing
        NotificationHelper.showUnifiedDailyBriefing(context)
    }
}
