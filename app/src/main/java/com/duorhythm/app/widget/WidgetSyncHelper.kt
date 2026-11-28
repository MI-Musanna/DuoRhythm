package com.duorhythm.app.widget

import android.content.Context

/**
 * Coordinates refreshes for all home-screen AppWidgetProviders after
 * profile switches or schedule/academic mutations.
 */
object WidgetSyncHelper {
    fun refreshAllWidgets(context: Context) {
        val appCtx = context.applicationContext ?: context
        try {
            AcademicHubWidget.pushUpdate(appCtx)
        } catch (_: Exception) {}
        try {
            TimetableGlanceWidget.pushUpdate(appCtx)
        } catch (_: Exception) {}
        try {
            ExamCountdownWidget.pushUpdate(appCtx)
        } catch (_: Exception) {}
    }
}
