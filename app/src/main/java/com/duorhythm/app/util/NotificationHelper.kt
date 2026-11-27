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
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_ID = "duorhythm_briefing_channel_v7"
    private const val CHANNEL_NAME = "Daily Academic & Work Briefings"
    private const val NOTIF_ID_MORNING = 6001
    private const val NOTIF_ID_EVENING = 8001
    private const val PREFS_NOTIF_GUARD = "duorhythm_notif_dedup_guard"

    data class BriefingExamItem(
        val title: String,
        val time: String,
        val room: String
    )

    fun showUnifiedDailyBriefing(
        context: Context,
        isEveningReview: Boolean = false,
        isManualTest: Boolean = false
    ) {
        if (!isManualTest && !SmartAutomationManager.isSmartBriefingsEnabled(context)) {
            return
        }
        val todayStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        val guardPrefs = context.getSharedPreferences(PREFS_NOTIF_GUARD, Context.MODE_PRIVATE)
        val guardKey = if (isEveningReview) "last_evening_$todayStamp" else "last_morning_$todayStamp"

        // Never auto-fire more than once per day (prevents on-install misfires)
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
                description = "Full daily timetable, exams, and dues briefings"
                setShowBadge(true)
                enableLights(true)
                lightColor = Color.parseColor("#38BDF8")
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

        // 1. Classes
        val classes = db.getClassesForIsoDay(profileId, targetIsoDay)

        // 2. Exams (Multi-table union: assessments + subject_work_items deduplicated)
        val examsList = mutableListOf<BriefingExamItem>()
        val seenExams = mutableSetOf<String>()

        try {
            val widgetExams = db.getUpcomingExamsForWidget(profileId).filter { it.dueDate == targetDateStr }
            for (ex in widgetExams) {
                val key = ex.title.lowercase().trim()
                if (seenExams.add(key)) {
                    examsList.add(
                        BriefingExamItem(
                            title = ex.title.ifBlank { ex.type },
                            time = ex.dueTime,
                            room = ex.room
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        try {
            val workItemExams = db.getAllExams(profileId = profileId).filter { (item, _) ->
                item.status != "DONE" && (item.endDate == targetDateStr || item.startDate == targetDateStr)
            }
            for ((item, subject) in workItemExams) {
                val key = item.title.lowercase().trim()
                if (seenExams.add(key)) {
                    examsList.add(
                        BriefingExamItem(
                            title = item.title.ifBlank { subject.name },
                            time = "",
                            room = item.room
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // 3. Pending & Floating Tasks (Include undated tasks like 'ITSS Practical' + tasks due on/before target date)
        val pendingTasks = mutableListOf<Triple<String, String, String>>()
        try {
            val cursor = db.readableDatabase.rawQuery(
                """SELECT title, type, priority, due_date FROM tasks 
                   WHERE profile_id = ? 
                     AND is_completed = 0 
                     AND (due_date IS NULL OR due_date = '' OR due_date <= ?) 
                   ORDER BY CASE WHEN due_date IS NOT NULL AND due_date != '' THEN 0 ELSE 1 END, id DESC LIMIT 10""",
                arrayOf(profileId.toString(), targetDateStr)
            )
            cursor.use { c ->
                val titleIdx = c.getColumnIndex("title")
                val typeIdx = c.getColumnIndex("type")
                val priorityIdx = c.getColumnIndex("priority")
                while (c.moveToNext()) {
                    val tTitle = if (titleIdx != -1) c.getString(titleIdx) ?: "" else ""
                    val tType = if (typeIdx != -1) c.getString(typeIdx) ?: "TASK" else "TASK"
                    val tPriority = if (priorityIdx != -1) c.getString(priorityIdx) ?: "NORMAL" else "NORMAL"
                    if (tTitle.isNotBlank()) {
                        pendingTasks.add(Triple(tTitle, tType, tPriority))
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Active Client Work Orders
        val activeWorkOrders = mutableListOf<Triple<String, String, String>>()
        try {
            val cursor = db.readableDatabase.rawQuery(
                """SELECT title, client_name, deadline FROM work_orders 
                   WHERE profile_id = ? 
                     AND status != 'COMPLETED' 
                   ORDER BY id DESC LIMIT 6""",
                arrayOf(profileId.toString())
            )
            cursor.use { c ->
                val titleIdx = c.getColumnIndex("title")
                val clientIdx = c.getColumnIndex("client_name")
                val deadlineIdx = c.getColumnIndex("deadline")
                while (c.moveToNext()) {
                    val oTitle = if (titleIdx != -1) c.getString(titleIdx) ?: "" else ""
                    val oClient = if (clientIdx != -1) c.getString(clientIdx) ?: "" else ""
                    val oDeadline = if (deadlineIdx != -1) c.getString(deadlineIdx) ?: "" else ""
                    if (oTitle.isNotBlank()) {
                        activeWorkOrders.add(Triple(oTitle, oClient, oDeadline))
                    }
                }
            }
        } catch (_: Exception) {}

        // 5. Bills & Dues (Recurring Subscriptions + Unpaid Dues/Debts)
        val billsAndDuesList = mutableListOf<String>()
        if (SmartAutomationManager.isSubscriptionAlertsEnabled(context)) {
            try {
                val subs = db.getSubscriptions(profileId)
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val dueSubs = subs.filter { sub ->
                    val sStart = sub.startDay.coerceIn(1, maxDays)
                    val sEnd = sub.endDay.coerceIn(sStart, maxDays)
                    if (sub.isRange) currentDay in sStart..sEnd else currentDay == sub.dueDay.coerceIn(1, maxDays)
                }
                for (sub in dueSubs) {
                    billsAndDuesList.add("Sub: ${sub.title} • ৳${sub.amount.toInt()}")
                }
            } catch (_: Exception) {}

            try {
                val dues = db.getAllDuesDebts(profileId).filter {
                    it.status.equals("UNPAID", ignoreCase = true) &&
                        it.dueDate.isNotBlank() && it.dueDate <= targetDateStr
                }
                for (due in dues) {
                    val label = if (due.type.equals("BORROWED", ignoreCase = true)) {
                        "Debt to ${due.personName}"
                    } else {
                        "Due from ${due.personName}"
                    }
                    billsAndDuesList.add("$label • ৳${due.amount.toInt()} (Due: ${due.dueDate})")
                }
            } catch (_: Exception) {}
        }

        val title = if (isEveningReview) {
            "Tomorrow's Briefing ($targetShortDate)"
        } else {
            "Today's Briefing ($targetShortDate)"
        }

        val totalItems = classes.size + examsList.size + pendingTasks.size + activeWorkOrders.size + billsAndDuesList.size

        // Build dynamic summary & body: omit zero-count sections entirely!
        val collapsedSummary: String
        val fullBodyText: String
        val bigSummaryLine: String

        if (totalItems == 0) {
            collapsedSummary = "All schedules, exams, tasks, and dues are clear!"
            fullBodyText = "All schedules, exams, tasks, and dues are clear!"
            bigSummaryLine = "Everything clear"
        } else {
            val summaryParts = mutableListOf<String>()
            if (classes.isNotEmpty()) summaryParts.add("${classes.size} Class${if (classes.size > 1) "es" else ""}")
            if (examsList.isNotEmpty()) summaryParts.add("${examsList.size} Exam${if (examsList.size > 1) "s" else ""}")
            if (pendingTasks.isNotEmpty()) summaryParts.add("${pendingTasks.size} Task${if (pendingTasks.size > 1) "s" else ""}")
            if (activeWorkOrders.isNotEmpty()) summaryParts.add("${activeWorkOrders.size} Order${if (activeWorkOrders.size > 1) "s" else ""}")
            if (billsAndDuesList.isNotEmpty()) summaryParts.add("${billsAndDuesList.size} Bill/Due${if (billsAndDuesList.size > 1) "s" else ""}")

            bigSummaryLine = summaryParts.joinToString(" • ")

            collapsedSummary = if (classes.isNotEmpty()) {
                val first = classes.first()
                val roomStr = if (first.roomNo.isNotBlank()) " (R:${first.roomNo})" else ""
                "${first.startTime} ${first.subjectName}$roomStr • $bigSummaryLine"
            } else {
                bigSummaryLine
            }

            val sections = mutableListOf<String>()

            if (classes.isNotEmpty()) {
                val sb = StringBuilder("CLASSES (${classes.size}):\n")
                classes.forEach { cls ->
                    val tag = if (cls.type.contains("PRACTICAL", ignoreCase = true)) "[LAB]" else "[LEC]"
                    val room = if (cls.roomNo.isNotBlank()) " (R:${cls.roomNo})" else ""
                    sb.append("• ${cls.startTime} - ${cls.subjectName} $tag$room\n")
                }
                sections.add(sb.toString().trimEnd())
            }

            if (examsList.isNotEmpty()) {
                val sb = StringBuilder("EXAMS (${examsList.size}):\n")
                examsList.forEach { ex ->
                    val room = if (ex.room.isNotBlank()) " (R:${ex.room})" else ""
                    val timeStr = if (ex.time.isNotBlank()) "${ex.time} - " else ""
                    sb.append("• $timeStr${ex.title}$room\n")
                }
                sections.add(sb.toString().trimEnd())
            }

            if (pendingTasks.isNotEmpty()) {
                val sb = StringBuilder("TASKS & GOALS (${pendingTasks.size}):\n")
                pendingTasks.forEach { (taskTitle, taskType, taskPriority) ->
                    val priorityTag = if (taskPriority.equals("HIGH", ignoreCase = true)) " [HIGH]" else ""
                    val typeTag = if (taskType.equals("GOAL", ignoreCase = true)) "[GOAL] " else ""
                    sb.append("• $typeTag$taskTitle$priorityTag\n")
                }
                sections.add(sb.toString().trimEnd())
            }

            if (activeWorkOrders.isNotEmpty()) {
                val sb = StringBuilder("WORK PORTAL (${activeWorkOrders.size}):\n")
                activeWorkOrders.forEach { (orderTitle, clientName, deadline) ->
                    val clientStr = if (clientName.isNotBlank()) " ($clientName)" else ""
                    val deadlineStr = if (deadline.isNotBlank()) " • Due: $deadline" else ""
                    sb.append("• $orderTitle$clientStr$deadlineStr\n")
                }
                sections.add(sb.toString().trimEnd())
            }

            if (billsAndDuesList.isNotEmpty()) {
                val sb = StringBuilder("BILLS & DUES (${billsAndDuesList.size}):\n")
                billsAndDuesList.forEach { item ->
                    sb.append("• $item\n")
                }
                sections.add(sb.toString().trimEnd())
            }

            fullBodyText = sections.joinToString("\n\n")
        }

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(title)
            .setSummaryText(bigSummaryLine)
            .bigText(fullBodyText)

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
            .setColor(Color.parseColor("#38BDF8"))
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
