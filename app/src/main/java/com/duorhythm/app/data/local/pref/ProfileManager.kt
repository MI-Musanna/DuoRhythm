package com.duorhythm.app.data.local.pref

import android.content.Context
import android.content.SharedPreferences
import database.entities.ProfileEntity
import java.security.MessageDigest

object ProfileManager {

    const val PREFS_NAME = "duorhythm_profile_prefs"
    const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    const val KEY_ONBOARDING_COMPLETED = "is_onboarding_completed"

    @Volatile
    private var cachedActiveProfileId: Long? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getActiveProfileId(context: Context? = null): Long {
        cachedActiveProfileId?.let { return it }
        val ctx = context ?: try { com.duorhythm.app.LifeSyncApp.instance } catch (_: Throwable) { null }
        if (ctx == null) return 1L
        val id = try {
            getPrefs(ctx).getLong(KEY_ACTIVE_PROFILE_ID, 1L)
        } catch (_: ClassCastException) {
            try {
                getPrefs(ctx).getInt(KEY_ACTIVE_PROFILE_ID, 1).toLong()
            } catch (_: Exception) {
                1L
            }
        } catch (_: Throwable) {
            1L
        }
        val resolvedId = if (id <= 0L) 1L else id
        cachedActiveProfileId = resolvedId
        return resolvedId
    }

    fun switchProfile(context: Context, newSlot: Int) {
        val slotLong = newSlot.toLong()
        cachedActiveProfileId = slotLong
        getPrefs(context).edit().putLong(KEY_ACTIVE_PROFILE_ID, slotLong).apply()
    }

    fun setActiveProfileId(context: Context, id: Long) {
        val resolvedId = if (id <= 0L) 1L else id
        cachedActiveProfileId = resolvedId
        getPrefs(context).edit().putLong(KEY_ACTIVE_PROFILE_ID, resolvedId).apply()
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun createInitialProfile(context: Context, name: String, password: String, avatarUri: String = ""): Long {
        val dbHelper = com.duorhythm.app.data.local.db.DatabaseHelper.getInstance(context)
        val existingSlot1 = dbHelper.profileDao.getProfileBySlot(1)
        val passwordHash = if (password.isNotBlank()) hashPassword(password) else ""
        val profile = ProfileEntity(
            id = existingSlot1?.id ?: 0L,
            slotNumber = 1,
            name = name.ifBlank { "Scholar" },
            email = "",
            passwordHash = passwordHash,
            institution = "",
            department = "",
            semester = "",
            avatarUri = avatarUri.ifBlank { null } ?: existingSlot1?.avatarUri,
            rememberEmail = false
        )
        val id = if (existingSlot1 != null) {
            dbHelper.profileDao.updateProfile(profile)
            existingSlot1.id
        } else {
            dbHelper.profileDao.insertProfile(profile)
        }
        val finalId = if (id <= 0L) 1L else id
        setActiveProfileId(context, finalId)
        syncUserProfile(context, profile.copy(id = finalId))
        setOnboardingCompleted(context, true)
        return finalId
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
        UserProfileManager.setAvatarUri(context, profile.avatarUri ?: "")
    }
}
