package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duorhythm.app.util.NotificationHelper

class AssessmentAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ASSESSMENT_ALERT = "com.duorhythm.app.ACTION_ASSESSMENT_ALERT"
        const val EXTRA_ASSESSMENT_ID = "extra_assessment_id"
        const val EXTRA_TITLE = "extra_assessment_title"
        const val EXTRA_BODY = "extra_assessment_body"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Disabled individual exam alarms; consolidated daily morning briefing handles all exams
    }
}
