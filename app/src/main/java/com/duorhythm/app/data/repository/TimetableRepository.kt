package com.duorhythm.app.data.repository

import android.content.Context
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.scheduler.AlarmSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TimetableRepository(private val context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    fun observeScheduleForDay(dayOfWeek: String, profileId: Int): Flow<List<ClassScheduleItem>> {
        val parsedDay = dayOfWeek.trim().toIntOrNull() ?: parseDayName(dayOfWeek)
        return observeScheduleForDay(parsedDay, profileId.toLong())
    }

    fun observeScheduleForDay(dayOfWeek: Int, profileId: Long): Flow<List<ClassScheduleItem>> =
        dbHelper.getScheduleForDayFlow(dayOfWeek, profileId)

    private fun parseDayName(name: String): Int = when (name.trim().lowercase()) {
        "mon", "monday" -> 1
        "tue", "tuesday" -> 2
        "wed", "wednesday" -> 3
        "thu", "thursday" -> 4
        "fri", "friday" -> 5
        "sat", "saturday" -> 6
        "sun", "sunday" -> 7
        else -> 1
    }

    fun insertClass(item: ClassScheduleItem): Long {
        val newId = dbHelper.insertClassSchedule(item)
        if (newId > 0) {
            val scheduled = item.copy(id = newId)
            AlarmSyncManager.scheduleClassAlarms(context, scheduled)
        }
        return newId
    }

    suspend fun insertClassAsync(item: ClassScheduleItem): Long = withContext(Dispatchers.IO) {
        insertClass(item)
    }

    fun updateClass(item: ClassScheduleItem): Int {
        AlarmSyncManager.cancelClassAlarms(context, item.id)
        val rows = dbHelper.updateClassSchedule(item)
        if (rows > 0) {
            AlarmSyncManager.scheduleClassAlarms(context, item)
        }
        return rows
    }

    suspend fun updateClassAsync(item: ClassScheduleItem): Int = withContext(Dispatchers.IO) {
        updateClass(item)
    }

    fun deleteClass(id: Long): Int {
        AlarmSyncManager.cancelClassAlarms(context, id)
        return dbHelper.deleteClassSchedule(id)
    }

    suspend fun deleteClassAsync(id: Long): Int = withContext(Dispatchers.IO) {
        deleteClass(id)
    }

    fun rescheduleClass(item: ClassScheduleItem) {
        AlarmSyncManager.cancelClassAlarms(context, item.id)
        AlarmSyncManager.scheduleClassAlarms(context, item)
    }

    fun resyncAllClasses() {
        AlarmSyncManager.resyncAll(context)
    }
}
