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
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.receiver.SubscriptionActionReceiver
import com.duorhythm.app.scheduler.SubscriptionAlarmScheduler
import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_BRIEFING = "duorhythm_daily_briefing"
    const val ASSESSMENT_CHANNEL_ID = "duorhythm_assessment_channel"
    const val ASSESSMENT_CHANNEL_NAME = "Exams & Assessments"

    fun showUnifiedDailyBriefing(context: Context, db: DatabaseHelper, profileId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_BRIEFING,
                "Daily Academic Briefing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Unified morning schedule, assessments, and dues"
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

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Daily Agenda • ${SimpleDateFormat("EEEE, MMM d", Locale.ENGLISH).format(Date())}")

        // 1. Add All Classes
        if (classes.isNotEmpty()) {
            inboxStyle.addLine("📚 Classes (${classes.size}):")
            for (c in classes) {
                inboxStyle.addLine("  • ${c.subjectName} (${c.startTime} - ${c.endTime}, Room ${c.room})")
            }
        } else {
            inboxStyle.addLine("📚 Classes: No lectures today")
        }

        // 2. Add All Today's Exams
        if (examsToday.isNotEmpty()) {
            inboxStyle.addLine("📝 Exams Today (${examsToday.size}):")
            for (e in examsToday) {
                inboxStyle.addLine("  • ${e.title} at ${e.dueTime} (Room ${e.room ?: "TBA"})")
            }
        }

        // 3. Add Subscriptions / Dues
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val subs = db.getSubscriptions(profileId)
        val dueSubs = subs.filter { sub ->
            val sStart = sub.startDay.coerceIn(1, maxDays)
            val sEnd = sub.endDay.coerceIn(sStart, maxDays)
            if (sub.isRange) currentDay in sStart..sEnd else currentDay == sub.dueDay.coerceIn(1, maxDays)
        }
        if (dueSubs.isNotEmpty()) {
            inboxStyle.addLine("💳 Subscriptions Due (${dueSubs.size}):")
            for (s in dueSubs) {
                inboxStyle.addLine("  • ${s.title} (Cost: ${s.amount})")
            }
        }

        val summaryText = "${classes.size} classes, ${examsToday.size} exams, ${dueSubs.size} dues"

        val notification = NotificationCompat.Builder(context, CHANNEL_BRIEFING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DuoRhythm • Morning Briefing")
            .setContentText(summaryText)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    fun showMorningBriefing(context: Context, db: DatabaseHelper, profileId: Long) {
        showUnifiedDailyBriefing(context, db, profileId)
    }

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

    fun postSubscriptionDueNotification(context: Context, sub: SubscriptionEntity) {
        SubscriptionAlarmScheduler.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val sourceText = if (sub.linkedAccountId == null || sub.linkedAccountId <= 0L) {
            "Payer: External"
        } else {
            "Source: ${sub.accountName.ifBlank { "Account" }}"
        }

        val cal = Calendar.getInstance()
        val dueDesc = if (sub.isRange) {
            "Window: Day ${DateRollValidator.getClampedDay(sub.startDay, cal)} - ${DateRollValidator.getClampedDay(sub.endDay, cal)}"
        } else {
            "Due today (Day ${DateRollValidator.getClampedDay(sub.dueDay, cal)})"
        }

        val markPaidIntent = Intent(context, SubscriptionActionReceiver::class.java).apply {
            action = SubscriptionActionReceiver.ACTION_MARK_SUB_PAID
            putExtra(SubscriptionActionReceiver.EXTRA_SUB_ID, sub.id)
            putExtra(SubscriptionActionReceiver.EXTRA_NOTIFICATION_ID, (92000 + sub.id).toInt())
        }
        val markPaidPi = PendingIntent.getBroadcast(
            context,
            (92000 + sub.id).toInt(),
            markPaidIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, SubscriptionAlarmScheduler.CHANNEL_FINANCE_ALERTS)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle("Subscription Due: ${sub.name}")
            .setContentText("Cost: ${sub.amount.toInt()} • $dueDesc • $sourceText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Subscription '${sub.name}' is due.\nAmount: ${sub.amount.toInt()}\n$dueDesc • $sourceText")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_check,
                "Mark as Paid",
                markPaidPi
            )
            .build()

        nm.notify((92000 + sub.id).toInt(), notif)
    }
}
