package com.duorhythm.app.ui.tasks

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.TaskItem
import com.duorhythm.app.data.repository.ProfileRepository
import com.duorhythm.app.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(application: Application = com.duorhythm.app.DuoRhythmApp.instance) : AndroidViewModel(application) {

    private val dbHelper = DatabaseHelper.getInstance(application)
    private val taskRepository = TaskRepository(application)

    private val vmJob = SupervisorJob()
    val viewModelScope = CoroutineScope(Dispatchers.Main.immediate + vmJob)

    private val _allTasksFlow = MutableStateFlow<List<TaskItem>>(emptyList())
    val allTasksFlow: StateFlow<List<TaskItem>> get() = _allTasksFlow

    private val _tasksFlow = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasksFlow: StateFlow<List<TaskItem>> get() = _tasksFlow

    private val _goalsFlow = MutableStateFlow<List<TaskItem>>(emptyList())
    val goalsFlow: StateFlow<List<TaskItem>> get() = _goalsFlow

    init {
        viewModelScope.launch {
            ProfileRepository.activeProfileFlow
                .flatMapLatest { activeProfile ->
                    val profileId = activeProfile?.id ?: ProfileManager.getActiveProfileId(application)
                    taskRepository.observeTasks(profileId, null)
                        .combine(kotlinx.coroutines.flow.flowOf(profileId)) { items, _ -> items }
                }
                .flowOn(Dispatchers.IO)
                .collect { items ->
                    _allTasksFlow.value = items
                    _tasksFlow.value = items.filter { !it.isGoal }
                    _goalsFlow.value = items.filter { it.isGoal }
                }
        }
    }

    fun loadTasks(profileId: Long = ProfileManager.getActiveProfileId()) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = dbHelper.getAllTasks("ALL", null, profileId)
                _allTasksFlow.value = items
                _tasksFlow.value = items.filter { !it.isGoal }
                _goalsFlow.value = items.filter { it.isGoal }
            } catch (e: Throwable) {
                Log.e("DUO_TASKS", "Failed loading tasks in ViewModel", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        vmJob.cancel()
    }
}
