package com.duorhythm.app.data.repository

import android.content.Context
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import database.entities.ProfileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ProfileRepository {
    private val _activeProfileFlow = MutableStateFlow<ProfileEntity?>(null)
    val activeProfileFlow: StateFlow<ProfileEntity?> get() = _activeProfileFlow

    private val _allProfilesFlow = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val allProfilesFlow: StateFlow<List<ProfileEntity>> get() = _allProfilesFlow

    @Synchronized
    fun refreshProfiles(context: Context) {
        try {
            val dbHelper = DatabaseHelper.getInstance(context)
            val profiles = dbHelper.profileDao.getAllProfiles()
            _allProfilesFlow.value = profiles
            val activeId = ProfileManager.getActiveProfileId(context)
            val active = profiles.find { it.id == activeId } ?: profiles.firstOrNull()
            _activeProfileFlow.value = active
            if (active != null) {
                ProfileManager.syncUserProfile(context, active)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun getActiveProfile(context: Context): ProfileEntity? {
        if (_activeProfileFlow.value == null) {
            refreshProfiles(context)
        }
        return _activeProfileFlow.value
    }
}
