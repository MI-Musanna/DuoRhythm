package com.duorhythm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.duorhythm.app.widget.AcademicHubWidget
import java.util.TimeZone

class TimezoneChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d("DUO_TIMEZONE", "System clock/timezone event captured: $action | Current Timezone: ${TimeZone.getDefault().id}")

        when (action) {
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                AcademicHubWidget.pushUpdate(context)
            }
        }
    }
}
