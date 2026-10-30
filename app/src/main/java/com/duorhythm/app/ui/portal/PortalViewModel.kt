package com.duorhythm.app.ui.portal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.StudentSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PortalViewModel(
    private val databaseHelper: DatabaseHelper = DatabaseHelper.getInstance(com.duorhythm.app.LifeSyncApp.instance)
) : ViewModel() {
    private val vmJob = SupervisorJob()
    val viewModelScope = CoroutineScope(Dispatchers.Main.immediate + vmJob)

    private val _isCoursesCollapsed = MutableLiveData<Boolean>(false)
    val isCoursesCollapsed: LiveData<Boolean> = _isCoursesCollapsed

    fun setCoursesCollapsed(collapsed: Boolean) {
        _isCoursesCollapsed.value = collapsed
    }

    private val _coursesFlow = MutableStateFlow<List<StudentSubject>>(emptyList())
    val coursesFlow: StateFlow<List<StudentSubject>> = _coursesFlow.asStateFlow()

    fun loadCourses(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val courses = databaseHelper.getStudentSubjects(profileId)
            _coursesFlow.value = courses
        }
    }

    override fun onCleared() {
        super.onCleared()
        vmJob.cancel()
    }
}
