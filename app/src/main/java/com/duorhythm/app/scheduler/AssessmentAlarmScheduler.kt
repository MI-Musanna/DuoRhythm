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
        if (assessment.status == "DONE" || assessment.date.isBlank()) return
        val examStartMs = parseExamStartMs(assessment.date, assessment.time) ?: return
        val (title, body) = buildAssessmentNotification(assessment)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val isMajor = assessment.type.contains("Final", ignoreCase = true) ||
                assessment.type.contains("Midterm", ignoreCase = true)

        if (isMajor) {
            // 24 hours prior (or previous day evening)
            val earlyTrigger = examStartMs - (24 * 60 * 60 * 1000L)
            if (earlyTrigger > System.currentTimeMillis()) {
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

            // 1 hour before exam start
            val finalTrigger = examStartMs - (60 * 60 * 1000L)
            if (finalTrigger > System.currentTimeMillis()) {
                val finalTitle = "Starting in 1 hour: ${assessment.title} (${assessment.subjectName})"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 2).toInt(),
                    finalTrigger, assessment.id, finalTitle, body
                )
            }
        } else {
            // Quizzes & Class Tests: 2 hours before
            val earlyTrigger = examStartMs - (2 * 60 * 60 * 1000L)
            if (earlyTrigger > System.currentTimeMillis()) {
                val earlyTitle = "Upcoming Quiz in 2h: ${assessment.subjectName}"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 1).toInt(),
                    earlyTrigger, assessment.id, earlyTitle, body
                )
            }

            // 15 minutes before exam start
            val finalTrigger = examStartMs - (15 * 60 * 1000L)
            if (finalTrigger > System.currentTimeMillis()) {
                val finalTitle = "Quiz Starting in 15m: ${assessment.title} (${assessment.subjectName})"
                dispatchAlarm(
                    context, alarmManager, (assessment.id * 10 + 2).toInt(),
                    finalTrigger, assessment.id, finalTitle, body
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
