package database.dao

import com.duorhythm.app.data.model.TaskItem

interface TaskDao {
    fun getAllTasks(filter: String = "ALL", isGoal: Boolean? = null, profileId: Long): List<TaskItem>
    fun insertTask(task: TaskItem): Long
    fun updateTask(task: TaskItem): Int
    fun deleteTask(id: Long): Int
    fun deleteByProfileId(profileId: Long): Int
}
