package com.pocketdev.duorhythm

import android.app.Application
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper

class LifeSyncApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            // Apply persisted theme preference before windows inflate
            com.pocketdev.duorhythm.data.local.pref.ThemeManager.applySavedTheme(this)
            // Preload/verify database with fallback destructive migration safety
            com.pocketdev.duorhythm.data.local.db.AppDatabase.fallbackToDestructiveMigration(this)
            DatabaseHelper.getInstance(this).writableDatabase
        } catch (e: Exception) {
            e.printStackTrace()
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                com.pocketdev.duorhythm.security.LifeSyncSecurityManager.onActivityStarted()
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                com.pocketdev.duorhythm.security.LifeSyncSecurityManager.onActivityStopped()
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
