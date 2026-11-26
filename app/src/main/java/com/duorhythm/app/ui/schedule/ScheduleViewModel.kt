package com.duorhythm.app.ui.schedule

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.data.repository.ProfileRepository
import com.duorhythm.app.data.repository.TimetableRepository
import com.duorhythm.app.util.TimetableUtils
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
class ScheduleViewModel(application: Application = com.duorhythm.app.DuoRhythmApp.instance) : AndroidViewModel(application) {

    private val dbHelper = DatabaseHelper.getInstance(application)
    private val timetableRepository = TimetableRepository(application)

    private val vmJob = SupervisorJob()
    val viewModelScope = CoroutineScope(Dispatchers.Main.immediate + vmJob)

    private val _selectedDayFlow = MutableStateFlow(
        TimetableUtils.calendarDayToDbDay(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK))
    )
    val selectedDayFlow: StateFlow<Int> get() = _selectedDayFlow

    private val _classesFlow = MutableStateFlow<List<ClassScheduleItem>>(emptyList())
    val classesFlow: StateFlow<List<ClassScheduleItem>> get() = _classesFlow
    val currentDayClasses: StateFlow<List<ClassScheduleItem>> get() = classesFlow

    init {
        viewModelScope.launch {
            ProfileRepository.activeProfileFlow
                .combine(_selectedDayFlow) { profile, day ->
                    val profileId = profile?.id ?: ProfileManager.getActiveProfileId(application)
                    Pair(profileId, day)
                }
                .flatMapLatest { (profileId, day) ->
                    timetableRepository.observeScheduleForDay(day, profileId)
                }
                .flowOn(Dispatchers.IO)
                .collect { classes ->
                    _classesFlow.value = classes
                }
        }
    }

    fun selectDay(dayOfWeek: Int) {
        _selectedDayFlow.value = dayOfWeek
    }

    fun setClassesForDay(classes: List<ClassScheduleItem>) {
        _classesFlow.value = classes
    }

    fun loadClassesForDay(profileId: Long = ProfileManager.getActiveProfileId(), dayOfWeek: Int) {
        _selectedDayFlow.value = dayOfWeek
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
