package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TransactionItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DailyAutomationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MORNING_BRIEFING = "com.duorhythm.app.ACTION_MORNING_BRIEFING"
        const val ACTION_EVENING_BRIEFING = "com.duorhythm.app.ACTION_EVENING_BRIEFING"
        const val ACTION_SUBSCRIPTION_CHECK = "com.duorhythm.app.ACTION_SUBSCRIPTION_CHECK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        SmartAutomationManager.createNotificationChannels(context)

        when (action) {
            ACTION_MORNING_BRIEFING -> {
                handleMorningBriefing(context)
                SmartAutomationManager.scheduleMorningBriefing(context)
            }
            ACTION_EVENING_BRIEFING -> {
                handleEveningBriefing(context)
                SmartAutomationManager.scheduleEveningBriefing(context)
            }
            ACTION_SUBSCRIPTION_CHECK -> {
                handleSubscriptionCheck(context)
                SmartAutomationManager.scheduleSubscriptionCheck(context)
            }
        }
    }

    private fun handleMorningBriefing(context: Context) {
        // Consolidated in BriefingAlarmScheduler & DailyBriefingReceiver
    }

    private fun handleEveningBriefing(context: Context) {
        if (!SmartAutomationManager.isSmartBriefingsEnabled(context)) return

        val db = DatabaseHelper.getInstance(context)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todaySpend = db.getTodayTotalExpense(todayStr)

        val spendFormatted = String.format(Locale.US, "%,.0f", todaySpend)
        val text = "Today's recorded expenditure: ৳$spendFormatted. Don't forget to log any cash or offline expenses before bed!"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            20102,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_BRIEFINGS)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle("Evening Wrap-up")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(20102, notification)
    }

    private fun handleSubscriptionCheck(context: Context) {
        if (!SmartAutomationManager.isSubscriptionAlertsEnabled(context)) return

        val db = DatabaseHelper.getInstance(context)
        val subs = db.getAllSubscriptions()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val todayDate = Date()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(todayDate)

        for (sub in subs) {
            if (!sub.isActive) continue

            val daysUntil = sub.getDaysUntilRenewal()
            val costStr = String.format(Locale.US, "%,.0f", sub.cost)
            val bucketName = sub.bucketName.ifBlank { "your wallet" }

            // 1. Advance warning 24-48 hours before renewal
            if (daysUntil in 1..2) {
                val timePhrase = if (daysUntil == 1L) "tomorrow" else "in 2 days"
                val message = "${sub.serviceName} renews $timePhrase for ৳$costStr. Keep sufficient balance in $bucketName."

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pi = PendingIntent.getActivity(
                    context,
                    (20200 + sub.id).toInt(),
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notif = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_SUBSCRIPTIONS)
                    .setSmallIcon(R.drawable.ic_subscription)
                    .setContentTitle("${sub.serviceName} Renewal Alert")
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build()

                nm.notify((20200 + sub.id).toInt(), notif)
            }

            // 2. Renewal day:
            if (daysUntil <= 0) {
                // STRICT SAFETY: Never deduct money for External / Third-party subscriptions
                val isExternal = sub.bucketName.contains("External", ignoreCase = true) ||
                                 sub.bucketName.contains("None", ignoreCase = true) ||
                                 sub.bucketId <= 0L ||
                                 sub.bucketName.isBlank()

                if (isExternal) {
                    // DO NOT TOUCH WALLET BALANCE. DO NOT INSERT EXPENSE TRANSACTION.
                    // Simply mark renewal date rollover if window passed, zero balance impact.
                    val nextRenewalDate = calculateNextRenewalDate(sub.renewalDate, sub.billingCycle)
                    db.updateSubscriptionRenewalDate(sub.id, nextRenewalDate)
                    continue
                }

                // Advance renewal date to next cycle without deducting money
                val nextRenewalDate = calculateNextRenewalDate(sub.renewalDate, sub.billingCycle)
                db.updateSubscriptionRenewalDate(sub.id, nextRenewalDate)
            }
        }
    }

    private fun calculateNextRenewalDate(currentDateStr: String, billingCycle: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = sdf.parse(currentDateStr) ?: Date()
            val cal = Calendar.getInstance().apply { time = d }
            if (billingCycle.equals("YEARLY", ignoreCase = true)) {
                cal.add(Calendar.YEAR, 1)
            } else {
                cal.add(Calendar.MONTH, 1)
            }
            sdf.format(cal.time)
        } catch (_: Exception) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, 1)
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        }
    }
}
