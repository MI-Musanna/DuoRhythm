package com.duorhythm.app

import android.content.Context
import android.util.Log
import java.io.File

object AppCrashHandler {
    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashLogFile = File(context.filesDir, "last_fatal_crash.txt")
                crashLogFile.writeText(Log.getStackTraceString(throwable))
                Log.e("DUO_FATAL", "FATAL UNCAUGHT APP CRASH: ", throwable)
            } catch (ignored: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
