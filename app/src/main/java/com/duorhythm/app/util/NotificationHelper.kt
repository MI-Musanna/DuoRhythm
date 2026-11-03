package com.duorhythm.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

    private const val CHANNEL_BRIEFING = "duorhythm_daily_briefing"

    fun showUnifiedDailyBriefing(context: Context, db: DatabaseHelper, profileId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Purge any lingering legacy notification IDs from notification tray
        notificationManager.cancel(1001)
        notificationManager.cancel(1002)
        notificationManager.cancel(1003)
        notificationManager.cancel(2001)
        notificationManager.cancel(2002)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_BRIEFING,
                "Daily Academic Briefing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Consolidated daily academic and financial agenda"
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

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Daily Agenda • ${SimpleDateFormat("EEEE, MMM d", Locale.ENGLISH).format(Date())}")

        // 1. Classes High-Density Format (Handles 1 to 10 classes)
        if (classes.isNotEmpty()) {
            val shown = classes.take(2).joinToString(", ") { "${it.subjectName} (${it.startTime})" }
            val extra = if (classes.size > 2) " (+${classes.size - 2} more)" else ""
            inboxStyle.addLine("📚 Classes (${classes.size}): $shown$extra")
        } else {
            inboxStyle.addLine("📚 Classes: No lectures today")
        }

        // 2. Exams High-Density Format
        if (examsToday.isNotEmpty()) {
            val shown = examsToday.take(2).joinToString(", ") { "${it.title} at ${it.dueTime}" }
            val extra = if (examsToday.size > 2) " (+${examsToday.size - 2} more)" else ""
            inboxStyle.addLine("📝 Exams (${examsToday.size}): $shown$extra")
        } else {
            inboxStyle.addLine("📝 Exams: All clear today")
        }

        // 3. Subscriptions & Recurring Dues with Grand Total
        if (dueSubs.isNotEmpty()) {
            val totalAmount = dueSubs.sumOf { it.amount }
            val shown = dueSubs.take(2).joinToString(", ") { "${it.title} (৳${it.amount.toInt()})" }
            val extra = if (dueSubs.size > 2) " (+${dueSubs.size - 2} more)" else ""
            inboxStyle.addLine("💳 Dues (${dueSubs.size}): $shown$extra • Total: ৳${totalAmount.toInt()}")
        }

        // Tap Action -> Opens Schedule tab directly
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_DEEP_LINK_DESTINATION", "DESTINATION_SCHEDULE")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            901,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryContent = "${classes.size} classes, ${examsToday.size} exams, ${dueSubs.size} dues pending"

        val notification = NotificationCompat.Builder(context, CHANNEL_BRIEFING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DuoRhythm • Morning Briefing")
            .setContentText(summaryContent)
            .setStyle(inboxStyle)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
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
