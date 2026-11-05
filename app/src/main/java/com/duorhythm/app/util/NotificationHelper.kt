package com.duorhythm.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.ui.main.MainActivity
import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_ID = "duorhythm_briefing_channel"
    private const val CHANNEL_NAME = "Daily Academic Briefing"
    private const val NOTIFICATION_ID = 6001

    fun showUnifiedDailyBriefing(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val db = DatabaseHelper.getInstance(context)
            val profileId = ProfileManager.getActiveProfileId(context)

            // 1. Clean any zombie notification IDs
            notificationManager.cancel(1001)
            notificationManager.cancel(1002)
            notificationManager.cancel(1003)
            notificationManager.cancel(2001)
            notificationManager.cancel(5001)
            notificationManager.cancel(20101)

            // 2. Register Notification Channel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Executive daily routine and dues briefing"
                    setShowBadge(true)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // 3. Query Database for Today's Real Schedule
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
            val dateHeaderStr = SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(Date())
            val fullDateStr = SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(Date())

            val classes = db.getClassesForIsoDay(profileId, isoDay)
            val exams = db.getUpcomingExamsForWidget(profileId).filter { it.dueDate == todayStr }
            val subs = db.getSubscriptions(profileId)

            val currentDay = cal.get(Calendar.DAY_OF_MONTH)
            val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val dueSubs = subs.filter { sub ->
                val sStart = sub.startDay.coerceIn(1, maxDays)
                val sEnd = sub.endDay.coerceIn(sStart, maxDays)
                if (sub.isRange) currentDay in sStart..sEnd else currentDay == sub.dueDay.coerceIn(1, maxDays)
            }

            // 4. Format Classes Text
            val classDetails = if (classes.isNotEmpty()) {
                classes.joinToString("\n") {
                    val roomStr = if (it.roomNo.isNotBlank()) " • R:${it.roomNo}" else ""
                    "• ${it.subjectName} (${it.startTime}$roomStr)"
                }
            } else {
                "• No lectures scheduled today"
            }

            // 5. Format Exams & Dues Text
            val duesList = mutableListOf<String>()
            if (exams.isNotEmpty()) {
                exams.forEach {
                    val timeStr = if (it.dueTime.isNotBlank()) " (${it.dueTime})" else ""
                    duesList.add("• Exam: ${it.title}$timeStr")
                }
            }
            if (dueSubs.isNotEmpty()) {
                dueSubs.forEach {
                    val formattedAmt = if (it.amount % 1.0 == 0.0) it.amount.toInt().toString() else it.amount.toString()
                    duesList.add("• ${it.title}: ৳$formattedAmt")
                }
            }
            if (duesList.isEmpty()) {
                duesList.add("• All exams & dues clear")
            }
            val examsDuesText = duesList.joinToString("\n")

            // 6. Bind RemoteViews
            val collapsedView = RemoteViews(context.packageName, R.layout.notification_agenda_collapsed).apply {
                setTextViewText(R.id.tvNotifCollapsedDate, "• $dateHeaderStr")
                val firstClass = classes.firstOrNull()
                val headline = if (firstClass != null) {
                    "Next: ${firstClass.subjectName} (${firstClass.startTime}) • ${classes.size} Classes"
                } else {
                    "All clear • ${classes.size} Classes today"
                }
                setTextViewText(R.id.tvNotifCollapsedHeadline, headline)
                setTextViewText(R.id.badgeNotifClassCount, "${classes.size} Classes")
            }

            val expandedView = RemoteViews(context.packageName, R.layout.notification_agenda_expanded).apply {
                setTextViewText(R.id.tvNotifExpandedDateBadge, fullDateStr)
                setTextViewText(R.id.tvNotifExpandedClasses, classDetails)
                setTextViewText(R.id.tvNotifExpandedExamsDues, examsDuesText)
            }

            // 7. Click Intent
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                601,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 8. Build Dual-View Notification
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_schedule)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showUnifiedDailyBriefing(context: Context, db: DatabaseHelper, profileId: Long) {
        showUnifiedDailyBriefing(context)
    }

    fun showMorningBriefing(context: Context, db: DatabaseHelper? = null, profileId: Long? = null) {
        showUnifiedDailyBriefing(context)
    }

    fun postSubscriptionDueNotification(context: Context, sub: SubscriptionEntity? = null) {
        // Consolidated in showUnifiedDailyBriefing to prevent duplicate individual alerts
    }

    fun showAssessmentNotification(
        context: Context,
        notificationId: Int = 0,
        title: String = "",
        body: String = ""
    ) {
        // Consolidated in showUnifiedDailyBriefing
    }
}
