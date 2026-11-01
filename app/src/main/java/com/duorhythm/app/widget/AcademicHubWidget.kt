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
        val prefs = context.getSharedPreferences("widget_academic_prefs", Context.MODE_PRIVATE)

        when (intent.action) {
            ACTION_PREV_TAB -> {
                val currentTab = prefs.getInt("active_tab", 0)
                val newTab = if (currentTab <= 0) 2 else currentTab - 1
                prefs.edit().putInt("active_tab", newTab).apply()
                pushUpdate(context)
            }
            ACTION_NEXT_TAB -> {
                val currentTab = prefs.getInt("active_tab", 0)
                val newTab = if (currentTab >= 2) 0 else currentTab + 1
                prefs.edit().putInt("active_tab", newTab).apply()
                pushUpdate(context)
            }
            ACTION_MANUAL_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, AcademicHubWidget::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

                for (id in allWidgetIds) {
                    val loadingViews = RemoteViews(context.packageName, R.layout.widget_academic_hub)
                    loadingViews.setViewVisibility(R.id.btnWidgetRefresh, View.GONE)
                    loadingViews.setViewVisibility(R.id.pbWidgetLoading, View.VISIBLE)
                    appWidgetManager.partiallyUpdateAppWidget(id, loadingViews)
                }

                CoroutineScope(Dispatchers.IO).launch {
                    delay(500)
                    withContext(Dispatchers.Main) {
                        pushUpdate(context)
                    }
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
        const val ACTION_PREV_TAB = "com.duorhythm.app.widget.ACTION_PREV_TAB"
        const val ACTION_NEXT_TAB = "com.duorhythm.app.widget.ACTION_NEXT_TAB"
        const val ACTION_MANUAL_REFRESH = "com.duorhythm.app.widget.ACTION_MANUAL_REFRESH"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_academic_hub)
                val db = DatabaseHelper.getInstance(context)
                val profileId = ProfileManager.getActiveProfileId(context)
                val prefs = context.getSharedPreferences("widget_academic_prefs", Context.MODE_PRIVATE)
                val activeTab = prefs.getInt("active_tab", 0) // 0: Overview, 1: Classes, 2: Exams

                // Reset Loader
                views.setViewVisibility(R.id.pbWidgetLoading, View.GONE)
                views.setViewVisibility(R.id.btnWidgetRefresh, View.VISIBLE)

                // Sync Time
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                views.setTextViewText(R.id.tvWidgetSyncTime, timeFormat.format(Date()))

                // Bind Arrow Intents
                val prevIntent = Intent(context, AcademicHubWidget::class.java).apply { action = ACTION_PREV_TAB }
                views.setOnClickPendingIntent(R.id.btnWidgetPrevTab, PendingIntent.getBroadcast(context, 401, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

                val nextIntent = Intent(context, AcademicHubWidget::class.java).apply { action = ACTION_NEXT_TAB }
                views.setOnClickPendingIntent(R.id.btnWidgetNextTab, PendingIntent.getBroadcast(context, 402, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

                val refreshIntent = Intent(context, AcademicHubWidget::class.java).apply { action = ACTION_MANUAL_REFRESH }
                views.setOnClickPendingIntent(R.id.btnWidgetRefresh, PendingIntent.getBroadcast(context, 403, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

                // Tab Header Title
                when (activeTab) {
                    0 -> views.setTextViewText(R.id.tvWidgetTabTitle, "OVERVIEW (1/3)")
                    1 -> views.setTextViewText(R.id.tvWidgetTabTitle, "CLASSES (2/3)")
                    2 -> views.setTextViewText(R.id.tvWidgetTabTitle, "EXAMS (3/3)")
                }

                // Data Retrieval
                val daySequence = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Saturday")
                val cal = Calendar.getInstance()
                val todayDayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)

                var targetDayName = todayDayName
                var classList = db.getClassesByDayName(profileId, todayDayName)

                val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                if (classList.isEmpty() || currentHour >= 18) {
                    val todayIndex = daySequence.indexOfFirst { it.equals(todayDayName, ignoreCase = true) }
                    for (i in 1..5) {
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

                val allExams = db.getAssessments(profileId)
                    .filter { it.status.uppercase() != "COMPLETED" }
                    .sortedBy { it.dueDate + " " + it.dueTime }

                val hasClasses = classList.isNotEmpty()
                val hasExams = allExams.isNotEmpty()

                // Bind TAB 0: OVERVIEW with Fluid Adaptive Grid
                views.setViewVisibility(R.id.containerTabOverview, if (activeTab == 0) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.containerTabClasses, if (activeTab == 1) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.containerTabExams, if (activeTab == 2) View.VISIBLE else View.GONE)

                if (activeTab == 0) {
                    // Fluid Adaptive Grid Logic:
                    if (!hasExams && hasClasses) {
                        // Collapse Exams entirely, Classes take 100% width
                        views.setViewVisibility(R.id.layoutOverviewExams, View.GONE)
                        views.setViewVisibility(R.id.gapOverview, View.GONE)
                        views.setViewVisibility(R.id.layoutOverviewClasses, View.VISIBLE)
                    } else if (!hasClasses && hasExams) {
                        // Collapse Classes entirely, Exams take 100% width
                        views.setViewVisibility(R.id.layoutOverviewClasses, View.GONE)
                        views.setViewVisibility(R.id.gapOverview, View.GONE)
                        views.setViewVisibility(R.id.layoutOverviewExams, View.VISIBLE)
                    } else {
                        // Both exist or both empty: Balanced 50-50 Split
                        views.setViewVisibility(R.id.layoutOverviewClasses, View.VISIBLE)
                        views.setViewVisibility(R.id.gapOverview, View.VISIBLE)
                        views.setViewVisibility(R.id.layoutOverviewExams, View.VISIBLE)
                    }

                    // Populate Split Classes
                    val isTomorrow = !targetDayName.equals(todayDayName, ignoreCase = true)
                    views.setTextViewText(R.id.tvOverviewClassHeader, if (isTomorrow) "NEXT: ${targetDayName.uppercase()}" else "TODAY (${targetDayName.uppercase()})")

                    if (hasClasses) {
                        views.setViewVisibility(R.id.tvOverviewNoClasses, View.GONE)
                        val c1 = classList[0]
                        views.setViewVisibility(R.id.tvOverviewC1Title, View.VISIBLE)
                        views.setViewVisibility(R.id.tvOverviewC1Details, View.VISIBLE)
                        views.setTextViewText(R.id.tvOverviewC1Title, c1.subjectName)
                        views.setTextViewText(R.id.tvOverviewC1Details, "${c1.startTime} - ${c1.endTime} • R:${c1.room}")

                        if (classList.size > 1) {
                            val c2 = classList[1]
                            views.setViewVisibility(R.id.tvOverviewC2Title, View.VISIBLE)
                            views.setViewVisibility(R.id.tvOverviewC2Details, View.VISIBLE)
                            views.setTextViewText(R.id.tvOverviewC2Title, c2.subjectName)
                            views.setTextViewText(R.id.tvOverviewC2Details, "${c2.startTime} - ${c2.endTime} • R:${c2.room}")
                        } else {
                            views.setViewVisibility(R.id.tvOverviewC2Title, View.GONE)
                            views.setViewVisibility(R.id.tvOverviewC2Details, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.tvOverviewC1Title, View.GONE)
                        views.setViewVisibility(R.id.tvOverviewC1Details, View.GONE)
                        views.setViewVisibility(R.id.tvOverviewC2Title, View.GONE)
                        views.setViewVisibility(R.id.tvOverviewC2Details, View.GONE)
                        views.setViewVisibility(R.id.tvOverviewNoClasses, View.VISIBLE)
                    }

                    // Populate Split Exams
                    if (hasExams) {
                        views.setViewVisibility(R.id.tvOverviewNoExams, View.GONE)
                        val e1 = allExams[0]
                        views.setViewVisibility(R.id.tvOverviewE1Title, View.VISIBLE)
                        views.setViewVisibility(R.id.tvOverviewE1Details, View.VISIBLE)
                        views.setTextViewText(R.id.tvOverviewE1Title, "${e1.title} (${getCountdownBadge(e1.dueDate)})")
                        views.setTextViewText(R.id.tvOverviewE1Details, "${e1.dueTime} • Room: ${e1.room ?: "TBA"}")

                        if (allExams.size > 1) {
                            val e2 = allExams[1]
                            views.setViewVisibility(R.id.tvOverviewE2Title, View.VISIBLE)
                            views.setViewVisibility(R.id.tvOverviewE2Details, View.VISIBLE)
                            views.setTextViewText(R.id.tvOverviewE2Title, "${e2.title} (${getCountdownBadge(e2.dueDate)})")
                            views.setTextViewText(R.id.tvOverviewE2Details, "${e2.dueTime} • Room: ${e2.room ?: "TBA"}")
                        } else {
                            views.setViewVisibility(R.id.tvOverviewE2Title, View.GONE)
                            views.setViewVisibility(R.id.tvOverviewE2Details, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.tvOverviewE1Title, View.GONE)
                        views.setViewVisibility(R.id.tvOverviewE1Details, View.GONE)
                        views.setViewVisibility(R.id.tvOverviewE2Title, View.GONE)
                        views.setViewVisibility(R.id.tvOverviewE2Details, View.GONE)
                        views.setViewVisibility(R.id.tvOverviewNoExams, View.VISIBLE)
                    }
                }

                // Bind TAB 1: FULL CLASSES VIEW
                if (activeTab == 1) {
                    views.setTextViewText(R.id.tvFullClassesDayHeader, "SCHEDULE • ${targetDayName.uppercase()}")
                    if (hasClasses) {
                        views.setViewVisibility(R.id.tvFullClassesEmpty, View.GONE)
                        val c1 = classList[0]
                        views.setViewVisibility(R.id.rowFullClass1, View.VISIBLE)
                        views.setTextViewText(R.id.tvFullC1Time, "${c1.startTime}-${c1.endTime}")
                        views.setTextViewText(R.id.tvFullC1Title, c1.subjectName)
                        views.setTextViewText(R.id.tvFullC1Room, "Room ${c1.room}")

                        if (classList.size > 1) {
                            val c2 = classList[1]
                            views.setViewVisibility(R.id.rowFullClass2, View.VISIBLE)
                            views.setTextViewText(R.id.tvFullC2Time, "${c2.startTime}-${c2.endTime}")
                            views.setTextViewText(R.id.tvFullC2Title, c2.subjectName)
                            views.setTextViewText(R.id.tvFullC2Room, "Room ${c2.room}")
                        } else {
                            views.setViewVisibility(R.id.rowFullClass2, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.rowFullClass1, View.GONE)
                        views.setViewVisibility(R.id.rowFullClass2, View.GONE)
                        views.setViewVisibility(R.id.tvFullClassesEmpty, View.VISIBLE)
                    }
                }

                // Bind TAB 2: FULL EXAMS VIEW
                if (activeTab == 2) {
                    if (hasExams) {
                        views.setViewVisibility(R.id.tvFullExamsEmpty, View.GONE)
                        val e1 = allExams[0]
                        views.setViewVisibility(R.id.rowFullExam1, View.VISIBLE)
                        views.setViewVisibility(R.id.tvFullE1Details, View.VISIBLE)
                        views.setTextViewText(R.id.tvFullE1Title, e1.title)
                        views.setTextViewText(R.id.tvFullE1Badge, getCountdownBadge(e1.dueDate))
                        views.setTextViewText(R.id.tvFullE1Details, "Date: ${e1.dueDate} • ${e1.dueTime} • Room: ${e1.room ?: "TBA"}")

                        if (allExams.size > 1) {
                            val e2 = allExams[1]
                            views.setViewVisibility(R.id.rowFullExam2, View.VISIBLE)
                            views.setViewVisibility(R.id.tvFullE2Details, View.VISIBLE)
                            views.setTextViewText(R.id.tvFullE2Title, e2.title)
                            views.setTextViewText(R.id.tvFullE2Badge, getCountdownBadge(e2.dueDate))
                            views.setTextViewText(R.id.tvFullE2Details, "Date: ${e2.dueDate} • ${e2.dueTime} • Room: ${e2.room ?: "TBA"}")
                        } else {
                            views.setViewVisibility(R.id.rowFullExam2, View.GONE)
                            views.setViewVisibility(R.id.tvFullE2Details, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.rowFullExam1, View.GONE)
                        views.setViewVisibility(R.id.tvFullE1Details, View.GONE)
                        views.setViewVisibility(R.id.rowFullExam2, View.GONE)
                        views.setViewVisibility(R.id.tvFullE2Details, View.GONE)
                        views.setViewVisibility(R.id.tvFullExamsEmpty, View.VISIBLE)
                    }
                }

                // Deep Link Navigation
                val scheduleIntent = Intent(context, MainActivity::class.java).apply {
                    action = "ACTION_OPEN_TAB"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_DEEP_LINK_DESTINATION", "DESTINATION_SCHEDULE")
                }
                val piSchedule = PendingIntent.getActivity(context, 201, scheduleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.layoutOverviewClasses, piSchedule)
                views.setOnClickPendingIntent(R.id.containerTabClasses, piSchedule)

                val examIntent = Intent(context, MainActivity::class.java).apply {
                    action = "ACTION_OPEN_TAB"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_DEEP_LINK_DESTINATION", "DESTINATION_PORTAL_EXAMS")
                }
                val piExam = PendingIntent.getActivity(context, 202, examIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.layoutOverviewExams, piExam)
                views.setOnClickPendingIntent(R.id.containerTabExams, piExam)

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
