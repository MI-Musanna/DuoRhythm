package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duorhythm.app.scheduler.AlarmSyncManager
import com.duorhythm.app.scheduler.SubscriptionAlarmScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AlarmSyncManager.resyncAll(context)
            SubscriptionAlarmScheduler.scheduleDailyAlarms(context)
        }
    }
}
