package com.duorhythm.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.ui.main.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class ExamCountdownWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_exam_glance)
            val profileId = ProfileManager.getActiveProfileId(context)
            val db = DatabaseHelper.getInstance(context)
            val exams = db.getAssessments(profileId).filter { it.status.uppercase() != "COMPLETED" }

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val sortedExams = exams.mapNotNull { item ->
                try {
                    val examDate = sdf.parse(item.date) ?: return@mapNotNull null
                    val cal = Calendar.getInstance().apply {
                        time = examDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val diffMillis = cal.timeInMillis - today.timeInMillis
                    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
                    Triple(item, diffDays, examDate)
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.second }

            if (sortedExams.isNotEmpty()) {
                val (nearestExam, daysDiff, _) = sortedExams.first()
                val countdownText = when {
                    daysDiff < 0 -> "OVERDUE"
                    daysDiff == 0 -> "TODAY"
                    daysDiff == 1 -> "TOMORROW"
                    else -> "IN $daysDiff DAYS"
                }
                views.setTextViewText(R.id.tvWidgetExamCountdown, countdownText)
                views.setTextViewText(R.id.tvWidgetExamTitle, nearestExam.title)
                val roomText = if (nearestExam.room.isNotBlank()) " • Room ${nearestExam.room}" else ""
                val timeText = if (nearestExam.time.isNotBlank()) " at ${nearestExam.time}" else ""
                views.setTextViewText(R.id.tvWidgetExamDetails, "${nearestExam.date}$timeText$roomText • [${nearestExam.category}]")
            } else {
                views.setTextViewText(R.id.tvWidgetExamCountdown, "ALL CLEAR")
                views.setTextViewText(R.id.tvWidgetExamTitle, "No pending assessments")
                views.setTextViewText(R.id.tvWidgetExamDetails, "Tap to view exam schedule & portal")
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.layoutWidgetExamRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun pushUpdate(context: Context) {
            try {
                val intent = Intent(context, ExamCountdownWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                }
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, ExamCountdownWidget::class.java)
                )
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
