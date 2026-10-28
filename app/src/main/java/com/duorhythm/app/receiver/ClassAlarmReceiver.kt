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
import com.duorhythm.app.data.local.pref.AppPreferences
import com.duorhythm.app.scheduler.AlarmSyncManager
import com.duorhythm.app.scheduler.ClassAlarmScheduler
import com.duorhythm.app.service.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ClassAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AlarmSyncManager.resyncAll(context)
            ReminderManager.rescheduleAll(context)
            SmartAutomationManager.init(context)
            return
        }

        when (action) {
            AlarmSyncManager.ACTION_CLASS_LEAD_ALERT,
            ClassAlarmScheduler.ACTION_CLASS_PRE_ALERT -> {
                handleLeadAlert(context, intent)
            }
            AlarmSyncManager.ACTION_CLASS_START_DND,
            ClassAlarmScheduler.ACTION_CLASS_START -> {
                handleClassStart(context, intent)
            }
            AlarmSyncManager.ACTION_CLASS_END_RESTORE,
            ClassAlarmScheduler.ACTION_CLASS_END -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        handleClassEnd(context, intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun handleLeadAlert(context: Context, intent: Intent) {
        if (!ReminderManager.isRemindersEnabled(context)) return

        val classId = intent.getLongExtra(AlarmSyncManager.EXTRA_CLASS_ID, -1L)
        val subjectName = intent.getStringExtra(AlarmSyncManager.EXTRA_SUBJECT_NAME) ?: "Class"
        val room = intent.getStringExtra(AlarmSyncManager.EXTRA_ROOM) ?: ""
        val teacher = intent.getStringExtra(AlarmSyncManager.EXTRA_TEACHER) ?: ""
        val startTime = intent.getStringExtra(AlarmSyncManager.EXTRA_START_TIME) ?: ""
        val leadMinutes = intent.getIntExtra(AlarmSyncManager.EXTRA_LEAD_MINUTES, AppPreferences.getClassLeadMinutes(context))

        if (leadMinutes <= 0) return

        val prefs = context.getSharedPreferences(ClassAlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val isInClassDnd = prefs.getBoolean(ClassAlarmScheduler.KEY_IN_CLASS_DND, false)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
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

        val roomOrSlot = if (room.isNotBlank()) "Room $room" else startTime
        val alertTitle = "Class in ${leadMinutes}m: $subjectName at $roomOrSlot"
        val details = buildString {
            append("Starts at $startTime")
            if (room.isNotBlank()) append(" • Room $room")
            if (teacher.isNotBlank()) append(" • $teacher")
        }

        val builder = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_schedule)
            .setContentTitle(alertTitle)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (isInClassDnd) {
            builder.setSound(null).setVibrate(null).setSilent(true)
        }

        val notifId = if (classId > 0) (classId.toInt() + 10000) else 1001
        nm.notify(notifId, builder.build())
    }

    private fun handleClassStart(context: Context, intent: Intent) {
        if (!SmartAutomationManager.isAutoSilentClassesEnabled(context)) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = context.getSharedPreferences(ClassAlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val currentMode = audioManager.ringerMode
        if (currentMode != AudioManager.RINGER_MODE_SILENT && currentMode != AudioManager.RINGER_MODE_VIBRATE) {
            prefs.edit().putInt(ClassAlarmScheduler.KEY_SAVED_RINGER_MODE, currentMode).apply()
        }
        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (_: Exception) {}
        prefs.edit().putBoolean(ClassAlarmScheduler.KEY_IN_CLASS_DND, true).apply()

        SmartAutomationManager.createNotificationChannels(context)

        val subjectName = intent.getStringExtra(AlarmSyncManager.EXTRA_SUBJECT_NAME) ?: "Class"
        val room = intent.getStringExtra(AlarmSyncManager.EXTRA_ROOM) ?: ""
        val roomText = if (room.isNotBlank()) " in Room $room" else ""

        val dndNotification = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_DND)
            .setSmallIcon(R.drawable.ic_schedule)
            .setContentTitle("Lecture in Session: $subjectName")
            .setContentText("Device set to silent mode$roomText.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        nm?.notify(30101, dndNotification)
    }

    private fun handleClassEnd(context: Context, intent: Intent) {
        if (!SmartAutomationManager.isAutoSilentClassesEnabled(context)) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = context.getSharedPreferences(ClassAlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val classId = intent.getLongExtra(AlarmSyncManager.EXTRA_CLASS_ID, -1L)
        val subjectName = intent.getStringExtra(AlarmSyncManager.EXTRA_SUBJECT_NAME) ?: "Class"
        val endTimeStr = intent.getStringExtra(AlarmSyncManager.EXTRA_END_TIME) ?: ""
        val dayOfWeek = intent.getIntExtra(AlarmSyncManager.EXTRA_DAY_OF_WEEK, -1)

        val db = DatabaseHelper.getInstance(context)
        val profileId = db.getActiveProfileId()

        val cal = Calendar.getInstance()
        val todayDayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val targetDay = if (dayOfWeek in 1..7) dayOfWeek else todayDayOfWeek

        val todayClasses = db.timetableDao.getClassesForDay(targetDay, profileId)
        val currentClass = todayClasses.firstOrNull { it.id == classId }
        val currentEndTime = currentClass?.endTime ?: endTimeStr

        // Smart Consecutive Class Lookahead: Check if another lecture starts within <= 5 minutes
        val nextClass = db.timetableDao.getNextImmediateClass(profileId, targetDay, currentEndTime)
            ?.takeIf { it.id != classId }

        SmartAutomationManager.createNotificationChannels(context)

        if (nextClass != null) {
            // Consecutive lecture exists:
            // 1. Do NOT restore normal ringer mode. Maintain silent mode continuously.
            prefs.edit().putBoolean(ClassAlarmScheduler.KEY_IN_CLASS_DND, true).apply()
            nm?.cancel(30101)

            val roomText = if (nextClass.room.isNotBlank()) " in Room ${nextClass.room}" else ""
            val transitionContent = "Consecutive Lecture: ${nextClass.subjectName} • Silent mode maintained."

            val transitionNotif = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_DND)
                .setSmallIcon(R.drawable.ic_schedule)
                .setContentTitle("Consecutive Lecture: ${nextClass.subjectName}")
                .setContentText("Silent mode maintained. Class starts at ${nextClass.startTime}$roomText.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(transitionContent))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            nm?.notify(30101, transitionNotif)
        } else {
            // No immediate class exists:
            // Restore normal sound profile and post "Class Ended: ${currentClass.subjectName}"
            val savedMode = prefs.getInt(ClassAlarmScheduler.KEY_SAVED_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
            try {
                audioManager.ringerMode = savedMode
            } catch (_: Exception) {}
            prefs.edit().putBoolean(ClassAlarmScheduler.KEY_IN_CLASS_DND, false).apply()

            nm?.cancel(30101)

            val restoredNotif = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_DND)
                .setSmallIcon(R.drawable.ic_schedule)
                .setContentTitle("Class Ended: $subjectName")
                .setContentText("Normal sound profile restored.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(8000)
                .build()

            nm?.notify(30102, restoredNotif)
        }

        // Reschedule class alarms for next week's occurrence
        if (classId > 0) {
            val itemToReschedule = currentClass ?: db.getAllClasses().firstOrNull { it.id == classId }
            if (itemToReschedule != null) {
                AlarmSyncManager.scheduleClassAlarms(context, itemToReschedule)
            }
        }
    }
}
