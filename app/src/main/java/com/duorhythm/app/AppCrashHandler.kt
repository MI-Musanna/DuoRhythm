package com.duorhythm.app

import android.content.Context
import android.content.Intent
import android.util.Log
import com.duorhythm.app.ui.crash.CrashActivity
import java.io.File

object AppCrashHandler {
    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = Log.getStackTraceString(throwable)
            try {
                val crashLogFile = File(context.filesDir, "last_fatal_crash.txt")
                crashLogFile.writeText(stackTrace)
                Log.e("DUO_FATAL", "FATAL UNCAUGHT APP CRASH: ", throwable)
            } catch (ignored: Exception) {
            }
            try {
                val crashIntent = Intent(context, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_CRASH_INFO, stackTrace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(crashIntent)
            } catch (launchError: Throwable) {
                Log.e("DUO_FATAL", "Failed launching CrashActivity: ", launchError)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
