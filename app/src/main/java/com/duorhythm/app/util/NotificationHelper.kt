package com.duorhythm.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.ui.main.MainActivity
import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_ID = "duorhythm_briefing_channel_v5"
    private const val CHANNEL_NAME = "Daily Academic & Work Briefings"
    private const val NOTIF_ID_MORNING = 6001
    private const val NOTIF_ID_EVENING = 8001
    private const val PREFS_NOTIF_GUARD = "duorhythm_notif_dedup_guard"

    fun showUnifiedDailyBriefing(
        context: Context,
        isEveningReview: Boolean = false,
        isManualTest: Boolean = false
    ) {
        val todayStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        val guardPrefs = context.getSharedPreferences(PREFS_NOTIF_GUARD, Context.MODE_PRIVATE)
        val guardKey = if (isEveningReview) "last_evening_$todayStamp" else "last_morning_$todayStamp"

        // Strict Deduplication Guard: Never fire twice on the same day unless manually tested
        if (!isManualTest && guardPrefs.getBoolean(guardKey, false)) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val db = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId(context)

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
                description = "Complete daily timetable, exams, and dues briefings"
                setShowBadge(true)
                enableLights(true)
                lightColor = Color.parseColor("#00E5FF")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

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

        if (isEveningReview) {
            targetIsoDay = if (targetIsoDay == 7) 1 else targetIsoDay + 1
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val targetDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
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

        val title = if (isEveningReview) {
            "Tomorrow's Briefing ($targetShortDate)"
        } else {
            "Today's Briefing ($targetShortDate)"
        }

        val collapsedSummary = if (classes.isNotEmpty()) {
            val first = classes.first()
            "${first.startTime} ${first.subjectName} (R:${first.roomNo}) • ${classes.size} Classes, ${exams.size} Exams"
        } else {
            "${classes.size} Classes • ${exams.size} Exams • ${dueSubs.size} Dues"
        }

        // Build Full-Capacity BigTextStyle (Displays ALL classes + ALL exams + ALL bills with zero cutoff!)
        val fullBody = StringBuilder()

        fullBody.append("CLASSES (${classes.size}):\n")
        if (classes.isEmpty()) {
            fullBody.append("• No classes scheduled\n")
        } else {
            classes.forEach { cls ->
                val tag = if (cls.type.contains("PRACTICAL", ignoreCase = true)) "[LAB]" else "[LEC]"
                val room = if (cls.roomNo.isNotBlank()) " (R:${cls.roomNo})" else ""
                fullBody.append("• ${cls.startTime} - ${cls.subjectName} $tag$room\n")
            }
        }

        fullBody.append("\nEXAMS & ASSESSMENTS (${exams.size}):\n")
        if (exams.isEmpty()) {
            fullBody.append("• No exams scheduled\n")
        } else {
            exams.forEach { ex ->
                val room = if (ex.room.isNotBlank()) " (R:${ex.room})" else ""
                fullBody.append("• ${ex.dueTime} - ${ex.title}$room\n")
            }
        }

        fullBody.append("\nBILLS & SUBSCRIPTIONS (${dueSubs.size}):\n")
        if (dueSubs.isEmpty()) {
            fullBody.append("• All recurring bills clear")
        } else {
            dueSubs.forEachIndexed { index, sub ->
                val suffix = if (index < dueSubs.size - 1) "\n" else ""
                fullBody.append("• ${sub.title}: ৳${sub.amount}$suffix")
            }
        }

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(title)
            .setSummaryText("${classes.size} Classes • ${exams.size} Exams • ${dueSubs.size} Dues")
            .bigText(fullBody.toString().trim())

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            if (isEveningReview) 801 else 601,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duorhythm)
            .setColor(Color.parseColor("#00E5FF"))
            .setContentTitle(title)
            .setContentText(collapsedSummary)
            .setStyle(bigTextStyle)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        val notifId = if (isEveningReview) NOTIF_ID_EVENING else NOTIF_ID_MORNING
        notificationManager.notify(notifId, notification)

        if (!isManualTest) {
            guardPrefs.edit().putBoolean(guardKey, true).apply()
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
