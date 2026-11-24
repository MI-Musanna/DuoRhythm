package com.duorhythm.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.receiver.AssessmentAlarmReceiver
import database.entities.AssessmentEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AssessmentAlarmScheduler {

    const val KEY_DEFAULT_EXAM_HOUR = "pref_default_exam_hour"
    const val KEY_DEFAULT_EXAM_MINUTE = "pref_default_exam_minute"
    const val KEY_EXAM_REMINDER_LEAD_MINUTES = "exam_reminder_lead_minutes"
    const val DEFAULT_EXAM_REMINDER_LEAD_MINUTES = 60 // 1H

    fun getExamReminderLeadMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        return prefs.getInt(KEY_EXAM_REMINDER_LEAD_MINUTES, DEFAULT_EXAM_REMINDER_LEAD_MINUTES)
    }

    fun setExamReminderLeadMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_EXAM_REMINDER_LEAD_MINUTES, minutes)
            .apply()
        try {
            context.getSharedPreferences("duorhythm_backup_prefs", Context.MODE_PRIVATE)
                .edit().putInt(KEY_EXAM_REMINDER_LEAD_MINUTES, minutes).apply()
        } catch (_: Exception) {}
        rescheduleAll(context)
    }

    fun formatExamLeadTimeLabel(minutes: Int): String {
        return when (minutes) {
            20 -> "20m"
            60 -> "1h"
            120 -> "2h"
            -1 -> "OFF"
            else -> if (minutes <= 0) "OFF" else if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60}h" else "${minutes}m"
        }
    }

    /**
     * Returns configured (hour24, minute) for exams saved without a specific time,
     * or null if the user explicitly turned off alerts for untimed exams (hour < 0).
     */
    fun getDefaultExamReminderTime(context: Context): Pair<Int, Int>? {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        val hour = prefs.getInt(KEY_DEFAULT_EXAM_HOUR, 9)
        if (hour < 0) return null
        val minute = prefs.getInt(KEY_DEFAULT_EXAM_MINUTE, 0).coerceIn(0, 59)
        return Pair(hour.coerceIn(0, 23), minute)
    }

    fun setDefaultExamReminderTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DEFAULT_EXAM_HOUR, hour)
            .putInt(KEY_DEFAULT_EXAM_MINUTE, minute.coerceIn(0, 59))
            .apply()
        resyncAllAssessments(context)
    }

    fun formatDefaultExamTimeLabel(context: Context): String {
        val pair = getDefaultExamReminderTime(context) ?: return "OFF"
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, pair.first)
            set(Calendar.MINUTE, pair.second)
        }
        return SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
    }

    fun buildAssessmentNotification(assessment: AssessmentEntity): Pair<String, String> {
        val typeName = assessment.type
        val title = when {
            typeName.contains("Final", ignoreCase = true) -> "Semester Final Tomorrow: ${assessment.subjectName}"
            typeName.contains("Midterm", ignoreCase = true) -> "Midterm Exam Alert: ${assessment.subjectName}"
            typeName.contains("Quiz", ignoreCase = true) || typeName.contains("CT", ignoreCase = true) -> "Upcoming Quiz: ${assessment.subjectName}"
            else -> "Upcoming Assessment: ${assessment.subjectName}"
        }
        val roomText = if (assessment.room.isNotBlank()) "Room: ${assessment.room} | " else ""
        val timeText = if (assessment.time.isNotBlank()) "Time: ${assessment.time} | " else ""
        val syllabusSnippet = if (assessment.syllabus.isNotBlank()) "Syllabus: ${assessment.syllabus.take(40)}..." else "Review assigned material."
        val body = "$timeText$roomText$syllabusSnippet"
        return Pair(title, body)
    }

    fun scheduleAssessmentAlarms(context: Context, assessment: AssessmentEntity) {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        val examRemindersEnabled = prefs.getBoolean("pref_exam_reminders_enabled", true)
        if (!examRemindersEnabled) {
            cancelAlarmsForAssessment(context, assessment.id)
            return
        }
        if (assessment.status == "DONE" || assessment.date.isBlank()) {
            cancelAlarmsForAssessment(context, assessment.id)
            return
        }

        // Cleanly cancel any previous alarms for this assessment before scheduling
        cancelAlarmsForAssessment(context, assessment.id)

        val isTimed = assessment.time.isNotBlank()
        val examStartMs = parseExamStartMs(context, assessment.date, assessment.time)
        if (examStartMs == null) {
            return
        }
        val now = System.currentTimeMillis()
        if (examStartMs <= now) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val category = when {
            assessment.type.contains("Final", ignoreCase = true) -> "Final Exam"
            assessment.type.contains("Midterm", ignoreCase = true) -> "Midterm Exam"
            assessment.type.contains("Quiz", ignoreCase = true) || assessment.type.contains("CT", ignoreCase = true) -> "Quiz"
            else -> "Assessment"
        }
        val roomText = if (assessment.room.isNotBlank()) "Room: ${assessment.room} | " else ""
        val timeText = if (assessment.time.isNotBlank()) "Time: ${assessment.time} | " else ""
        val syllabusSnippet = if (assessment.syllabus.isNotBlank()) "Syllabus: ${assessment.syllabus.take(40)}..." else "Review assigned material."
        val body = "$timeText$roomText$syllabusSnippet"

        if (isTimed) {
            val leadMinutes = getExamReminderLeadMinutes(context)
            if (leadMinutes == -1) {
                // OFF for timed exams: do not schedule a timed pre-exam lead alarm
                return
            }

            val leadMs = leadMinutes * 60 * 1000L
            val triggerMs = examStartMs - leadMs
            val leadLabel = formatExamLeadTimeLabel(leadMinutes)

            if (triggerMs > now) {
                val title = "Upcoming $category in $leadLabel: ${assessment.subjectName}"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 1).toInt(),
                    triggerMs, assessment.id, title, body
                )
            } else if (examStartMs > now) {
                val fallbackTrigger = if (examStartMs - 60_000L > now) examStartMs - 60_000L else examStartMs
                val fallbackTitle = "Upcoming $category Starting Soon: ${assessment.subjectName}"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 1).toInt(),
                    fallbackTrigger, assessment.id, fallbackTitle, body
                )
            }
        } else {
            // Untimed exam: parsed at Default Exam Reminder Time (if not OFF)
            if (examStartMs > now) {
                val title = "Today's $category: ${assessment.subjectName}"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 1).toInt(),
                    examStartMs, assessment.id, title, body
                )
            }
        }
    }

    private fun dispatchAlarm(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        triggerMs: Long,
        assessmentId: Long,
        title: String,
        body: String
    ) {
        val intent = Intent(context, AssessmentAlarmReceiver::class.java).apply {
            action = AssessmentAlarmReceiver.ACTION_ASSESSMENT_ALERT
            putExtra(AssessmentAlarmReceiver.EXTRA_ASSESSMENT_ID, assessmentId)
            putExtra(AssessmentAlarmReceiver.EXTRA_TITLE, title)
            putExtra(AssessmentAlarmReceiver.EXTRA_BODY, body)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ClassAlarmScheduler.setExactAlarm(alarmManager, triggerMs, pi)
    }

    fun cancelAlarmsForAssessment(context: Context, assessmentId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val codes = listOf((assessmentId * 10 + 1).toInt(), (assessmentId * 10 + 2).toInt())
        for (code in codes) {
            val intent = Intent(context, AssessmentAlarmReceiver::class.java).apply {
                action = AssessmentAlarmReceiver.ACTION_ASSESSMENT_ALERT
            }
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    fun cancelAlarmsForCourse(context: Context, courseId: Long) {
        val db = DatabaseHelper.getInstance(context)
        val items = db.getItemsForSubject(courseId)
        for (item in items) {
            cancelAlarmsForAssessment(context, item.id)
        }
    }

    fun rescheduleAll(context: Context) {
        try {
            val db = DatabaseHelper.getInstance(context)
            val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("pref_exam_reminders_enabled", true)
            val profiles = try { db.getAllProfiles() } catch (_: Exception) { emptyList() }
            val profileIds = if (profiles.isNotEmpty()) profiles.map { it.id }.distinct() else listOf(com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(context))

            for (profileId in profileIds) {
                val exams = try { db.getAllExams(profileId = profileId) } catch (_: Exception) { emptyList() }
                val widgetExams = try { db.getUpcomingExamsForWidget(profileId) } catch (_: Exception) { emptyList() }

                if (!enabled) {
                    exams.forEach { pair -> cancelAlarmsForAssessment(context, pair.first.id) }
                    widgetExams.forEach { widgetEx -> cancelAlarmsForAssessment(context, widgetEx.id) }
                } else {
                    exams.forEach { pair ->
                        val entity = AssessmentEntity.fromSubjectWorkItem(
                            pair.first,
                            pair.second.name,
                            pair.second.code,
                            pair.second.teacher
                        )
                        scheduleAssessmentAlarms(context, entity)
                    }
                    widgetExams.forEach { widgetEx ->
                        val entity = AssessmentEntity.fromLocalEntity(widgetEx)
                        scheduleAssessmentAlarms(context, entity)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun resyncAllAssessments(context: Context) {
        rescheduleAll(context)
    }

    private fun parseExamStartMs(context: Context, dateStr: String, timeStr: String): Long? {
        val cleanDate = dateStr.trim()
        val rawTime = timeStr.trim()
        val dateFormats = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy")

        if (rawTime.isNotBlank()) {
            val timeFormats = listOf("hh:mm a", "h:mm a", "hh:mma", "h:mma", "HH:mm", "H:mm")
            for (df in dateFormats) {
                for (tf in timeFormats) {
                    try {
                        val sdf = SimpleDateFormat("$df $tf", Locale.US)
                        val date = sdf.parse("$cleanDate $rawTime")
                        if (date != null) return date.time
                    } catch (_: Exception) {}
                }
            }
        }

        val defaultPair = getDefaultExamReminderTime(context) ?: return null
        for (df in dateFormats) {
            try {
                val sdf = SimpleDateFormat(df, Locale.US)
                val date = sdf.parse(cleanDate)
                if (date != null) {
                    val cal = Calendar.getInstance().apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, defaultPair.first)
                        set(Calendar.MINUTE, defaultPair.second)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
        }

        return null
    }
}
