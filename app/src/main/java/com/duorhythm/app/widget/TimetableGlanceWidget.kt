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
import com.duorhythm.app.ui.main.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TimetableGlanceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_timetable_glance)
            val cal = Calendar.getInstance()
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)

            views.setTextViewText(R.id.tvWidgetDayTag, dayName.uppercase())

            val db = DatabaseHelper.getInstance(context)
            val classes = db.getClasses(1L, dayOfWeek)

            if (classes.isNotEmpty()) {
                val nextClass = classes.first()
                views.setTextViewText(R.id.tvWidgetSubjectTitle, nextClass.subjectName)
                views.setTextViewText(R.id.tvWidgetClassDetails, "${nextClass.startTime} - ${nextClass.endTime} • Room ${nextClass.room} • ${nextClass.teacherName}")
            } else {
                views.setTextViewText(R.id.tvWidgetSubjectTitle, "No classes scheduled")
                views.setTextViewText(R.id.tvWidgetClassDetails, "All clear for today • Tap to view schedule")
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.layoutWidgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun pushUpdate(context: Context) {
            val intent = Intent(context, TimetableGlanceWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, TimetableGlanceWidget::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
