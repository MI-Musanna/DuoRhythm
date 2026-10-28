package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.scheduler.OrderAlarmScheduler

class OrderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ORDER_ALERT = "com.duorhythm.app.ACTION_ORDER_ALERT"
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_ORDER_TITLE = "extra_order_title"
        const val EXTRA_CLIENT_NAME = "extra_client_name"
        const val EXTRA_ORDER_DEADLINE = "extra_order_deadline"
        const val EXTRA_HOURS_REMAINING = "extra_hours_remaining"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val orderId = intent.getLongExtra(EXTRA_ORDER_ID, System.currentTimeMillis())
        val orderTitle = intent.getStringExtra(EXTRA_ORDER_TITLE) ?: "Client Deliverable"
        val clientName = intent.getStringExtra(EXTRA_CLIENT_NAME)?.takeIf { it.isNotBlank() } ?: "Direct Client"
        val orderDeadline = intent.getStringExtra(EXTRA_ORDER_DEADLINE) ?: "Today"
        val hoursRemaining = intent.getIntExtra(EXTRA_HOURS_REMAINING, 24)

        OrderAlarmScheduler.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "portal")
        }
        val requestCode = (600000 + (orderId % 50000) * 2 + if (hoursRemaining <= 2) 1 else 0).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationTitle = "[WORK] $orderTitle"
        val notificationBody = "$clientName | Due in $hoursRemaining hours ($orderDeadline)"

        val builder = NotificationCompat.Builder(context, OrderAlarmScheduler.CHANNEL_ID_WORK_ORDERS)
            .setSmallIcon(R.drawable.ic_work)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setColor(0xFF10B981.toInt()) // Emerald Green tint
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        nm.notify(requestCode, builder.build())
    }
}
