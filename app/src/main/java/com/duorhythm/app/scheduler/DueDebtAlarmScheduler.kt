package com.duorhythm.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.DueDebtItem
import com.duorhythm.app.receiver.DueDebtAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DueDebtAlarmScheduler {

    fun parseDueDate9Am(dueDateStr: String): Long? {
        val clean = dueDateStr.trim()
        if (clean.isBlank()) return null
        val formats = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val date = sdf.parse(clean)
                if (date != null) {
                    val cal = Calendar.getInstance().apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun scheduleDueDebtAlarm(context: Context, item: DueDebtItem) {
        if (item.status.equals("SETTLED", ignoreCase = true)) {
            cancelDueDebtAlarm(context, item.id)
            return
        }
        if (item.dueDate.isBlank()) return
        if (!SmartAutomationManager.isSubscriptionAlertsEnabled(context)) {
            cancelDueDebtAlarm(context, item.id)
            return
        }

        val triggerMs = parseDueDate9Am(item.dueDate) ?: return
        val now = System.currentTimeMillis()
        if (triggerMs <= now) return

        SubscriptionAlarmScheduler.ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, DueDebtAlarmReceiver::class.java).apply {
            action = DueDebtAlarmReceiver.ACTION_DUE_DEBT_ALERT
            putExtra(DueDebtAlarmReceiver.EXTRA_DUE_DEBT_ID, item.id)
            putExtra(DueDebtAlarmReceiver.EXTRA_PERSON_NAME, item.personName)
            putExtra(DueDebtAlarmReceiver.EXTRA_AMOUNT, item.amount)
            putExtra(DueDebtAlarmReceiver.EXTRA_TYPE, item.type)
            putExtra(DueDebtAlarmReceiver.EXTRA_DUE_DATE, item.dueDate)
            putExtra(DueDebtAlarmReceiver.EXTRA_NOTES, item.notes)
        }

        val reqCode = (840000 + (item.id % 50000)).toInt()
        val pi = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ClassAlarmScheduler.setExactAlarm(alarmManager, triggerMs, pi)
    }

    fun cancelDueDebtAlarm(context: Context, id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val reqCode = (840000 + (id % 50000)).toInt()
        val intent = Intent(context, DueDebtAlarmReceiver::class.java).apply {
            action = DueDebtAlarmReceiver.ACTION_DUE_DEBT_ALERT
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    fun resyncAllDuesDebts(context: Context) {
        try {
            val db = DatabaseHelper.getInstance(context)
            val enabled = SmartAutomationManager.isSubscriptionAlertsEnabled(context)
            val list = db.getAllDuesDebts()
            for (item in list) {
                if (enabled && !item.status.equals("SETTLED", ignoreCase = true) && item.dueDate.isNotBlank()) {
                    scheduleDueDebtAlarm(context, item)
                } else {
                    cancelDueDebtAlarm(context, item.id)
                }
            }
        } catch (_: Exception) {}
    }
}
