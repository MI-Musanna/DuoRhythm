package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.duorhythm.app.R
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.local.db.DatabaseHelper

class ClassDndReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CLASS_START = "com.duorhythm.app.ACTION_CLASS_START"
        const val ACTION_CLASS_END = "com.duorhythm.app.ACTION_CLASS_END"

        const val EXTRA_CLASS_ID = "extra_class_id"
        const val EXTRA_SUBJECT_NAME = "extra_subject_name"
        const val EXTRA_END_TIME = "extra_end_time"

        private const val KEY_SAVED_RINGER_MODE = "saved_ringer_mode"
        private const val KEY_IN_CLASS_DND = "in_class_dnd"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (!SmartAutomationManager.isAutoSilentClassesEnabled(context)) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = context.getSharedPreferences(SmartAutomationManager.PREFS_NAME, Context.MODE_PRIVATE)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val classId = intent.getLongExtra(EXTRA_CLASS_ID, -1L)
        val subjectName = intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: "Class"
        val endTime = intent.getStringExtra(EXTRA_END_TIME) ?: ""

        when (action) {
            ACTION_CLASS_START -> {
                // Save current ringer mode if not already in class DND mode
                if (!prefs.getBoolean(KEY_IN_CLASS_DND, false)) {
                    prefs.edit()
                        .putInt(KEY_SAVED_RINGER_MODE, audioManager.ringerMode)
                        .putBoolean(KEY_IN_CLASS_DND, true)
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

            ACTION_CLASS_END -> {
                // Restore saved ringer mode
                val savedMode = prefs.getInt(KEY_SAVED_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                try {
                    audioManager.ringerMode = savedMode
                } catch (_: Exception) {}
                prefs.edit().putBoolean(KEY_IN_CLASS_DND, false).apply()

                // Cancel the class DND notification and show brief restored notification
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

                // Reschedule for next week's occurrence
                if (classId > 0) {
                    val db = DatabaseHelper.getInstance(context)
                    val classItem = db.getAllClasses().firstOrNull { it.id == classId }
                    if (classItem != null) {
                        SmartAutomationManager.scheduleClassDndForClass(context, classItem)
                    }
                }
            }
        }
    }
}
