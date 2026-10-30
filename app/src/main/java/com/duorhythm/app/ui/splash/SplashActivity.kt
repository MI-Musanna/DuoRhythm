package com.duorhythm.app.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.MainActivity
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.ui.onboarding.OnboardingActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val failSafeHandler = Handler(Looper.getMainLooper())
    private val failSafeRunnable = Runnable {
        if (!isFinishing && !isDestroyed) {
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        failSafeHandler.postDelayed(failSafeRunnable, 3000L)

        // Ensure default database integrity
        try {
            DatabaseHelper.getInstance(this).ensureDefaultProfileIfEmpty()
        } catch (e: Throwable) {
            android.util.Log.e("DUO_SPLASH", "Error ensuring default profile", e)
        }

        try {
            val isOnboardingDone = ProfileManager.isOnboardingCompleted(this)
            val targetIntent = if (isOnboardingDone) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, OnboardingActivity::class.java)
            }

            targetIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            failSafeHandler.removeCallbacks(failSafeRunnable)
            startActivity(targetIntent)
            finish()
        } catch (e: Throwable) {
            android.util.Log.e("DUO_SPLASH", "Error launching target activity", e)
            try {
                val fallbackIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                failSafeHandler.removeCallbacks(failSafeRunnable)
                startActivity(fallbackIntent)
                finish()
            } catch (fatal: Throwable) {
                android.util.Log.e("DUO_SPLASH", "Fatal fallback error", fatal)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        failSafeHandler.removeCallbacks(failSafeRunnable)
    }
}
