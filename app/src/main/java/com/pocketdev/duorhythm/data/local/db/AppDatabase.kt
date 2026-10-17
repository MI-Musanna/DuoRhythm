package com.pocketdev.duorhythm.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * AppDatabase entrypoint providing Room-compatible architecture interface
 * and safe fallback destructive migration capabilities for older local installs.
 */
class AppDatabase private constructor(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    companion object {
        const val DATABASE_NAME = DatabaseHelper.DATABASE_NAME
        const val DATABASE_VERSION = DatabaseHelper.DATABASE_VERSION

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context.applicationContext).also { instance = it }
            }

        /**
         * Verifies and configures fallback to destructive migration for database instances
         * to prevent startup crashes on older local installations.
         */
        fun fallbackToDestructiveMigration(context: Context) {
            val db = DatabaseHelper.getInstance(context)
            try {
                // Ensure writable database can be opened
                val sqliteDb = db.writableDatabase
                if (!sqliteDb.isOpen) {
                    context.deleteDatabase(DATABASE_NAME)
                }
            } catch (_: Exception) {
                // Perform clean fallback destructive rebuild if corrupt
                try {
                    context.deleteDatabase(DATABASE_NAME)
                } catch (_: Exception) {}
            }
        }
    }

    fun getDatabaseHelper(): DatabaseHelper = dbHelper
}

typealias AppDatabaseHelper = DatabaseHelper
