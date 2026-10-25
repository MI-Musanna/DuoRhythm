package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.scheduler.ClassAlarmScheduler
import com.duorhythm.app.service.ReminderManager
import java.util.Calendar

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ClassAlarmScheduler.rescheduleAll(context)
            ReminderManager.rescheduleAll(context)
            SmartAutomationManager.init(context)
            return
        }

        when (action) {
            ClassAlarmScheduler.ACTION_CLASS_PRE_ALERT -> {
                handlePreAlert(context, intent)
            }
            ClassAlarmScheduler.ACTION_CLASS_START -> {
                handleClassStart(context, intent)
            }
        }
    }

    private fun handlePreAlert(context: Context, intent: Intent) {
        if (!ReminderManager.isRemindersEnabled(context)) return

        val classId = intent.getLongExtra(ClassAlarmScheduler.EXTRA_CLASS_ID, -1L)
        val subjectName = intent.getStringExtra(ClassAlarmScheduler.EXTRA_SUBJECT_NAME) ?: "Class"
        val room = intent.getStringExtra(ClassAlarmScheduler.EXTRA_ROOM) ?: ""
        val teacher = intent.getStringExtra(ClassAlarmScheduler.EXTRA_TEACHER) ?: ""
        val startTime = intent.getStringExtra(ClassAlarmScheduler.EXTRA_START_TIME) ?: ""

        val prefs = context.getSharedPreferences(ClassAlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val isInClassDnd = prefs.getBoolean(ClassAlarmScheduler.KEY_IN_CLASS_DND, false)
        val isOngoing = isOngoingClassActive(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        SmartAutomationManager.createNotificationChannels(context)
        ReminderManager.createNotificationChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 100000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pre-Class Notification Polish:
        // If student is already in an ongoing lecture, trigger silently without vibration or ringing
        if (isInClassDnd || isOngoing) {
            val silentMessage = "Starts at $startTime${if (room.isNotBlank()) " in Room $room" else ""}. Silent mode active."
            val silentNotification = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_DND)
                .setSmallIcon(R.drawable.ic_schedule)
                .setContentTitle("Next Lecture in 5m: $subjectName")
                .setContentText(silentMessage)
                .setStyle(NotificationCompat.BigTextStyle().bigText(silentMessage))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null)
                .setSilent(true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notifId = if (classId > 0) (classId.toInt() + 10000) else 1001
            nm?.notify(notifId, silentNotification)
        } else {
            val roomOrSlot = if (room.isNotBlank()) "Room $room" else startTime
            val alertTitle = "Class in 5 mins: $subjectName at $roomOrSlot"
            val details = buildString {
                append("Starts at $startTime")
                if (room.isNotBlank()) append(" • Room $room")
                if (teacher.isNotBlank()) append(" • $teacher")
            }

            val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_schedule)
                .setContentTitle(alertTitle)
                .setContentText(details)
                .setStyle(NotificationCompat.BigTextStyle().bigText(details))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notifId = if (classId > 0) (classId.toInt() + 10000) else 1001
            nm?.notify(notifId, notification)
        }
    }

    private fun handleClassStart(context: Context, intent: Intent) {
        if (!SmartAutomationManager.isAutoSilentClassesEnabled(context)) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = context.getSharedPreferences(ClassAlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val subjectName = intent.getStringExtra(ClassAlarmScheduler.EXTRA_SUBJECT_NAME) ?: "Class"
        val endTime = intent.getStringExtra(ClassAlarmScheduler.EXTRA_END_TIME) ?: ""

        // Save current ringer mode if not already in class DND mode
        if (!prefs.getBoolean(ClassAlarmScheduler.KEY_IN_CLASS_DND, false)) {
            prefs.edit()
                .putInt(ClassAlarmScheduler.KEY_SAVED_RINGER_MODE, audioManager.ringerMode)
                .putBoolean(ClassAlarmScheduler.KEY_IN_CLASS_DND, true)
                .apply()
        }

        // Switch to Silent or Vibrate
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm?.isNotificationPolicyAccessGranted == true) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
        } catch (_: Exception) {
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } catch (_: Exception) {}
        }

        // Show subtle status notification
        SmartAutomationManager.createNotificationChannels(context)
        val notif = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_DND)
            .setSmallIcon(R.drawable.ic_schedule)
            .setContentTitle("Auto-Silent: $subjectName")
            .setContentText("Device muted for lecture${if (endTime.isNotBlank()) " until $endTime" else ""}.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        nm?.notify(30101, notif)
    }

    private fun isOngoingClassActive(context: Context): Boolean {
        return try {
            val cal = Calendar.getInstance()
            val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
            val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val db = DatabaseHelper.getInstance(context)
            val todayClasses = db.timetableDao.getClassesForDay(dayOfWeek)

            todayClasses.any { item ->
                val start = ClassAlarmScheduler.parseMinutes(item.startTime)
                val end = ClassAlarmScheduler.parseMinutes(item.endTime)
                currentMinutes in start until end
            }
        } catch (_: Exception) {
            false
        }
    }
}
