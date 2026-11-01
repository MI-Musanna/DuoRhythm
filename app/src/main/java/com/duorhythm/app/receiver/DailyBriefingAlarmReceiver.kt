package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.util.BriefingScheduler
import com.duorhythm.app.util.NotificationHelper
import com.duorhythm.app.util.ProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DailyBriefingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("DUO_ALARM", "Daily Briefing & Subscription exact alarm fired at user-configured time.")

        // Reschedule for next day at exact briefing hour
        BriefingScheduler.scheduleDailyBriefing(context)

        // Process Morning Briefing & Subscription Dues concurrently on IO
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = DatabaseHelper.getInstance(context)
                val profileId = ProfileManager.getActiveProfileId(context)

                // 1. Fire Daily Academic Briefing
                NotificationHelper.showMorningBriefing(context, db, profileId)

                // 2. Fire External Subscription Dues strictly at this exact hour
                val cal = Calendar.getInstance()
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val subscriptions = db.getSubscriptions(profileId)

                for (sub in subscriptions) {
                    val safeStart = sub.startDay.coerceIn(1, maxDays)
                    val safeEnd = sub.endDay.coerceIn(safeStart, maxDays)
                    val isDueToday = if (sub.isRange) currentDay in safeStart..safeEnd else currentDay == sub.dueDay.coerceIn(1, maxDays)

                    if (isDueToday) {
                        NotificationHelper.postSubscriptionDueNotification(context, sub)
                    }
                }
            } catch (e: Exception) {
                Log.e("DUO_ALARM", "Error executing briefing alarm tasks", e)
            }
        }
    }
}
