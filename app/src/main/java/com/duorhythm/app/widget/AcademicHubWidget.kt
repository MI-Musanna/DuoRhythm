package com.duorhythm.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.ui.main.MainActivity
import com.duorhythm.app.util.ProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AcademicHubWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MANUAL_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, AcademicHubWidget::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            for (id in allWidgetIds) {
                val loadingViews = RemoteViews(context.packageName, R.layout.widget_academic_hub)
                loadingViews.setViewVisibility(R.id.ivWidgetRefreshIcon, View.GONE)
                loadingViews.setViewVisibility(R.id.pbWidgetLoading, View.VISIBLE)
                appWidgetManager.partiallyUpdateAppWidget(id, loadingViews)
            }

            CoroutineScope(Dispatchers.IO).launch {
                delay(400)
                withContext(Dispatchers.Main) {
                    pushUpdate(context)
                }
            }
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
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_academic_hub)
                val db = DatabaseHelper.getInstance(context)
                val profileId = ProfileManager.getActiveProfileId(context)

                val prefs = context.getSharedPreferences("widget_academic_prefs", Context.MODE_PRIVATE)
                val transitionHour = prefs.getInt("transition_hour", 17)
                val showRoom = prefs.getBoolean("show_room", true)
                val compactMode = prefs.getBoolean("compact_mode", false)
                val isTransparentBg = prefs.getBoolean("transparent_bg", false)

                if (isTransparentBg) {
                    views.setInt(R.id.layoutWidgetRoot, "setBackgroundResource", android.R.color.transparent)
                } else {
                    views.setInt(R.id.layoutWidgetRoot, "setBackgroundResource", R.drawable.bg_widget_glass_oled)
                }

                views.setViewVisibility(R.id.pbWidgetLoading, View.GONE)
                views.setViewVisibility(R.id.ivWidgetRefreshIcon, View.VISIBLE)

                val refreshIntent = Intent(context, AcademicHubWidget::class.java).apply {
                    action = ACTION_MANUAL_REFRESH
                }
                val piRefresh = PendingIntent.getBroadcast(
                    context, 501, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btnWidgetRefreshHitbox, piRefresh)

                // System Alarm Launch PendingIntent
                val alarmIntent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val piAlarm = PendingIntent.getActivity(
                    context, 601, alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.layoutClockTouchpad, piAlarm)

                val cal = Calendar.getInstance()
                val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                val isEveningTransition = currentHour >= transitionHour
                val nowTimeMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

                fun parseTimeToMinutes(timeStr: String): Int {
                    return try {
                        val clean = timeStr.trim().uppercase()
                        val isPm = clean.endsWith("PM")
                        val isAm = clean.endsWith("AM")
                        val parts = clean.replace("AM", "").replace("PM", "").trim().split(":")
                        var h = parts[0].toInt()
                        val m = if (parts.size > 1) parts[1].toInt() else 0
                        if (isPm && h < 12) h += 12
                        if (isAm && h == 12) h = 0
                        h * 60 + m
                    } catch (e: Exception) {
                        -1
                    }
                }

                val evalCal = if (isEveningTransition) {
                    (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                } else {
                    cal
                }

                val evalDayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(evalCal.time)
                val evalDayOfWeek = evalCal.get(Calendar.DAY_OF_WEEK)

                var classList = db.getClassesForDay(profileId, evalDayOfWeek, evalDayName)
                var displayDayLabel = if (isEveningTransition) "TOMORROW • ${evalDayName.uppercase()}" else "TODAY • ${evalDayName.uppercase()}"

                if (classList.isEmpty()) {
                    val searchCal = evalCal.clone() as Calendar
                    for (i in 1..6) {
                        searchCal.add(Calendar.DAY_OF_YEAR, 1)
                        val nextName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(searchCal.time)
                        val nextDayOfWeek = searchCal.get(Calendar.DAY_OF_WEEK)
                        val candidateClasses = db.getClassesForDay(profileId, nextDayOfWeek, nextName)
                        if (candidateClasses.isNotEmpty()) {
                            classList = candidateClasses
                            displayDayLabel = "NEXT: ${nextName.uppercase()}"
                            break
                        }
                    }
                }

                val allExams = db.getAssessments(profileId)
                    .filter { it.status.uppercase() != "COMPLETED" }
                    .sortedBy { it.dueDate + " " + it.dueTime }

                val hasClasses = classList.isNotEmpty()
                val hasExams = allExams.isNotEmpty()

                // Adaptive Grid
                if (!hasExams && hasClasses) {
                    views.setViewVisibility(R.id.layoutExamSection, View.GONE)
                    views.setViewVisibility(R.id.vSectionDivider, View.GONE)
                    views.setViewVisibility(R.id.layoutScheduleSection, View.VISIBLE)
                } else if (!hasClasses && hasExams) {
                    views.setViewVisibility(R.id.layoutScheduleSection, View.GONE)
                    views.setViewVisibility(R.id.vSectionDivider, View.GONE)
                    views.setViewVisibility(R.id.layoutExamSection, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.layoutScheduleSection, View.VISIBLE)
                    views.setViewVisibility(R.id.vSectionDivider, View.VISIBLE)
                    views.setViewVisibility(R.id.layoutExamSection, View.VISIBLE)
                }

                // Bind Classes
                views.setTextViewText(R.id.tvClassHeader, displayDayLabel)
                if (hasClasses) {
                    views.setViewVisibility(R.id.tvNoClasses, View.GONE)

                    // Class 1
                    val c1 = classList[0]
                    views.setViewVisibility(R.id.rowClass1, View.VISIBLE)
                    views.setTextViewText(R.id.tvClass1Title, c1.subjectName)
                    views.setTextViewText(R.id.tvClass1Time, "${c1.startTime} - ${c1.endTime}")
                    views.setViewVisibility(R.id.tvClass1Room, if (showRoom && c1.room.isNotBlank()) View.VISIBLE else View.GONE)
                    views.setTextViewText(R.id.tvClass1Room, "R:${c1.room}")

                    val c1Start = parseTimeToMinutes(c1.startTime)
                    val c1End = parseTimeToMinutes(c1.endTime)
                    val isC1InProgress = !isEveningTransition && c1Start != -1 && c1End != -1 && nowTimeMinutes in c1Start..c1End
                    val isC1Next = if (!isEveningTransition && c1End != -1 && nowTimeMinutes < c1Start) true else (!isEveningTransition && c1Start == -1)

                    // Apply Active Highlighter or Standard Frame
                    if (isC1InProgress) {
                        views.setInt(R.id.rowClass1, "setBackgroundResource", R.drawable.bg_widget_item_class_active)
                        views.setTextViewText(R.id.tvClass1Badge, "● IN PROGRESS")
                        views.setTextColor(R.id.tvClass1Badge, android.graphics.Color.parseColor("#00E5FF"))
                    } else {
                        views.setInt(R.id.rowClass1, "setBackgroundResource", R.drawable.bg_widget_item_class)
                        views.setTextViewText(R.id.tvClass1Badge, if (isC1Next) "NEXT UP" else "SCHEDULED")
                        views.setTextColor(R.id.tvClass1Badge, if (isC1Next) android.graphics.Color.parseColor("#00E5FF") else android.graphics.Color.parseColor("#94A3B8"))
                    }

                    // Class 2
                    if (classList.size > 1 && !compactMode) {
                        val c2 = classList[1]
                        views.setViewVisibility(R.id.rowClass2, View.VISIBLE)
                        views.setTextViewText(R.id.tvClass2Title, c2.subjectName)
                        views.setTextViewText(R.id.tvClass2Time, "${c2.startTime} - ${c2.endTime}")
                        views.setViewVisibility(R.id.tvClass2Room, if (showRoom && c2.room.isNotBlank()) View.VISIBLE else View.GONE)
                        views.setTextViewText(R.id.tvClass2Room, "R:${c2.room}")

                        val c2Start = parseTimeToMinutes(c2.startTime)
                        val c2End = parseTimeToMinutes(c2.endTime)
                        val isC2InProgress = !isEveningTransition && c2Start != -1 && c2End != -1 && nowTimeMinutes in c2Start..c2End
                        val isC2Next = !isC1InProgress && !isC1Next && c2End != -1 && nowTimeMinutes < c2Start

                        if (isC2InProgress) {
                            views.setInt(R.id.rowClass2, "setBackgroundResource", R.drawable.bg_widget_item_class_active)
                            views.setTextViewText(R.id.tvClass2Badge, "● IN PROGRESS")
                            views.setTextColor(R.id.tvClass2Badge, android.graphics.Color.parseColor("#00E5FF"))
                        } else {
                            views.setInt(R.id.rowClass2, "setBackgroundResource", R.drawable.bg_widget_item_class)
                            views.setTextViewText(R.id.tvClass2Badge, if (isC2Next) "NEXT UP" else "UPCOMING")
                            views.setTextColor(R.id.tvClass2Badge, if (isC2Next) android.graphics.Color.parseColor("#00E5FF") else android.graphics.Color.parseColor("#94A3B8"))
                        }
                    } else {
                        views.setViewVisibility(R.id.rowClass2, View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.rowClass1, View.GONE)
                    views.setViewVisibility(R.id.rowClass2, View.GONE)
                    views.setViewVisibility(R.id.tvNoClasses, View.VISIBLE)
                }

                // Bind Exams
                if (hasExams) {
                    views.setViewVisibility(R.id.tvNoExams, View.GONE)
                    val e1 = allExams[0]
                    views.setViewVisibility(R.id.rowExam1, View.VISIBLE)
                    views.setTextViewText(R.id.tvExam1Title, e1.title)
                    views.setTextViewText(R.id.tvExam1Badge, getCountdownBadge(e1.dueDate))
                    views.setTextViewText(R.id.tvExam1Time, e1.dueTime)
                    views.setViewVisibility(R.id.tvExam1Room, if (showRoom && !e1.room.isNullOrBlank()) View.VISIBLE else View.GONE)
                    views.setTextViewText(R.id.tvExam1Room, "R:${e1.room}")

                    if (allExams.size > 1 && !compactMode) {
                        val e2 = allExams[1]
                        views.setViewVisibility(R.id.rowExam2, View.VISIBLE)
                        views.setTextViewText(R.id.tvExam2Title, e2.title)
                        views.setTextViewText(R.id.tvExam2Badge, getCountdownBadge(e2.dueDate))
                        views.setTextViewText(R.id.tvExam2Time, e2.dueTime)
                        views.setViewVisibility(R.id.tvExam2Room, if (showRoom && !e2.room.isNullOrBlank()) View.VISIBLE else View.GONE)
                        views.setTextViewText(R.id.tvExam2Room, "R:${e2.room}")
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
            } catch (e: Exception) {
                Log.e("DUO_WIDGET", "Widget update error", e)
            }
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
            val intent = Intent(context, AcademicHubWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, AcademicHubWidget::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
