package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duorhythm.app.util.NotificationHelper

class DailyBriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Exclusively trigger the modern dual-view briefing
        NotificationHelper.showUnifiedDailyBriefing(context)
    }
}
