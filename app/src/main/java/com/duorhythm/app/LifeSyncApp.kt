package com.duorhythm.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.db.DatabaseMaintenanceWorker

class LifeSyncApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        AppCrashHandler.install(this)

        try {
            DatabaseMaintenanceWorker.scheduleWeekly(this)
        } catch (e: Exception) {
            android.util.Log.e("DUO_STARTUP", "Error scheduling DB maintenance", e)
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
                com.duorhythm.app.security.LifeSyncSecurityManager.onActivityStarted()
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                com.duorhythm.app.security.LifeSyncSecurityManager.onActivityStopped()
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    companion object {
        lateinit var instance: LifeSyncApp
            private set
    }
}
