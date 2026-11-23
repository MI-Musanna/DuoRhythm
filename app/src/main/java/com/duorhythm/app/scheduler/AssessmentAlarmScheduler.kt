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
        val examStartMs = parseExamStartMs(context, assessment.date, assessment.time)
        if (examStartMs == null) {
            cancelAlarmsForAssessment(context, assessment.id)
            return
        }
        val now = System.currentTimeMillis()
        if (examStartMs <= now) return

        val (title, body) = buildAssessmentNotification(assessment)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val isMajor = assessment.type.contains("Final", ignoreCase = true) ||
                assessment.type.contains("Midterm", ignoreCase = true)

        if (isMajor) {
            // 1. 24 hours prior (or previous day)
            val earlyTrigger = examStartMs - (24 * 60 * 60 * 1000L)
            if (earlyTrigger > now) {
                val earlyTitle = if (assessment.type.contains("Final", ignoreCase = true)) {
                    "Semester Final Tomorrow: ${assessment.subjectName}"
                } else {
                    "Midterm Exam Tomorrow: ${assessment.subjectName}"
                }
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 1).toInt(),
                    earlyTrigger, assessment.id, earlyTitle, body
                )
            }

            // 2. 1 hour before exam start (with short-window fallback)
            val finalLeadMs = 60 * 60 * 1000L
            val finalTrigger = examStartMs - finalLeadMs
            if (finalTrigger > now) {
                val finalTitle = "Starting in 1 hour: ${assessment.title} (${assessment.subjectName})"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 2).toInt(),
                    finalTrigger, assessment.id, finalTitle, body
                )
            } else if (examStartMs > now) {
                val fallbackTrigger = if (examStartMs - 60_000L > now) examStartMs - 60_000L else examStartMs
                val fallbackTitle = "Exam Starting Soon: ${assessment.title} (${assessment.subjectName})"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 2).toInt(),
                    fallbackTrigger, assessment.id, fallbackTitle, body
                )
            }
        } else {
            // 1. Quizzes & Class Tests: 2 hours before
            val earlyTrigger = examStartMs - (2 * 60 * 60 * 1000L)
            if (earlyTrigger > now) {
                val earlyTitle = "Upcoming Quiz in 2h: ${assessment.subjectName}"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 1).toInt(),
                    earlyTrigger, assessment.id, earlyTitle, body
                )
            }

            // 2. Default 20 minutes before start (or lead time) with short-window fallback
            val leadMinutes = prefs.getInt("pref_exam_lead_minutes", 20).coerceIn(5, 60)
            val leadMs = leadMinutes * 60 * 1000L
            val leadTrigger = examStartMs - leadMs
            if (leadTrigger > now) {
                val finalTitle = "Quiz Starting in ${leadMinutes}m: ${assessment.title} (${assessment.subjectName})"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 2).toInt(),
                    leadTrigger, assessment.id, finalTitle, body
                )
            } else if (examStartMs > now) {
                val fallbackTrigger = if (examStartMs - 60_000L > now) examStartMs - 60_000L else examStartMs
                val fallbackTitle = "Quiz Starting Soon: ${assessment.title} (${assessment.subjectName})"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 2).toInt(),
                    fallbackTrigger, assessment.id, fallbackTitle, body
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

    fun resyncAllAssessments(context: Context) {
        try {
            val db = DatabaseHelper.getInstance(context)
            val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("pref_exam_reminders_enabled", true)
            val activeProfileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(context)

            if (!enabled) {
                db.getAllExams(profileId = activeProfileId).forEach { pair ->
                    cancelAlarmsForAssessment(context, pair.first.id)
                }
                db.getUpcomingExamsForWidget(activeProfileId).forEach { widgetEx ->
                    cancelAlarmsForAssessment(context, widgetEx.id)
                }
                return
            }

            db.getAllExams(profileId = activeProfileId).forEach { pair ->
                val entity = AssessmentEntity.fromSubjectWorkItem(
                    pair.first,
                    pair.second.name,
                    pair.second.code,
                    pair.second.teacher
                )
                scheduleAssessmentAlarms(context, entity)
            }

            db.getUpcomingExamsForWidget(activeProfileId).forEach { widgetEx ->
                val entity = AssessmentEntity.fromLocalEntity(widgetEx)
                scheduleAssessmentAlarms(context, entity)
            }
        } catch (_: Exception) {}
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
