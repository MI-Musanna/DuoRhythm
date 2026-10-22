package com.duorhythm.app.data.local.db

import android.content.Context
import androidx.room.Database
import database.dao.AttendanceDao
import database.dao.CategoryDao
import database.dao.HolidayDao
import database.dao.TimetableDao
import database.entities.AttendanceRecord
import database.entities.CategoryEntity
import database.entities.HolidayRecord

/**
 * AppDatabase entrypoint providing Room-compatible architecture interface
 * and safe fallback destructive migration capabilities for older local installs.
 */
@Database(
    entities = [AttendanceRecord::class, HolidayRecord::class, CategoryEntity::class],
    version = DatabaseHelper.DATABASE_VERSION,
    exportSchema = false
)
class AppDatabase private constructor(context: Context) {
    private val dbHelper: DatabaseHelper = DatabaseHelper.getInstance(context)

    fun attendanceDao(): AttendanceDao = dbHelper.attendanceDao
    fun timetableDao(): TimetableDao = dbHelper.timetableDao
    fun holidayDao(): HolidayDao = dbHelper.holidayDao
    fun categoryDao(): CategoryDao = dbHelper.categoryDao

    val attendanceDao: AttendanceDao get() = dbHelper.attendanceDao
    val timetableDao: TimetableDao get() = dbHelper.timetableDao
    val holidayDao: HolidayDao get() = dbHelper.holidayDao
    val categoryDao: CategoryDao get() = dbHelper.categoryDao

    companion object {
        const val DATABASE_NAME = DatabaseHelper.DATABASE_NAME
        const val DATABASE_VERSION = DatabaseHelper.DATABASE_VERSION

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context.applicationContext).also { instance = it }
            }

        fun getAttendanceDao(context: Context): AttendanceDao = getInstance(context).attendanceDao()
        fun getTimetableDao(context: Context): TimetableDao = getInstance(context).timetableDao()
        fun getHolidayDao(context: Context): HolidayDao = getInstance(context).holidayDao()
        fun getCategoryDao(context: Context): CategoryDao = getInstance(context).categoryDao()

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
