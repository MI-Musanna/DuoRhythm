package com.duorhythm.app.ui.portal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PortalViewModel : ViewModel() {
    private val _isCoursesCollapsed = MutableLiveData<Boolean>(false)
    val isCoursesCollapsed: LiveData<Boolean> = _isCoursesCollapsed

    fun setCoursesCollapsed(collapsed: Boolean) {
        _isCoursesCollapsed.value = collapsed
    }
}
