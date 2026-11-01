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
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.receiver.SubscriptionActionReceiver
import com.duorhythm.app.scheduler.SubscriptionAlarmScheduler
import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

    fun showMorningBriefing(context: Context, db: DatabaseHelper, profileId: Long) {
        SmartAutomationManager.createNotificationChannels(context)

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

        val classesToday = db.getScheduleForDay(dayOfWeek)
        val highPriorityTasks = db.getAllTasks("HIGH")
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val homeworkDueToday = db.getTodayHomeworkDue(todayStr)
        val classTestsToday = db.getTodayClassTests(todayStr)

        val classCount = classesToday.size
        val taskCount = highPriorityTasks.size
        val homeworkCount = homeworkDueToday.size
        val testCount = classTestsToday.size

        val summaryText = buildString {
            if (classCount > 0) {
                val firstClass = classesToday.first()
                append("$classCount class${if (classCount > 1) "es" else ""} today (first: ${firstClass.subjectName} at ${firstClass.startTime})")
            } else {
                append("No classes scheduled today")
            }

            if (homeworkCount > 0) {
                append("\n$homeworkCount homework/assignment${if (homeworkCount > 1) "s" else ""} due: ")
                append(homeworkDueToday.take(2).joinToString(", ") { "${it.first.title} (${it.second.name})" })
                if (homeworkCount > 2) append(" +${homeworkCount - 2} more")
            }

            if (testCount > 0) {
                append("\n$testCount Class Test/Quiz today: ")
                append(classTestsToday.take(2).joinToString(", ") { "${it.first.title} (${it.second.name})" })
                if (testCount > 2) append(" +${testCount - 2} more")
            }

            if (taskCount > 0) {
                append("\n$taskCount high-priority task${if (taskCount > 1) "s" else ""} on agenda")
            }

            if (classCount == 0 && homeworkCount == 0 && testCount == 0 && taskCount == 0) {
                append("\nAll clear for today! No classes or urgent deadlines scheduled.")
            }
        }.trim()

        val headlineText = when {
            testCount > 0 -> "$testCount Test${if (testCount > 1) "s" else ""} & $classCount class${if (classCount > 1) "es" else ""} today"
            homeworkCount > 0 -> "$homeworkCount Due Today • $classCount class${if (classCount > 1) "es" else ""}"
            classCount > 0 -> "$classCount class${if (classCount > 1) "es" else ""} today"
            else -> "Morning Academic Briefing"
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            20101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_BRIEFINGS)
            .setSmallIcon(R.drawable.ic_schedule)
            .setContentTitle(headlineText)
            .setContentText(summaryText.replace("\n", " • "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(20101, notification)
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

        // Action Intent: Mark as Paid
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
