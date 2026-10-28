package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.scheduler.SubscriptionAlarmScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SubscriptionAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_SUBSCRIPTIONS = "com.duorhythm.app.ACTION_CHECK_SUBSCRIPTIONS"
        const val EXTRA_SLOT_HOUR = "extra_slot_hour"
    }

    override fun onReceive(context: Context, intent: Intent) {
        SubscriptionAlarmScheduler.ensureChannel(context)

        // Reschedule next cycle
        SubscriptionAlarmScheduler.scheduleDailyAlarms(context)

        val db = DatabaseHelper.getInstance(context)
        val activeProfileId = ProfileManager.getActiveProfileId(context)
        val activeSubs = db.subscriptionDao.getActiveSubscriptions(activeProfileId)

        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        for (sub in activeSubs) {
            // If already settled this month, skip
            if (sub.lastPaidMonthYear == currentMonthYear) {
                continue
            }

            // Check if due today
            val isDueToday = if (sub.isRange) {
                currentDay in sub.startDay..sub.endDay
            } else {
                currentDay == sub.dueDay
            }

            if (!isDueToday) {
                continue
            }

            val sourceText = if (sub.linkedAccountId == null || sub.linkedAccountId <= 0L) {
                "Payer: External"
            } else {
                "Source: ${sub.accountName.ifBlank { "Account" }}"
            }

            val dueDesc = if (sub.isRange) {
                "Window: Day ${sub.startDay} - ${sub.endDay}"
            } else {
                "Due today (Day ${sub.dueDay})"
            }

            // Action Intent: Mark as Paid
            val markPaidIntent = Intent(context, SubscriptionActionReceiver::class.java).apply {
                action = SubscriptionActionReceiver.ACTION_MARK_SUB_PAID
                putExtra(SubscriptionActionReceiver.EXTRA_SUB_ID, sub.id)
                putExtra(SubscriptionActionReceiver.EXTRA_NOTIFICATION_ID, (92000 + sub.id).toInt())
            }
            val markPaidPi = PendingIntent.getBroadcast(
                context,
                (92000 + sub.id).toInt(),
                markPaidIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, SubscriptionAlarmScheduler.CHANNEL_FINANCE_ALERTS)
                .setSmallIcon(R.drawable.ic_sync)
                .setContentTitle("Subscription Due: ${sub.name}")
                .setContentText("Cost: ${sub.amount.toInt()} • $dueDesc • $sourceText")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Subscription '${sub.name}' is due.\nAmount: ${sub.amount.toInt()}\n$dueDesc • $sourceText")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(
                    R.drawable.ic_check,
                    "Mark as Paid",
                    markPaidPi
                )
                .build()

            nm.notify((92000 + sub.id).toInt(), notif)
        }
    }
}
