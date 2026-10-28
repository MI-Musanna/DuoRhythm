package com.duorhythm.app.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import com.duorhythm.app.data.model.WorkOrder
import com.duorhythm.app.receiver.OrderAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object OrderAlarmScheduler {

    const val CHANNEL_ID_WORK_ORDERS = "duorhythm_work_orders_channel"
    const val CHANNEL_NAME_WORK_ORDERS = "Work & Client Order Reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_WORK_ORDERS,
                CHANNEL_NAME_WORK_ORDERS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority deadline reminders for client deliverables and work orders"
                enableLights(true)
                lightColor = Color.parseColor("#10B981") // Emerald Green
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun scheduleOrderReminders(context: Context, order: WorkOrder) {
        if (order.status.equals("COMPLETED", ignoreCase = true)) {
            cancelOrderReminders(context, order.id)
            return
        }

        val deadlineStr = order.endDate.trim()
        if (deadlineStr.isEmpty()) return

        val deadlineMillis = parseDeadlineMillis(deadlineStr) ?: return
        val now = System.currentTimeMillis()

        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // 1. Alarm 24 Hours prior to deadline
        val trigger24h = deadlineMillis - (24 * 60 * 60 * 1000L)
        if (trigger24h > now) {
            scheduleSingleAlarm(
                context = context,
                alarmManager = alarmManager,
                order = order,
                triggerTime = trigger24h,
                hoursRemaining = 24,
                requestCode = (600000 + (order.id % 50000) * 2).toInt()
            )
        }

        // 2. Alarm 2 Hours prior to deadline
        val trigger2h = deadlineMillis - (2 * 60 * 60 * 1000L)
        if (trigger2h > now) {
            scheduleSingleAlarm(
                context = context,
                alarmManager = alarmManager,
                order = order,
                triggerTime = trigger2h,
                hoursRemaining = 2,
                requestCode = (600000 + (order.id % 50000) * 2 + 1).toInt()
            )
        }
    }

    private fun scheduleSingleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        order: WorkOrder,
        triggerTime: Long,
        hoursRemaining: Int,
        requestCode: Int
    ) {
        val intent = Intent(context, OrderAlarmReceiver::class.java).apply {
            action = OrderAlarmReceiver.ACTION_ORDER_ALERT
            putExtra(OrderAlarmReceiver.EXTRA_ORDER_ID, order.id)
            putExtra(OrderAlarmReceiver.EXTRA_ORDER_TITLE, order.title)
            putExtra(OrderAlarmReceiver.EXTRA_CLIENT_NAME, order.clientName)
            putExtra(OrderAlarmReceiver.EXTRA_ORDER_DEADLINE, order.endDate)
            putExtra(OrderAlarmReceiver.EXTRA_HOURS_REMAINING, hoursRemaining)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (_: Exception) {}
    }

    fun cancelOrderReminders(context: Context, orderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, OrderAlarmReceiver::class.java).apply {
            action = OrderAlarmReceiver.ACTION_ORDER_ALERT
        }

        val req24h = (600000 + (orderId % 50000) * 2).toInt()
        val pi24h = PendingIntent.getBroadcast(
            context,
            req24h,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi24h)
        pi24h.cancel()

        val req2h = (600000 + (orderId % 50000) * 2 + 1).toInt()
        val pi2h = PendingIntent.getBroadcast(
            context,
            req2h,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi2h)
        pi2h.cancel()
    }

    fun rescheduleAllActiveOrders(context: Context) {
        try {
            val db = com.duorhythm.app.data.local.db.DatabaseHelper.getInstance(context)
            val activeOrders = db.getAllWorkOrders().filter { !it.status.equals("COMPLETED", ignoreCase = true) }
            for (order in activeOrders) {
                scheduleOrderReminders(context, order)
            }
        } catch (_: Exception) {}
    }

    private fun parseDeadlineMillis(deadlineStr: String): Long? {
        val formatsWithTime = listOf(
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm",
            "MM/dd/yyyy HH:mm"
        )
        for (fmt in formatsWithTime) {
            try {
                val date = SimpleDateFormat(fmt, Locale.US).parse(deadlineStr)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }

        val formatsDateOnly = listOf(
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy"
        )
        for (fmt in formatsDateOnly) {
            try {
                val date = SimpleDateFormat(fmt, Locale.US).parse(deadlineStr)
                if (date != null) {
                    val cal = Calendar.getInstance().apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, 18) // 6:00 PM close of business default
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
