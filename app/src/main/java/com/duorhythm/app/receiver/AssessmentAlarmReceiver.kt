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
import com.duorhythm.app.service.ReminderManager

class AssessmentAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ASSESSMENT_ALERT = "com.duorhythm.app.ACTION_ASSESSMENT_ALERT"
        const val EXTRA_ASSESSMENT_ID = "extra_assessment_id"
        const val EXTRA_TITLE = "extra_assessment_title"
        const val EXTRA_BODY = "extra_assessment_body"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("pref_exam_reminders_enabled", true)) return

        val assessmentId = intent.getLongExtra(EXTRA_ASSESSMENT_ID, -1L)
        val db = com.duorhythm.app.data.local.db.DatabaseHelper.getInstance(context)
        if (assessmentId <= 0L || !db.doesAssessmentExistForActiveProfile(assessmentId)) {
            if (assessmentId > 0L) com.duorhythm.app.scheduler.AssessmentAlarmScheduler.cancelAlarmsForAssessment(context, assessmentId)
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Upcoming Exam Alert"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "Review your scheduled exam syllabus and room."

        ReminderManager.createNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val notifId = (700000 + (assessmentId % 90000)).toInt()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "portal")
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
            putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, assessmentId)
            putExtra(NotificationActionReceiver.EXTRA_ALERT_TYPE, "EXAM")
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

        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duorhythm)
            .setColor(Color.parseColor("#F59E0B"))
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
