package com.duorhythm.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.local.entity.ClassEntity

object ClassSessionNotificationManager {

    private const val SESSION_CHANNEL_ID = "duorhythm_live_session_channel"
    private const val SESSION_CHANNEL_NAME = "Live Academic Lecture Sessions"
    private const val NOTIF_ID_SESSION = 7701

    fun checkAndPromptDndPermission(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!nm.isNotificationPolicyAccessGranted) {
                AlertDialog.Builder(context)
                    .setTitle("DND Policy Access Required")
                    .setMessage("Android requires 'Do Not Disturb Access' to automatically mute your phone during class. Without this, the system blocks automatic silent mode.")
                    .setPositiveButton("Grant Access") { _, _ ->
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                    .setNegativeButton("Later", null)
                    .show()
                return false
            }
        }
        return true
    }

    fun startLiveClassSession(context: Context, classItem: ClassEntity) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 1. Create High-Priority Notification Channel so icon ALWAYS appears in status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SESSION_CHANNEL_ID,
                SESSION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active ongoing lecture notifications"
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }

        // 2. Hardware Sound Profile: Engage Silent / DND Mode
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (_: Exception) {}

        // 3. Post ONGOING Notification (Cannot be dismissed by 'Clear all' while class is active)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            771,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val instructorText = if (classItem.instructor.isNotBlank()) " • Prof: ${classItem.instructor}" else ""
        val roomText = if (classItem.roomNo.isNotBlank()) "Room: ${classItem.roomNo}" else "Main Hall"

        val notification = NotificationCompat.Builder(context, SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duorhythm) // Pure white vector icon
            .setContentTitle("🔴 IN SESSION: ${classItem.subjectName}")
            .setContentText("${classItem.startTime} - ${classItem.endTime} • $roomText$instructorText")
            .setSubText("Silent Mode Active")
            .setOngoing(true) // PREVENTS ACCIDENTAL CLEAR-ALL WIPEOUT
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .build()

        nm.notify(NOTIF_ID_SESSION, notification)
    }

    fun endLiveClassSession(context: Context, subjectName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 1. Dismiss Ongoing Notification
        nm.cancel(NOTIF_ID_SESSION)

        // 2. Hardware Sound Profile: Restore Normal Ringer Mode
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } catch (_: Exception) {}

        // 3. Post Quick Dismissible Completion Notification
        val notif = NotificationCompat.Builder(context, SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duorhythm)
            .setContentTitle("✅ Class Finished: $subjectName")
            .setContentText("Normal sound profile restored • Ready for next schedule")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(30000) // Auto-cleans itself after 30 seconds
            .build()

        nm.notify(NOTIF_ID_SESSION + 1, notif)
    }
}
