package com.duorhythm.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_BRIEFING = "duorhythm_unified_agenda"

    fun showUnifiedDailyBriefing(context: Context, db: DatabaseHelper, profileId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Clear all ghost IDs
        notificationManager.cancel(1001)
        notificationManager.cancel(1002)
        notificationManager.cancel(1003)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_BRIEFING,
                "Daily Unified Agenda",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Custom dashboard notifications for academic routines and dues"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val cal = Calendar.getInstance()
        val isoDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val classes = db.getClassesForIsoDay(profileId, isoDay)
        val examsToday = db.getAssessments(profileId).filter {
            it.dueDate == todayStr && it.status.uppercase() !in listOf("COMPLETED", "DONE", "FINISHED")
        }

        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val subs = db.getSubscriptions(profileId)
        val dueSubs = subs.filter { sub ->
            val sStart = sub.startDay.coerceIn(1, maxDays)
            val sEnd = sub.endDay.coerceIn(sStart, maxDays)
            if (sub.isRange) currentDay in sStart..sEnd else currentDay == sub.dueDay.coerceIn(1, maxDays)
        }

        // Format Classes Text
        val classText = if (classes.isNotEmpty()) {
            classes.joinToString("\n") { "• ${it.subjectName} (${it.startTime})" }
        } else {
            "• No lectures today"
        }

        // Format Dues & Exams Text
        val duesList = mutableListOf<String>()
        if (examsToday.isNotEmpty()) {
            examsToday.forEach { duesList.add("• Exam: ${it.title} (${it.dueTime})") }
        }
        if (dueSubs.isNotEmpty()) {
            val total = dueSubs.sumOf { it.amount }
            dueSubs.forEach { duesList.add("• ${it.title}: ৳${if (it.amount % 1.0 == 0.0) it.amount.toInt().toString() else it.amount.toString()}") }
            duesList.add("• Total Due: ৳${if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()}")
        }
        if (duesList.isEmpty()) {
            duesList.add("• All dues & exams clear")
        }
        val rightColumnText = duesList.joinToString("\n")

        // Build Custom RemoteViews
        val expandedView = RemoteViews(context.packageName, R.layout.notification_agenda_expanded).apply {
            setTextViewText(R.id.tvNotifDateBadge, SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(Date()))
            setTextViewText(R.id.tvNotifClassesContent, classText)
            setTextViewText(R.id.tvNotifDuesContent, rightColumnText)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            901,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BRIEFING)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(expandedView)
            .setContentTitle("DuoRhythm • Daily Agenda")
            .setContentText("${classes.size} classes, ${dueSubs.size} dues pending today")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup("DUORHYTHM_SINGLE_BRIEFING")
            .setGroupSummary(false)
            .build()

        notificationManager.notify(1001, notification)
    }

    fun showMorningBriefing(context: Context, db: DatabaseHelper, profileId: Long) {
        showUnifiedDailyBriefing(context, db, profileId)
    }

    fun postSubscriptionDueNotification(context: Context, sub: SubscriptionEntity) {
        // Consolidated in showUnifiedDailyBriefing to prevent duplicate individual alerts
    }

    fun showAssessmentNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ) {
        // Consolidated in showUnifiedDailyBriefing
    }
}
