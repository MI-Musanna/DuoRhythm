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
        if (assessment.status == "DONE" || assessment.date.isBlank()) return
        val examStartMs = parseExamStartMs(assessment.date, assessment.time) ?: return
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
                // Short-window fallback (< 1h before start): 1m prior or at exact start
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
                // Short-window fallback (< 20m before start): 1m prior or at exact start
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

            // Resync exams from subject_work_items
            db.getAllExams(profileId = activeProfileId).forEach { pair ->
                val entity = AssessmentEntity.fromSubjectWorkItem(
                    pair.first,
                    pair.second.name,
                    pair.second.code,
                    pair.second.teacher
                )
                scheduleAssessmentAlarms(context, entity)
            }

            // Resync assessments from assessments table
            db.getUpcomingExamsForWidget(activeProfileId).forEach { widgetEx ->
                val entity = AssessmentEntity.fromLocalEntity(widgetEx)
                scheduleAssessmentAlarms(context, entity)
            }
        } catch (_: Exception) {}
    }

    private fun parseExamStartMs(dateStr: String, timeStr: String): Long? {
        val cleanDate = dateStr.trim()
        val cleanTime = timeStr.trim().ifBlank { "09:00 AM" }
        val dateFormats = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy")
        val timeFormats = listOf("hh:mm a", "h:mm a", "hh:mma", "h:mma", "HH:mm", "H:mm")

        for (df in dateFormats) {
            for (tf in timeFormats) {
                try {
                    val sdf = SimpleDateFormat("$df $tf", Locale.US)
                    val date = sdf.parse("$cleanDate $cleanTime")
                    if (date != null) return date.time
                } catch (_: Exception) {}
            }
        }

        for (df in dateFormats) {
            try {
                val sdf = SimpleDateFormat(df, Locale.US)
                val date = sdf.parse(cleanDate)
                if (date != null) {
                    val cal = Calendar.getInstance().apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
        }

        return null
    }
}
