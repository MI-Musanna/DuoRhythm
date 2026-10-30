package com.duorhythm.app.ui.tasks

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.TaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TasksViewModel(application: Application = com.duorhythm.app.LifeSyncApp.instance) : AndroidViewModel(application) {

    private val dbHelper = DatabaseHelper.getInstance(application)

    private val _tasksFlow = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasksFlow: StateFlow<List<TaskItem>> = _tasksFlow.asStateFlow()

    fun loadTasks(profileId: Long = ProfileManager.getActiveProfileId()) {
        try {
            _tasksFlow.value = dbHelper.getAllTasks("ALL", null, profileId)
        } catch (e: Throwable) {
            Log.e("DUO_TASKS", "Failed loading tasks in ViewModel", e)
        }
    }
}
