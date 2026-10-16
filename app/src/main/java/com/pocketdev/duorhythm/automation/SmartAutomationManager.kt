package com.pocketdev.duorhythm.automation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.pocketdev.duorhythm.data.model.ClassScheduleItem
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.receiver.ClassDndReceiver
import com.pocketdev.duorhythm.receiver.DailyAutomationReceiver
import com.pocketdev.duorhythm.service.FinancialNotificationListenerService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object SmartAutomationManager {

    const val PREFS_NAME = "lifesync_prefs"

    const val KEY_AUTO_SILENT_CLASSES = "key_auto_silent_classes"
    const val KEY_EXPENSE_DETECTION = "key_expense_detection"
    const val KEY_SMART_BRIEFINGS = "key_smart_briefings"
    const val KEY_SUBSCRIPTION_ALERTS = "key_subscription_alerts"

    const val CHANNEL_EXPENSE_DETECTION = "lifesync_expense_detection"
    const val CHANNEL_BRIEFINGS = "lifesync_briefings_channel"
    const val CHANNEL_SUBSCRIPTIONS = "lifesync_subscriptions_channel"
    const val CHANNEL_DND = "lifesync_dnd_channel"

    private const val REQUEST_CODE_MORNING = 31001
    private const val REQUEST_CODE_EVENING = 31002
    private const val REQUEST_CODE_SUBSCRIPTION = 31003

    fun init(context: Context) {
        createNotificationChannels(context)
        scheduleMorningBriefing(context)
        scheduleEveningBriefing(context)
        scheduleSubscriptionCheck(context)
        rescheduleAllClassDnd(context)
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Expense Detection Channel
            val expenseChannel = NotificationChannel(
                CHANNEL_EXPENSE_DETECTION,
                "Smart Expense Detection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Interactive 1-tap logging prompts for bKash, Nagad, and bank transactions"
                enableLights(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(expenseChannel)

            // 2. Morning & Evening Briefings
            val briefingChannel = NotificationChannel(
                CHANNEL_BRIEFINGS,
                "Daily Smart Briefings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "8:00 AM class/task agenda and 10:00 PM daily expense summary"
            }
            nm.createNotificationChannel(briefingChannel)

            // 3. Subscriptions Countdown & Renewals
            val subChannel = NotificationChannel(
                CHANNEL_SUBSCRIPTIONS,
                "Subscription Renewals",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "24-48h advance renewal alerts and automatic recurring log confirmation"
                enableLights(true)
            }
            nm.createNotificationChannel(subChannel)

            // 4. Class DND status channel
            val dndChannel = NotificationChannel(
                CHANNEL_DND,
                "Lecture Silent Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status notifications when phone enters or leaves class silent mode"
            }
            nm.createNotificationChannel(dndChannel)
        }
    }

    // ==========================================
    // PERMISSION CHECKERS
    // ==========================================
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, FinancialNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    fun isNotificationPolicyAccessGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            return nm?.isNotificationPolicyAccessGranted == true
        }
        return true
    }

    // ==========================================
    // PREFERENCE HELPERS
    // ==========================================
    fun isAutoSilentClassesEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SILENT_CLASSES, true)
    }

    fun setAutoSilentClassesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SILENT_CLASSES, enabled)
            .apply()

        if (enabled) {
            rescheduleAllClassDnd(context)
        } else {
            cancelAllClassDnd(context)
        }
    }

    fun isExpenseDetectionEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXPENSE_DETECTION, true)
    }

    fun setExpenseDetectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXPENSE_DETECTION, enabled)
            .apply()
    }

    fun isSmartBriefingsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SMART_BRIEFINGS, true)
    }

    fun setSmartBriefingsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SMART_BRIEFINGS, enabled)
            .apply()

        if (enabled) {
            scheduleMorningBriefing(context)
            scheduleEveningBriefing(context)
        }
    }

    fun isSubscriptionAlertsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUBSCRIPTION_ALERTS, true)
    }

    fun setSubscriptionAlertsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUBSCRIPTION_ALERTS, enabled)
            .apply()

        if (enabled) {
            scheduleSubscriptionCheck(context)
        }
    }

    // ==========================================
    // DAILY BRIEFING & SUBSCRIPTION SCHEDULERS
    // ==========================================
    fun scheduleMorningBriefing(context: Context) {
        if (!isSmartBriefingsEnabled(context)) return
        scheduleDailyAlarm(context, DailyAutomationReceiver.ACTION_MORNING_BRIEFING, 7, 30, REQUEST_CODE_MORNING)
    }

    fun scheduleEveningBriefing(context: Context) {
        if (!isSmartBriefingsEnabled(context)) return
        scheduleDailyAlarm(context, DailyAutomationReceiver.ACTION_EVENING_BRIEFING, 22, 0, REQUEST_CODE_EVENING)
    }

    fun scheduleSubscriptionCheck(context: Context) {
        if (!isSubscriptionAlertsEnabled(context)) return
        scheduleDailyAlarm(context, DailyAutomationReceiver.ACTION_SUBSCRIPTION_CHECK, 9, 0, REQUEST_CODE_SUBSCRIPTION)
    }

    private fun scheduleDailyAlarm(
        context: Context,
        action: String,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, DailyAutomationReceiver::class.java).apply {
            this.action = action
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    // ==========================================
    // CLASS SCHEDULE DND SCHEDULERS
    // ==========================================
    fun scheduleClassDndForClass(context: Context, item: ClassScheduleItem) {
        if (!isAutoSilentClassesEnabled(context)) return

        val startTimePair = parseTime(item.startTime) ?: return
        val endTimePair = parseTime(item.endTime) ?: return

        val startMs = calculateNextOccurrenceMs(item.dayOfWeek, startTimePair.first, startTimePair.second)
        val endMs = calculateNextOccurrenceMs(item.dayOfWeek, endTimePair.first, endTimePair.second)

        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // 1. Class Start Alarm
        if (startMs > System.currentTimeMillis()) {
            val startIntent = Intent(context, ClassDndReceiver::class.java).apply {
                action = ClassDndReceiver.ACTION_CLASS_START
                putExtra(ClassDndReceiver.EXTRA_CLASS_ID, item.id)
                putExtra(ClassDndReceiver.EXTRA_SUBJECT_NAME, item.subjectName)
                putExtra(ClassDndReceiver.EXTRA_END_TIME, item.endTime)
            }
            val startPi = PendingIntent.getBroadcast(
                context,
                (400000 + item.id).toInt(),
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startMs, startPi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, startMs, startPi)
                }
            } catch (_: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, startMs, startPi)
            }
        }

        // 2. Class End Alarm
        if (endMs > System.currentTimeMillis()) {
            val endIntent = Intent(context, ClassDndReceiver::class.java).apply {
                action = ClassDndReceiver.ACTION_CLASS_END
                putExtra(ClassDndReceiver.EXTRA_CLASS_ID, item.id)
                putExtra(ClassDndReceiver.EXTRA_SUBJECT_NAME, item.subjectName)
            }
            val endPi = PendingIntent.getBroadcast(
                context,
                (500000 + item.id).toInt(),
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMs, endPi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, endMs, endPi)
                }
            } catch (_: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, endMs, endPi)
            }
        }
    }

    fun cancelClassDndForClass(context: Context, classId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val startIntent = Intent(context, ClassDndReceiver::class.java).apply {
            action = ClassDndReceiver.ACTION_CLASS_START
        }
        val startPi = PendingIntent.getBroadcast(
            context,
            (400000 + classId).toInt(),
            startIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (startPi != null) {
            am.cancel(startPi)
            startPi.cancel()
        }

        val endIntent = Intent(context, ClassDndReceiver::class.java).apply {
            action = ClassDndReceiver.ACTION_CLASS_END
        }
        val endPi = PendingIntent.getBroadcast(
            context,
            (500000 + classId).toInt(),
            endIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (endPi != null) {
            am.cancel(endPi)
            endPi.cancel()
        }
    }

    fun rescheduleAllClassDnd(context: Context) {
        if (!isAutoSilentClassesEnabled(context)) return

        val db = DatabaseHelper.getInstance(context)
        val classes = db.getAllClasses()
        for (item in classes) {
            scheduleClassDndForClass(context, item)
        }
    }

    private fun cancelAllClassDnd(context: Context) {
        val db = DatabaseHelper.getInstance(context)
        val classes = db.getAllClasses()
        for (item in classes) {
            cancelClassDndForClass(context, item.id)
        }
    }

    private fun parseTime(timeStr: String): Pair<Int, Int>? {
        val clean = timeStr.trim()
        val formats = listOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val d = sdf.parse(clean)
                if (d != null) {
                    val cal = Calendar.getInstance().apply { time = d }
                    return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun calculateNextOccurrenceMs(dayOfWeek: Int, hourOfDay: Int, minute: Int): Long {
        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetCalendarDay = when (dayOfWeek) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }

        val currentDayOfWeek = targetCal.get(Calendar.DAY_OF_WEEK)
        var daysUntil = targetCalendarDay - currentDayOfWeek
        if (daysUntil < 0 || (daysUntil == 0 && targetCal.timeInMillis <= System.currentTimeMillis())) {
            daysUntil += 7
        }
        targetCal.add(Calendar.DAY_OF_YEAR, daysUntil)
        return targetCal.timeInMillis
    }
}
