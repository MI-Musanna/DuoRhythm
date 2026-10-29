package com.duorhythm.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.AppPreferences
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.data.model.WorkOrder
import com.duorhythm.app.receiver.ClassAlarmReceiver
import com.duorhythm.app.receiver.ClassEndReceiver
import com.duorhythm.app.receiver.OrderAlarmReceiver
import com.duorhythm.app.receiver.ScheduleReceiver
import com.duorhythm.app.service.ReminderManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmSyncManager {

    const val ACTION_CLASS_LEAD_ALERT = "com.duorhythm.app.ACTION_CLASS_LEAD_ALERT"
    const val ACTION_CLASS_START_DND = "com.duorhythm.app.ACTION_CLASS_START_DND"
    const val ACTION_CLASS_END_RESTORE = "com.duorhythm.app.ACTION_CLASS_END_RESTORE"

    const val EXTRA_CLASS_ID = "extra_class_id"
    const val EXTRA_SUBJECT_NAME = "extra_subject_name"
    const val EXTRA_ROOM = "extra_room"
    const val EXTRA_TEACHER = "extra_teacher"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_END_TIME = "extra_end_time"
    const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
    const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"

    /**
     * Deterministic Request Code Architecture:
     * alertType: 1 = Lead Reminder, 2 = Class Start DND, 3 = Class End Restore
     */
    fun getRequestCode(classId: Long, alertType: Int): Int {
        return (classId.toInt() * 10) + alertType
    }

    /**
     * Explicit Alarm Cancellation (Purge Zombie Alarms)
     */
    fun cancelClassAlarms(context: Context, classId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val receivers = listOf(
            ClassAlarmReceiver::class.java,
            ScheduleReceiver::class.java,
            ClassEndReceiver::class.java
        )

        for (type in 1..3) {
            val reqCode = getRequestCode(classId, type)
            for (receiver in receivers) {
                val intent = Intent(context, receiver)
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
        }
    }

    /**
     * Schedules alarms for a timetable class using configurable user lead time.
     */
    fun scheduleClassAlarms(context: Context, item: ClassScheduleItem) {
        // 1. Purge existing alarms first
        cancelClassAlarms(context, item.id)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val startTimePair = ClassAlarmScheduler.parseTime(item.startTime) ?: return
        val endTimePair = ClassAlarmScheduler.parseTime(item.endTime) ?: return

        val startHour = startTimePair.first
        val startMin = startTimePair.second
        val endHour = endTimePair.first
        val endMin = endTimePair.second

        val startMinutes = startHour * 60 + startMin
        var endMinutes = endHour * 60 + endMin
        if (endMinutes < startMinutes) {
            endMinutes += 24 * 60
        }
        val durationMs = (endMinutes - startMinutes) * 60 * 1000L

        val targetCalendarDay = when (item.dayOfWeek) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentDayOfWeek = targetCal.get(Calendar.DAY_OF_WEEK)
        var daysUntil = targetCalendarDay - currentDayOfWeek
        if (daysUntil < 0) {
            daysUntil += 7
        }
        targetCal.add(Calendar.DAY_OF_YEAR, daysUntil)

        var startMs = targetCal.timeInMillis
        var endMs = startMs + durationMs

        // If today's class has already finished, schedule for next week
        if (endMs <= System.currentTimeMillis()) {
            startMs += 7 * 24 * 60 * 60 * 1000L
            endMs += 7 * 24 * 60 * 60 * 1000L
            targetCal.add(Calendar.DAY_OF_YEAR, 7)
        }

        // Calendar Holiday Lookahead: Check if target class date is a marked holiday
        val db = DatabaseHelper.getInstance(context)
        val targetDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(targetCal.time)
        if (db.holidayDao.isHoliday(item.profileId, targetDateStr)) {
            // Suppress alarms on holidays
            return
        }

        val leadMinutes = AppPreferences.getClassLeadMinutes(context)
        val remindersEnabled = ReminderManager.isRemindersEnabled(context)

        // 1. Configurable Lead Reminder Alarm (alertType = 1)
        if (leadMinutes > 0 && remindersEnabled) {
            val preAlertMs = startMs - (leadMinutes * 60 * 1000L)
            if (preAlertMs > System.currentTimeMillis()) {
                val preAlertIntent = Intent(context, ClassAlarmReceiver::class.java).apply {
                    action = ACTION_CLASS_LEAD_ALERT
                    putExtra(EXTRA_CLASS_ID, item.id)
                    putExtra(EXTRA_SUBJECT_NAME, item.subjectName)
                    putExtra(EXTRA_ROOM, item.room)
                    putExtra(EXTRA_TEACHER, item.teacher)
                    putExtra(EXTRA_START_TIME, item.startTime)
                    putExtra(EXTRA_END_TIME, item.endTime)
                    putExtra(EXTRA_DAY_OF_WEEK, item.dayOfWeek)
                    putExtra(EXTRA_LEAD_MINUTES, leadMinutes)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    getRequestCode(item.id, 1),
                    preAlertIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                ClassAlarmScheduler.setExactAlarm(alarmManager, preAlertMs, pi)
            }
        }

        // 2. Class Start DND Alarm (alertType = 2)
        if (SmartAutomationManager.isAutoSilentClassesEnabled(context)) {
            if (startMs > System.currentTimeMillis()) {
                val startIntent = Intent(context, ClassAlarmReceiver::class.java).apply {
                    action = ACTION_CLASS_START_DND
                    putExtra(EXTRA_CLASS_ID, item.id)
                    putExtra(EXTRA_SUBJECT_NAME, item.subjectName)
                    putExtra(EXTRA_ROOM, item.room)
                    putExtra(EXTRA_TEACHER, item.teacher)
                    putExtra(EXTRA_START_TIME, item.startTime)
                    putExtra(EXTRA_END_TIME, item.endTime)
                    putExtra(EXTRA_DAY_OF_WEEK, item.dayOfWeek)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    getRequestCode(item.id, 2),
                    startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                ClassAlarmScheduler.setExactAlarm(alarmManager, startMs, pi)
            }

            // 3. Class End Restore Alarm (alertType = 3)
            if (endMs > System.currentTimeMillis()) {
                val endIntent = Intent(context, ClassAlarmReceiver::class.java).apply {
                    action = ACTION_CLASS_END_RESTORE
                    putExtra(EXTRA_CLASS_ID, item.id)
                    putExtra(EXTRA_SUBJECT_NAME, item.subjectName)
                    putExtra(EXTRA_ROOM, item.room)
                    putExtra(EXTRA_TEACHER, item.teacher)
                    putExtra(EXTRA_START_TIME, item.startTime)
                    putExtra(EXTRA_END_TIME, item.endTime)
                    putExtra(EXTRA_DAY_OF_WEEK, item.dayOfWeek)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    getRequestCode(item.id, 3),
                    endIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                ClassAlarmScheduler.setExactAlarm(alarmManager, endMs, pi)
            }
        }
    }

    fun cancelOrderAlarms(context: Context, orderId: Long) {
        OrderAlarmScheduler.cancelOrderReminders(context, orderId)
    }

    /**
     * Schedules alarms for a client work order using configurable lead mode.
     */
    fun scheduleOrderAlarms(context: Context, order: WorkOrder) {
        cancelOrderAlarms(context, order.id)

        if (order.status.equals("COMPLETED", ignoreCase = true)) {
            return
        }

        val leadHours = AppPreferences.getOrderLeadHours(context)
        if (leadHours <= 0) {
            return
        }

        val deadlineStr = order.endDate.trim()
        if (deadlineStr.isEmpty()) return

        val deadlineMillis = parseDeadlineMillis(deadlineStr) ?: return
        val now = System.currentTimeMillis()

        OrderAlarmScheduler.ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val scheduleInterval = { hours: Int, requestCode: Int ->
            val triggerTime = deadlineMillis - (hours * 60 * 60 * 1000L)
            if (triggerTime > now) {
                val intent = Intent(context, OrderAlarmReceiver::class.java).apply {
                    action = OrderAlarmReceiver.ACTION_ORDER_ALERT
                    putExtra(OrderAlarmReceiver.EXTRA_ORDER_ID, order.id)
                    putExtra(OrderAlarmReceiver.EXTRA_ORDER_TITLE, order.title)
                    putExtra(OrderAlarmReceiver.EXTRA_CLIENT_NAME, order.clientName)
                    putExtra(OrderAlarmReceiver.EXTRA_ORDER_DEADLINE, order.endDate)
                    putExtra(OrderAlarmReceiver.EXTRA_HOURS_REMAINING, hours)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pi)
                    }
                } catch (_: SecurityException) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pi)
                } catch (_: Exception) {}
            }
        }

        scheduleInterval(leadHours, (600000 + (order.id % 50000) * 2).toInt())
    }

    /**
     * Resynchronizes all timetable classes and active work orders application-wide.
     */
    fun resyncAll(context: Context) {
        try {
            val db = DatabaseHelper.getInstance(context)

            // Resync timetable classes
            val classes = db.getAllClasses()
            for (cls in classes) {
                scheduleClassAlarms(context, cls)
            }

            // Resync work orders
            val orders = db.getAllWorkOrders().filter { !it.status.equals("COMPLETED", ignoreCase = true) }
            for (order in orders) {
                scheduleOrderAlarms(context, order)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                        set(Calendar.HOUR_OF_DAY, 18)
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
