package com.duorhythm.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R

object NotificationHelper {

    const val ASSESSMENT_CHANNEL_ID = "duorhythm_assessment_channel"
    const val ASSESSMENT_CHANNEL_NAME = "Exams & Assessments"

    fun ensureAssessmentChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ASSESSMENT_CHANNEL_ID,
                ASSESSMENT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts for Semester Finals, Midterms, Quizzes, and Tests"
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun showAssessmentNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ) {
        ensureAssessmentChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "portal")
        }
        val pi = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ASSESSMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quiz)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(Color.parseColor("#0284C7"))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(notificationId, notification)
    }
}
