package com.duorhythm.app.util

import android.content.Context
import com.duorhythm.app.scheduler.BriefingAlarmScheduler

object BriefingScheduler {
    fun scheduleDailyBriefing(context: Context) {
        BriefingAlarmScheduler.scheduleDailyBriefings(context)
    }
}
