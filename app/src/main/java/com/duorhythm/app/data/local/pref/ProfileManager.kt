package com.duorhythm.app.data.local.pref

import android.content.Context
import android.content.SharedPreferences
import database.entities.ProfileEntity
import java.security.MessageDigest

object ProfileManager {

    const val PREFS_NAME = "duorhythm_profile_prefs"
    const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    const val KEY_ONBOARDING_COMPLETED = "is_onboarding_completed"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getActiveProfileId(context: Context): Long {
        return getPrefs(context).getLong(KEY_ACTIVE_PROFILE_ID, 1L)
    }

    fun setActiveProfileId(context: Context, id: Long) {
        getPrefs(context).edit().putLong(KEY_ACTIVE_PROFILE_ID, id).apply()
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            password
        }
    }

    fun syncUserProfile(context: Context, profile: ProfileEntity) {
        if (profile.name.isNotBlank()) {
            UserProfileManager.setUserName(context, profile.name)
        }
        if (profile.institution.isNotBlank()) {
            UserProfileManager.setInstitutionName(context, profile.institution)
        }
        if (profile.department.isNotBlank()) {
            UserProfileManager.setAcademicFaculty(context, profile.department)
        }
        if (!profile.avatarUri.isNullOrBlank()) {
            UserProfileManager.setAvatarUri(context, profile.avatarUri)
        }
    }
}
