package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.scheduler.SubscriptionAlarmScheduler
import java.util.Locale

class DueDebtAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DUE_DEBT_ALERT = "com.duorhythm.app.ACTION_DUE_DEBT_ALERT"
        const val EXTRA_DUE_DEBT_ID = "extra_due_debt_id"
        const val EXTRA_PERSON_NAME = "extra_person_name"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_TYPE = "extra_type" // "LENT" or "BORROWED"
        const val EXTRA_DUE_DATE = "extra_due_date"
        const val EXTRA_NOTES = "extra_notes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DUE_DEBT_ALERT) return
        if (!SmartAutomationManager.isSubscriptionAlertsEnabled(context)) return

        val id = intent.getLongExtra(EXTRA_DUE_DEBT_ID, -1L)
        val person = intent.getStringExtra(EXTRA_PERSON_NAME) ?: "Contact"
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: "BORROWED"
        val dueDate = intent.getStringExtra(EXTRA_DUE_DATE) ?: ""
        val notes = intent.getStringExtra(EXTRA_NOTES).orEmpty()

        SubscriptionAlarmScheduler.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = (850000 + (id % 50000)).toInt()

        val formattedAmount = if (amount % 1.0 == 0.0) {
            "${amount.toLong()}"
        } else {
            String.format(Locale.US, "%.2f", amount)
        }

        val (title, body) = if (type.equals("BORROWED", ignoreCase = true)) {
            val notePart = if (notes.isNotBlank()) " Note: $notes" else ""
            Pair(
                "Debt Due Today: $person",
                "You owe ৳$formattedAmount to $person. Due today ($dueDate).$notePart"
            )
        } else {
            val notePart = if (notes.isNotBlank()) " Note: $notes" else ""
            Pair(
                "Due Collection Today: $person",
                "৳$formattedAmount is due from $person today ($dueDate).$notePart"
            )
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "wallet")
        }
        val tapPi = PendingIntent.getActivity(
            context,
            notifId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_ALERT
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, id)
            putExtra(NotificationActionReceiver.EXTRA_ALERT_TYPE, "DUE_DEBT")
            putExtra(NotificationActionReceiver.EXTRA_TITLE, title)
            putExtra(NotificationActionReceiver.EXTRA_BODY, body)
            putExtra(NotificationActionReceiver.EXTRA_SNOOZE_MINUTES, 10)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SubscriptionAlarmScheduler.CHANNEL_FINANCE_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_duorhythm)
            .setColor(Color.parseColor("#0D9488"))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapPi)
            .addAction(R.drawable.ic_schedule, "Snooze 10m", snoozePi)
            .build()

        nm.notify(notifId, notification)
    }
}
