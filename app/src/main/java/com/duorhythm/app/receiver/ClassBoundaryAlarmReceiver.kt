package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.duorhythm.app.widget.AcademicHubWidget

class ClassBoundaryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("DUO_CLASS_ALARM", "Class boundary reached (start/end). Pushing instant widget neon update.")
        AcademicHubWidget.pushUpdate(context)
    }
}
