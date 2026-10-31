package com.duorhythm.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.ui.main.MainActivity
import com.duorhythm.app.util.ProfileManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AcademicHubWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MANUAL_REFRESH) {
            pushUpdate(context)
            Toast.makeText(context, "Academic Hub refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        const val ACTION_MANUAL_REFRESH = "com.duorhythm.app.widget.ACTION_MANUAL_REFRESH"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_academic_hub)
            val db = DatabaseHelper.getInstance(context)
            val profileId = ProfileManager.getActiveProfileId(context)

            val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
            views.setTextViewText(R.id.tvWidgetSyncTime, timeFormat.format(Date()))

            // Wire Quick Refresh Button
            val refreshIntent = Intent(context, AcademicHubWidget::class.java).apply {
                action = ACTION_MANUAL_REFRESH
            }
            val piRefresh = PendingIntent.getBroadcast(
                context,
                301,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetRefresh, piRefresh)

            // Resolve Classes
            val daySequence = listOf("Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday")
            val cal = Calendar.getInstance()
            val todayDayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)

            var targetDayName = todayDayName
            var classList = db.getClassesByDayName(profileId, todayDayName)

            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            if (classList.isEmpty() || currentHour >= 17) {
                val todayIndex = daySequence.indexOfFirst { it.equals(todayDayName, ignoreCase = true) }
                for (i in 1..6) {
                    val nextIndex = (if (todayIndex == -1) 0 else todayIndex + i) % daySequence.size
                    val candidateDay = daySequence[nextIndex]
                    val candidateClasses = db.getClassesByDayName(profileId, candidateDay)
                    if (candidateClasses.isNotEmpty()) {
                        targetDayName = candidateDay
                        classList = candidateClasses
                        break
                    }
                }
            }

            val isTomorrow = !targetDayName.equals(todayDayName, ignoreCase = true)
            val dayPrefix = if (isTomorrow) "NEXT: ${targetDayName.uppercase()}" else "TODAY (${targetDayName.uppercase()})"
            views.setTextViewText(R.id.tvNextClassHeader, dayPrefix)

            if (classList.isNotEmpty()) {
                views.setViewVisibility(R.id.tvNoClasses, View.GONE)
                val c1 = classList[0]
                views.setViewVisibility(R.id.rowClass1, View.VISIBLE)
                views.setTextViewText(R.id.tvClass1Title, c1.subjectName)
                views.setTextViewText(R.id.tvClass1Details, "${c1.startTime} - ${c1.endTime} • R:${c1.room}")

                if (classList.size > 1) {
                    val c2 = classList[1]
                    views.setViewVisibility(R.id.rowClass2, View.VISIBLE)
                    views.setTextViewText(R.id.tvClass2Title, c2.subjectName)
                    views.setTextViewText(R.id.tvClass2Details, "${c2.startTime} - ${c2.endTime} • R:${c2.room}")
                } else {
                    views.setViewVisibility(R.id.rowClass2, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.rowClass1, View.GONE)
                views.setViewVisibility(R.id.rowClass2, View.GONE)
                views.setViewVisibility(R.id.tvNoClasses, View.VISIBLE)
            }

            // Resolve Exams
            val allExams = db.getAssessments(profileId)
                .filter { it.status.uppercase() != "COMPLETED" }
                .sortedBy { it.dueDate + " " + it.dueTime }

            if (allExams.isNotEmpty()) {
                views.setViewVisibility(R.id.tvNoExams, View.GONE)
                val e1 = allExams[0]
                views.setViewVisibility(R.id.rowExam1, View.VISIBLE)
                views.setTextViewText(R.id.tvExam1Title, e1.title)
                views.setTextViewText(R.id.tvExam1Badge, getCountdownBadge(e1.dueDate))
                val room1 = if (e1.room.isNotBlank()) e1.room else "TBA"
                views.setTextViewText(R.id.tvExam1Details, "${e1.dueTime} • Room: $room1")

                if (allExams.size > 1) {
                    val e2 = allExams[1]
                    views.setViewVisibility(R.id.rowExam2, View.VISIBLE)
                    views.setTextViewText(R.id.tvExam2Title, e2.title)
                    views.setTextViewText(R.id.tvExam2Badge, getCountdownBadge(e2.dueDate))
                    val room2 = if (e2.room.isNotBlank()) e2.room else "TBA"
                    views.setTextViewText(R.id.tvExam2Details, "${e2.dueTime} • Room: $room2")
                } else {
                    views.setViewVisibility(R.id.rowExam2, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.rowExam1, View.GONE)
                views.setViewVisibility(R.id.rowExam2, View.GONE)
                views.setViewVisibility(R.id.tvNoExams, View.VISIBLE)
            }

            // Deep Links
            val scheduleIntent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_OPEN_TAB"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_DEEP_LINK_DESTINATION", "DESTINATION_SCHEDULE")
            }
            val piSchedule = PendingIntent.getActivity(
                context, 201, scheduleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.layoutScheduleSection, piSchedule)

            val examIntent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_OPEN_TAB"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_DEEP_LINK_DESTINATION", "DESTINATION_PORTAL_EXAMS")
            }
            val piExam = PendingIntent.getActivity(
                context, 202, examIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.layoutExamSection, piExam)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getCountdownBadge(dueDateStr: String): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dueDate = sdf.parse(dueDateStr) ?: return ""
                val today = sdf.parse(sdf.format(Date())) ?: return ""
                val diff = dueDate.time - today.time
                val days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
                when {
                    days < 0 -> "OVERDUE"
                    days == 0L -> "TODAY"
                    days == 1L -> "TOMORROW"
                    else -> "IN $days DAYS"
                }
            } catch (e: Exception) {
                ""
            }
        }

        fun pushUpdate(context: Context) {
            try {
                val intent = Intent(context, AcademicHubWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                }
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, AcademicHubWidget::class.java)
                )
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
