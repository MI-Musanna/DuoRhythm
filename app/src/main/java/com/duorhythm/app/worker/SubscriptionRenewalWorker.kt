package com.duorhythm.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.SubscriptionEntity
import com.duorhythm.app.data.model.TransactionEntity
import com.duorhythm.app.util.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SubscriptionRenewalWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val db = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId()
        val subscriptions = db.getSubscriptions(profileId)

        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1 // 1-12
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val maxDaysInCurrentMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH) // Dynamic 28, 29, 30, or 31

        val currentMonthYearKey = String.format(Locale.US, "%04d-%02d", currentYear, currentMonth)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

        for (sub in subscriptions) {
            // Rule 1: Never process if already paid/logged for the current month
            if (sub.lastPaidMonthYear == currentMonthYearKey) {
                continue
            }

            // Rule 2: Strictly evaluate day window against real month boundaries
            val safeStart = sub.startDay.coerceIn(1, maxDaysInCurrentMonth)
            val safeEnd = sub.endDay.coerceIn(safeStart, maxDaysInCurrentMonth)
            val isDueToday = if (sub.isRange) {
                currentDay in safeStart..safeEnd
            } else {
                currentDay == sub.dueDay.coerceIn(1, maxDaysInCurrentMonth)
            }

            if (!isDueToday) {
                continue
            }

            // STRICT SAFETY: Never deduct money for External / Third-party subscriptions
            val isExternal = sub.name.contains("External", ignoreCase = true) ||
                             sub.accountName.contains("External", ignoreCase = true) ||
                             sub.linkedAccountId == null || sub.linkedAccountId <= 0L

            if (isExternal) {
                // DO NOT TOUCH WALLET BALANCE. DO NOT INSERT EXPENSE TRANSACTION.
                // Simply mark renewal date rollover if window passed, zero balance impact.
                db.updateSubscriptionPaidState(sub.id, currentMonthYearKey, calculateNextRenewal(cal, sub))
                continue
            }

            // Transaction logging blocked for external source
            // Account balance modification blocked
            db.updateSubscriptionPaidState(sub.id, currentMonthYearKey, calculateNextRenewal(cal, sub))
        }
        Result.success()
    }

    private fun calculateNextRenewal(cal: Calendar, sub: SubscriptionEntity): String {
        val nextCal = cal.clone() as Calendar
        nextCal.add(Calendar.MONTH, 1)
        val maxDaysNextMonth = nextCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val day = if (sub.isRange) sub.startDay.coerceIn(1, maxDaysNextMonth) else sub.dueDay.coerceIn(1, maxDaysNextMonth)
        nextCal.set(Calendar.DAY_OF_MONTH, day)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nextCal.time)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel("duo_subscriptions_channel") == null) {
                val channel = NotificationChannel(
                    "duo_subscriptions_channel",
                    "Subscription Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun postExternalSubscriptionNotification(context: Context, sub: SubscriptionEntity) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val notif = NotificationCompat.Builder(context, "duo_subscriptions_channel")
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle("${sub.name} Due Reminder")
            .setContentText("External fee of ৳${sub.amount.toInt()} due today. Managed outside DuoRhythm (No account deducted).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify((sub.id + 1000).toInt(), notif)
    }

    private fun postDeductionNotification(context: Context, sub: SubscriptionEntity, accountName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val notif = NotificationCompat.Builder(context, "duo_subscriptions_channel")
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle("${sub.name} Auto-Renewed")
            .setContentText("Automatically logged ৳${sub.amount.toInt()} under '$accountName'.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify((sub.id + 2000).toInt(), notif)
    }

    companion object {
        private const val WORK_NAME = "duorhythm_subscription_renewal"

        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<SubscriptionRenewalWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
