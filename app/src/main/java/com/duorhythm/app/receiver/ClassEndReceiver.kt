package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import com.duorhythm.app.R
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.scheduler.ClassAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ClassEndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ClassAlarmScheduler.ACTION_CLASS_END && action != "com.duorhythm.app.ACTION_CLASS_END") return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleClassEnd(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleClassEnd(context: Context, intent: Intent) {
        if (!SmartAutomationManager.isAutoSilentClassesEnabled(context)) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = context.getSharedPreferences(ClassAlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val classId = intent.getLongExtra(ClassAlarmScheduler.EXTRA_CLASS_ID, -1L)
        val subjectName = intent.getStringExtra(ClassAlarmScheduler.EXTRA_SUBJECT_NAME) ?: "Class"
        val endTimeStr = intent.getStringExtra(ClassAlarmScheduler.EXTRA_END_TIME) ?: ""
        val dayOfWeek = intent.getIntExtra(ClassAlarmScheduler.EXTRA_DAY_OF_WEEK, -1)

        val db = DatabaseHelper.getInstance(context)

        // Determine current day of week (1=Mon ... 7=Sun)
        val cal = Calendar.getInstance()
        val todayDayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val targetDay = if (dayOfWeek in 1..7) dayOfWeek else todayDayOfWeek

        val todayClasses = db.timetableDao.getClassesForDay(targetDay)
        val currentClass = todayClasses.firstOrNull { it.id == classId }

        val currentEndMinutes = if (currentClass != null) {
            ClassAlarmScheduler.parseMinutes(currentClass.endTime)
        } else {
            ClassAlarmScheduler.parseMinutes(endTimeStr)
        }

        // Smart Back-to-Back Lookahead:
        // Find if there is an upcoming class whose start time is within 10 minutes of the ending class
        val consecutiveClass = todayClasses
            .filter { it.id != classId }
            .mapNotNull { candidate ->
                val startMin = ClassAlarmScheduler.parseMinutes(candidate.startTime)
                val endMin = ClassAlarmScheduler.parseMinutes(candidate.endTime)
                val diff = startMin - currentEndMinutes
                // Start within 10 minutes (i.e. nextClass.startTime - currentClass.endTime <= 10 min)
                if (diff in -5..10 && endMin > currentEndMinutes) {
                    Pair(candidate, diff)
                } else {
                    null
                }
            }
            .minByOrNull { it.second }
            ?.first

        SmartAutomationManager.createNotificationChannels(context)

        if (consecutiveClass != null) {
            // Consecutive / back-to-back class exists:
            // 1. DO NOT restore normal sound profile
            // 2. Keep device in Silent / DND mode without interruption
            prefs.edit().putBoolean(ClassAlarmScheduler.KEY_IN_CLASS_DND, true).apply()

            // 3. Suppress "Normal sound profile restored" notification, cancel previous DND notification
            nm?.cancel(30101)

            // 4. Post smooth transition notification
            val roomText = if (consecutiveClass.room.isNotBlank()) " in Room ${consecutiveClass.room}" else ""
            val transitionContent = "Silent mode maintained. Class starts at ${consecutiveClass.startTime}$roomText."

            val transitionNotif = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_DND)
                .setSmallIcon(R.drawable.ic_schedule)
                .setContentTitle("Consecutive Lecture: ${consecutiveClass.subjectName}")
                .setContentText(transitionContent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(transitionContent))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            nm?.notify(30101, transitionNotif)
        } else {
            // No consecutive class exists:
            // 1. Restore normal sound profile
            val savedMode = prefs.getInt(ClassAlarmScheduler.KEY_SAVED_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
            try {
                audioManager.ringerMode = savedMode
            } catch (_: Exception) {}
            prefs.edit().putBoolean(ClassAlarmScheduler.KEY_IN_CLASS_DND, false).apply()

            // 2. Cancel previous class DND notification
            nm?.cancel(30101)

            // 3. Post standard restored notification
            val restoredNotif = NotificationCompat.Builder(context, SmartAutomationManager.CHANNEL_DND)
                .setSmallIcon(R.drawable.ic_schedule)
                .setContentTitle("Class Ended: $subjectName")
                .setContentText("Normal sound profile restored.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(8000)
                .build()

            nm?.notify(30102, restoredNotif)
        }

        // Reschedule for next week's occurrence
        if (classId > 0) {
            val itemToReschedule = currentClass ?: db.getAllClasses().firstOrNull { it.id == classId }
            if (itemToReschedule != null) {
                ClassAlarmScheduler.scheduleClassAlarms(context, itemToReschedule)
            }
        }
    }
}
