package com.duorhythm.app.data.repository

import android.content.Context
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TaskItem
import com.duorhythm.app.scheduler.TaskAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TaskRepository(private val context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    fun observeTasks(profileId: Int, isGoal: Boolean? = null): Flow<List<TaskItem>> =
        observeTasks(profileId.toLong(), isGoal)

    fun observeTasks(profileId: Long, isGoal: Boolean? = null): Flow<List<TaskItem>> =
        dbHelper.getTasksFlow(profileId, isGoal)

    fun insertTask(task: TaskItem): Long = dbHelper.insertTask(task)

    fun updateTask(task: TaskItem): Int = dbHelper.updateTask(task)

    fun updateTaskCompletion(taskId: Long, isCompleted: Boolean) =
        dbHelper.updateTaskCompletion(taskId, isCompleted)

    fun updateTaskProgress(taskId: Long, progress: Int) =
        dbHelper.updateTaskProgress(taskId, progress)

    fun deleteTask(taskId: Long): Int {
        TaskAlarmScheduler.cancelTaskReminder(context, taskId)
        return dbHelper.deleteTask(taskId)
    }

    suspend fun insertTaskAsync(task: TaskItem): Long = withContext(Dispatchers.IO) { insertTask(task) }
    suspend fun updateTaskAsync(task: TaskItem): Int = withContext(Dispatchers.IO) { updateTask(task) }
    suspend fun deleteTaskAsync(taskId: Long): Int = withContext(Dispatchers.IO) { deleteTask(taskId) }
}
