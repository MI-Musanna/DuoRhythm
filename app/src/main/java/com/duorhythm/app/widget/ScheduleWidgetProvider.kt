package com.duorhythm.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.entity.AssessmentEntity
import com.duorhythm.app.ui.main.MainActivity
import com.duorhythm.app.util.ProfileManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, ScheduleWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                for (widgetId in allWidgetIds) {
                    updateWidget(context, appWidgetManager, widgetId)
                }
            } catch (_: Exception) {}

            try {
                AcademicHubWidget.pushUpdate(context)
            } catch (_: Exception) {}
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_schedule_dashboard)
                val db = DatabaseHelper.getInstance(context)
                val profileId = ProfileManager.getActiveProfileId(context)

                // Click Intent to open app
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    widgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.layoutWidgetRoot, pendingIntent)

                val cal = Calendar.getInstance()
                val todaySdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val todayStr = todaySdf.format(Date())

                // ==========================================
                // 1. LEFT COLUMN: TODAY'S CLASSES
                // ==========================================
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

                val todayClasses = db.getClassesForIsoDay(profileId, isoDay)
                val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date()).uppercase()
                views.setTextViewText(R.id.tvWidgetDayHeader, "TODAY • $dayName")

                if (todayClasses.isNotEmpty()) {
                    val class1 = todayClasses[0]
                    views.setViewVisibility(R.id.cardWidgetClass1, View.VISIBLE)
                    views.setTextViewText(R.id.tvClass1Badge, "NEXT UP")
                    views.setTextViewText(R.id.tvClass1Title, class1.subjectName)
                    views.setTextViewText(R.id.tvClass1Time, "${class1.startTime} - ${class1.endTime}")
                    views.setTextViewText(R.id.tvClass1Room, "R:${class1.roomNo}")

                    if (todayClasses.size > 1) {
                        val class2 = todayClasses[1]
                        views.setViewVisibility(R.id.cardWidgetClass2, View.VISIBLE)
                        views.setTextViewText(R.id.tvClass2Badge, "UPCOMING")
                        views.setTextViewText(R.id.tvClass2Title, class2.subjectName)
                        views.setTextViewText(R.id.tvClass2Time, "${class2.startTime} - ${class2.endTime}")
                        views.setTextViewText(R.id.tvClass2Room, "R:${class2.roomNo}")
                    } else {
                        views.setViewVisibility(R.id.cardWidgetClass2, View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.cardWidgetClass1, View.GONE)
                    views.setViewVisibility(R.id.cardWidgetClass2, View.GONE)
                }

                // ==========================================
                // 2. RIGHT COLUMN: UPCOMING EXAMS (FILTERED & SORTED)
                // ==========================================
                val examList = db.getUpcomingExamsForWidget(profileId)

                if (examList.isNotEmpty()) {
                    // Exam Slot 1
                    val exam1 = examList[0]
                    views.setViewVisibility(R.id.cardWidgetExam1, View.VISIBLE)
                    views.setTextViewText(R.id.tvExam1Title, exam1.title)
                    bindExamBadge(views, R.id.tvExam1Badge, R.id.tvExam1Room, exam1, todayStr)

                    // Exam Slot 2
                    if (examList.size > 1) {
                        val exam2 = examList[1]
                        views.setViewVisibility(R.id.cardWidgetExam2, View.VISIBLE)
                        views.setTextViewText(R.id.tvExam2Title, exam2.title)
                        bindExamBadge(views, R.id.tvExam2Badge, R.id.tvExam2Room, exam2, todayStr)
                    } else {
                        views.setViewVisibility(R.id.cardWidgetExam2, View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.cardWidgetExam1, View.GONE)
                    views.setViewVisibility(R.id.cardWidgetExam2, View.GONE)
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun bindExamBadge(views: RemoteViews, badgeViewId: Int, roomViewId: Int, exam: AssessmentEntity, todayStr: String) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val examDate = try { sdf.parse(exam.dueDate) } catch (_: Exception) { null }
            val todayDate = try { sdf.parse(todayStr) } catch (_: Exception) { null }

            val diffDays = if (examDate != null && todayDate != null) {
                TimeUnit.DAYS.convert(examDate.time - todayDate.time, TimeUnit.MILLISECONDS)
            } else {
                0L
            }

            val roomText = if (exam.room.isNotBlank()) "R:${exam.room}" else ""
            views.setTextViewText(roomViewId, roomText)

            when {
                diffDays == 0L -> {
                    // TODAY'S EXAM!
                    val timeSub = if (exam.dueTime.isNotBlank()) " • ${exam.dueTime}" else ""
                    views.setTextViewText(badgeViewId, "TODAY$timeSub")
                    views.setTextColor(badgeViewId, Color.parseColor("#00E5FF"))
                }
                diffDays == 1L -> {
                    views.setTextViewText(badgeViewId, "TOMORROW")
                    views.setTextColor(badgeViewId, Color.parseColor("#F59E0B"))
                }
                diffDays > 1L -> {
                    views.setTextViewText(badgeViewId, "IN $diffDays DAYS")
                    views.setTextColor(badgeViewId, Color.parseColor("#F59E0B"))
                }
                else -> {
                    // Overdue past date
                    views.setTextViewText(badgeViewId, "OVERDUE")
                    views.setTextColor(badgeViewId, Color.parseColor("#EF4444"))
                }
            }
        }
    }
}
