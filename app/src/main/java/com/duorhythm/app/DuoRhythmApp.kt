package com.duorhythm.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.db.DatabaseMaintenanceWorker
import java.io.File

class DuoRhythmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        migrateLegacyStorageIfNeeded()

        AppCrashHandler.install(this)

        try {
            com.duorhythm.app.util.ViewAnimationExtensions.installGlobalMotionEngine(this)
        } catch (e: Exception) {
            android.util.Log.e("DUO_STARTUP", "Error installing Global Motion Engine", e)
        }

        try {
            DatabaseMaintenanceWorker.scheduleWeekly(this)
            com.duorhythm.app.util.BriefingScheduler.scheduleDailyBriefing(this)
        } catch (e: Exception) {
            android.util.Log.e("DUO_STARTUP", "Error scheduling DB maintenance or Subscription Worker", e)
        }

        try {
            // Force the application-wide night mode strictly to dark
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            // Apply persisted theme preference before windows inflate
            com.duorhythm.app.data.local.pref.ThemeManager.applySavedTheme(this)
            // Preload/verify database with fallback destructive migration safety
            com.duorhythm.app.data.local.db.AppDatabase.fallbackToDestructiveMigration(this)
            DatabaseHelper.getInstance(this).writableDatabase
        } catch (e: Exception) {
            android.util.Log.e("DUO_STARTUP", "Error during Application initialization", e)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                com.duorhythm.app.security.DuoRhythmSecurityManager.onActivityStarted()
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                com.duorhythm.app.security.DuoRhythmSecurityManager.onActivityStopped()
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    private fun migrateLegacyStorageIfNeeded() {
        try {
            val oldDb = getDatabasePath("lifesync.db")
            val newDb = getDatabasePath("duorhythm.db")
            if (oldDb.exists() && !newDb.exists()) {
                oldDb.copyTo(newDb, overwrite = false)
                listOf("-wal", "-shm").forEach { suffix ->
                    val sidecar = File(oldDb.path + suffix)
                    if (sidecar.exists()) sidecar.copyTo(File(newDb.path + suffix), overwrite = false)
                }
            }
            val oldPrefs = getSharedPreferences("lifesync_prefs", MODE_PRIVATE)
            val newPrefs = getSharedPreferences("duorhythm_prefs", MODE_PRIVATE)
            if (oldPrefs.all.isNotEmpty() && newPrefs.all.isEmpty()) {
                val editor = newPrefs.edit()
                for ((k, v) in oldPrefs.all) {
                    when (v) {
                        is String -> editor.putString(k, v)
                        is Int -> editor.putInt(k, v)
                        is Boolean -> editor.putBoolean(k, v)
                        is Long -> editor.putLong(k, v)
                        is Float -> editor.putFloat(k, v)
                    }
                }
                editor.apply()
            }
        } catch (_: Exception) {}
    }

    companion object {
        lateinit var instance: DuoRhythmApp
            private set
    }
}

typealias LifeSyncApp = DuoRhythmApp
typealias DuoApplication = DuoRhythmApp
