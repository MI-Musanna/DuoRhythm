package com.duorhythm.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.automation.FinancialExpenseParser
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.receiver.FinancialExpenseActionReceiver
import java.util.Collections
import java.util.Locale

class FinancialNotificationListenerService : NotificationListenerService() {

    companion object {
        private val recentHashes = Collections.synchronizedSet(LinkedHashSet<Int>())
        private const val MAX_RECENT_HASHES = 30
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return
        if (pkgName == packageName) return // Do not listen to own notifications

        val prefs = getSharedPreferences(SmartAutomationManager.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(SmartAutomationManager.KEY_EXPENSE_DETECTION, true)
        if (!isEnabled) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val fullText = if (bigText.length > text.length) bigText else text

        if (!FinancialExpenseParser.isPotentialFinancialNotification(title, fullText, pkgName)) {
            return
        }

        val expenseInfo = FinancialExpenseParser.parse(title, fullText, pkgName) ?: return

        // Prevent duplicate alerts for the same notification event
        val eventHash = (expenseInfo.source + expenseInfo.amount + expenseInfo.merchant).hashCode()
        synchronized(recentHashes) {
            if (recentHashes.contains(eventHash)) return
            if (recentHashes.size >= MAX_RECENT_HASHES) {
                val iterator = recentHashes.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            recentHashes.add(eventHash)
        }

        // Show high priority interactive notification
        showInteractiveExpensePrompt(expenseInfo)
    }

    private fun showInteractiveExpensePrompt(info: com.duorhythm.app.automation.DetectedExpenseInfo) {
        SmartAutomationManager.createNotificationChannels(this)

        val notificationId = (System.currentTimeMillis() % 100000).toInt() + 30000

        // Tap on content opens main wallet
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Log Expense
        val logIntent = Intent(this, FinancialExpenseActionReceiver::class.java).apply {
            action = FinancialExpenseActionReceiver.ACTION_LOG_EXPENSE
            putExtra(FinancialExpenseActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(FinancialExpenseActionReceiver.EXTRA_AMOUNT, info.amount)
            putExtra(FinancialExpenseActionReceiver.EXTRA_SOURCE, info.source)
            putExtra(FinancialExpenseActionReceiver.EXTRA_TYPE, info.type)
            putExtra(FinancialExpenseActionReceiver.EXTRA_MERCHANT, info.merchant)
            putExtra(FinancialExpenseActionReceiver.EXTRA_TARGET_BUCKET, info.targetBucketName)
        }
        val logPendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId + 1,
            logIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Dismiss
        val dismissIntent = Intent(this, FinancialExpenseActionReceiver::class.java).apply {
            action = FinancialExpenseActionReceiver.ACTION_DISMISS_EXPENSE
            putExtra(FinancialExpenseActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = String.format(Locale.US, "%,.0f", info.amount)
        val notifTitle = "Detected ${info.source} ${info.type} of ৳$amountStr"
        val notifContent = "Log under '${info.targetBucketName}'? Recipient: ${info.merchant}"

        val builder = NotificationCompat.Builder(this, SmartAutomationManager.CHANNEL_EXPENSE_DETECTION)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(notifTitle)
            .setContentText(notifContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_wallet, "Log Expense", logPendingIntent)
            .addAction(R.drawable.ic_close, "Dismiss", dismissPendingIntent)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(notificationId, builder.build())
    }
}
