package com.duorhythm.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.ui.main.MainActivity
import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_ID = "duorhythm_briefing_channel_v3"
    private const val CHANNEL_NAME = "Daily Academic & Work Briefings"
    private const val NOTIF_ID_MORNING = 6001
    private const val NOTIF_ID_EVENING = 8001

    fun showUnifiedDailyBriefing(context: Context, isEveningReview: Boolean = false) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val db = DatabaseHelper.getInstance(context)
            val profileId = ProfileManager.getActiveProfileId(context)

            // 1. Enforce Crisp Audible & Vibrating Heads-Up Notification Channel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Executive timetable, exams, and daily dues briefings"
                    setShowBadge(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250)
                    setSound(soundUri, audioAttributes)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Clean any legacy zombie notification IDs
            notificationManager.cancel(1001)
            notificationManager.cancel(1002)
            notificationManager.cancel(1003)
            notificationManager.cancel(2001)
            notificationManager.cancel(5001)
            notificationManager.cancel(20101)

            val cal = Calendar.getInstance()
            var targetIsoDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

            // If evening review (8 PM), target TOMORROW's schedule
            if (isEveningReview) {
                targetIsoDay = if (targetIsoDay == 7) 1 else targetIsoDay + 1
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val targetDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            val targetDayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
            val targetShortDate = SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(cal.time)

            val classes = db.getClassesForIsoDay(profileId, targetIsoDay)
            val exams = db.getUpcomingExamsForWidget(profileId).filter { it.dueDate == targetDateStr }
            val subs = db.getSubscriptions(profileId)

            val currentDay = cal.get(Calendar.DAY_OF_MONTH)
            val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val dueSubs = subs.filter { sub ->
                val sStart = sub.startDay.coerceIn(1, maxDays)
                val sEnd = sub.endDay.coerceIn(sStart, maxDays)
                if (sub.isRange) currentDay in sStart..sEnd else currentDay == sub.dueDay.coerceIn(1, maxDays)
            }

            // Header Labels
            val headerTitle = if (isEveningReview) "Tomorrow's Academic Briefing" else "Today's Academic Briefing"
            val headerBadge = if (isEveningReview) "TOMORROW • $targetDayName" else "TODAY • $targetDayName"

            // Format Classes String
            val classDetails = if (classes.isNotEmpty()) {
                classes.joinToString("\n") {
                    val roomStr = if (it.roomNo.isNotBlank()) " • R:${it.roomNo}" else ""
                    val typeStr = if (it.type.isNotBlank()) " [${it.type}]" else ""
                    "• ${it.subjectName}$typeStr\n  ${it.startTime} - ${it.endTime}$roomStr"
                }
            } else {
                "• No academic lectures scheduled"
            }

            // Format Exams & Dues String
            val duesList = mutableListOf<String>()
            if (exams.isNotEmpty()) {
                exams.forEach {
                    val roomStr = if (it.room.isNotBlank()) " (R:${it.room})" else ""
                    duesList.add("• Exam: ${it.title}\n  ${it.dueTime}$roomStr")
                }
            }
            if (dueSubs.isNotEmpty()) {
                dueSubs.forEach {
                    val extTag = if (it.paymentSource.contains("External", ignoreCase = true)) " [External]" else ""
                    duesList.add("• ${it.title}: ৳${it.amount}$extTag")
                }
            }
            if (duesList.isEmpty()) {
                duesList.add("• All exams & financial dues clear")
            }
            val rightColumnText = duesList.joinToString("\n")

            // Construct Rich Collapsed RemoteViews
            val collapsedView = RemoteViews(context.packageName, R.layout.notification_agenda_collapsed).apply {
                setTextViewText(R.id.tvNotifCollapsedDate, "• $targetShortDate")
                val firstClass = classes.firstOrNull()
                val headline = if (firstClass != null) {
                    "${firstClass.subjectName} (${firstClass.startTime}) • ${classes.size} Classes"
                } else {
                    "Schedule Clear • ${classes.size} Classes"
                }
                setTextViewText(R.id.tvNotifCollapsedHeadline, headline)
                setTextViewText(R.id.badgeNotifClassCount, "${classes.size} Classes")
            }

            // Construct Rich Expanded RemoteViews
            val expandedView = RemoteViews(context.packageName, R.layout.notification_agenda_expanded).apply {
                setTextViewText(R.id.tvNotifExpandedDateBadge, headerBadge)
                setTextViewText(R.id.tvNotifExpandedClasses, classDetails)
                setTextViewText(R.id.tvNotifExpandedExamsDues, rightColumnText)
            }

            // Setup Open Action
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                if (isEveningReview) 801 else 601,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Native System Strings (These prevent 'Custom app notification' in Android Notification History!)
            val nativeTitle = "DuoRhythm: $headerTitle ($targetShortDate)"
            val nativeSummary = "${classes.size} classes, ${exams.size} exams, ${dueSubs.size} dues on agenda"

            val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_schedule)
                // MANDATORY SYSTEM METADATA: Populates Notification History & Lockscreen
                .setContentTitle(nativeTitle)
                .setContentText(nativeSummary)
                .setSubText(if (isEveningReview) "Evening Review" else "Morning Briefing")
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 250, 150, 250))

            val notifId = if (isEveningReview) NOTIF_ID_EVENING else NOTIF_ID_MORNING
            notificationManager.notify(notifId, notifBuilder.build())
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
