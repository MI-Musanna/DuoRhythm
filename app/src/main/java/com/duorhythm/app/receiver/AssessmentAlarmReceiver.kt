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
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Upcoming Assessment"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "You have an exam scheduled."
        val assessmentId = intent.getLongExtra(EXTRA_ASSESSMENT_ID, System.currentTimeMillis())

        NotificationHelper.showAssessmentNotification(
            context = context,
            notificationId = (300000 + (assessmentId % 100000)).toInt(),
            title = title,
            body = body
        )
    }
}
