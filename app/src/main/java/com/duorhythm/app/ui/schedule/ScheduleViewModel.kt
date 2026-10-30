package com.duorhythm.app.ui.schedule

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.ClassScheduleItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduleViewModel(application: Application = com.duorhythm.app.LifeSyncApp.instance) : AndroidViewModel(application) {

    private val dbHelper = DatabaseHelper.getInstance(application)

    private val vmJob = SupervisorJob()
    val viewModelScope = CoroutineScope(Dispatchers.Main.immediate + vmJob)

    private val _classesFlow = MutableStateFlow<List<ClassScheduleItem>>(emptyList())
    val classesFlow: StateFlow<List<ClassScheduleItem>> = _classesFlow.asStateFlow()

    fun loadClassesForDay(profileId: Long = ProfileManager.getActiveProfileId(), dayOfWeek: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val classes = dbHelper.getScheduleForDay(dayOfWeek, profileId)
                _classesFlow.value = classes
            } catch (e: Throwable) {
                Log.e("DUO_SCHEDULE", "Failed loading classes for day in ViewModel", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        vmJob.cancel()
    }
}
