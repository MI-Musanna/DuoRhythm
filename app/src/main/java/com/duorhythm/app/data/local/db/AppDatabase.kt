package com.duorhythm.app.data.local.db

import android.content.Context
import androidx.room.Database
import database.dao.AccountDao
import database.dao.AssessmentCategoryDao
import database.dao.AssessmentDao
import database.dao.AttendanceDao
import database.dao.CategoryDao
import database.dao.ClassTypeDao
import database.dao.CourseDao
import database.dao.HolidayDao
import database.dao.ProfileDao
import database.dao.SubscriptionDao
import database.dao.TimetableDao
import database.entities.AssessmentCategoryEntity
import database.entities.AssessmentEntity
import database.entities.AttendanceRecord
import database.entities.CategoryEntity
import database.entities.ClassTypeEntity
import database.entities.HolidayRecord
import database.entities.ProfileEntity
import database.entities.QuickNoteEntity
import database.entities.SubscriptionEntity

/**
 * AppDatabase entrypoint providing Room-compatible architecture interface
 * and safe fallback destructive migration capabilities for older local installs.
 */
@Database(
    entities = [
        AttendanceRecord::class,
        HolidayRecord::class,
        CategoryEntity::class,
        ProfileEntity::class,
        ClassTypeEntity::class,
        AssessmentCategoryEntity::class,
        SubscriptionEntity::class,
        QuickNoteEntity::class
    ],
    version = DatabaseHelper.DATABASE_VERSION,
    exportSchema = false
)
class AppDatabase private constructor(context: Context) {
    private val dbHelper: DatabaseHelper = DatabaseHelper.getInstance(context)

    fun attendanceDao(): AttendanceDao = dbHelper.attendanceDao
    fun timetableDao(): TimetableDao = dbHelper.timetableDao
    fun holidayDao(): HolidayDao = dbHelper.holidayDao
    fun categoryDao(): CategoryDao = dbHelper.categoryDao
    fun profileDao(): ProfileDao = dbHelper.profileDao
    fun classTypeDao(): ClassTypeDao = dbHelper.classTypeDao
    fun assessmentCategoryDao(): database.dao.AssessmentCategoryDao = dbHelper.assessmentCategoryDao
    fun courseDao(): CourseDao = dbHelper.courseDao
    fun assessmentDao(): AssessmentDao = dbHelper.assessmentDao
    fun walletDao(): DatabaseHelper.WalletDao = dbHelper.walletDao
    fun taskDao(): DatabaseHelper.TaskDao = dbHelper.taskDao
    fun subscriptionDao(): SubscriptionDao = dbHelper.subscriptionDao
    fun accountDao(): AccountDao = dbHelper.accountDao

    val attendanceDao: AttendanceDao get() = dbHelper.attendanceDao
    val timetableDao: TimetableDao get() = dbHelper.timetableDao
    val holidayDao: HolidayDao get() = dbHelper.holidayDao
    val categoryDao: CategoryDao get() = dbHelper.categoryDao
    val profileDao: ProfileDao get() = dbHelper.profileDao
    val classTypeDao: ClassTypeDao get() = dbHelper.classTypeDao
    val assessmentCategoryDao: database.dao.AssessmentCategoryDao get() = dbHelper.assessmentCategoryDao
    val courseDao: CourseDao get() = dbHelper.courseDao
    val assessmentDao: AssessmentDao get() = dbHelper.assessmentDao
    val walletDao: DatabaseHelper.WalletDao get() = dbHelper.walletDao
    val taskDao: DatabaseHelper.TaskDao get() = dbHelper.taskDao
    val subscriptionDao: SubscriptionDao get() = dbHelper.subscriptionDao
    val accountDao: AccountDao get() = dbHelper.accountDao

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
        fun getProfileDao(context: Context): ProfileDao = getInstance(context).profileDao()
        fun getClassTypeDao(context: Context): ClassTypeDao = getInstance(context).classTypeDao()

        /**
         * Verifies and configures fallback to destructive migration for database instances
         * to prevent startup crashes on older local installations.
         */
        fun fallbackToDestructiveMigration(context: Context) {
            try {
                val db = DatabaseHelper.getInstance(context)
                val sqliteDb = db.writableDatabase
                if (!sqliteDb.isOpen) {
                    context.deleteDatabase(DATABASE_NAME)
                    context.deleteDatabase("duorhythm.db")
                    DatabaseHelper.getInstance(context).writableDatabase
                }
            } catch (_: Throwable) {
                // Perform clean fallback destructive rebuild if corrupt
                try {
                    context.deleteDatabase(DATABASE_NAME)
                    context.deleteDatabase("duorhythm.db")
                    DatabaseHelper.getInstance(context).writableDatabase
                } catch (_: Throwable) {}
            }
        }
    }

    fun getDatabaseHelper(): DatabaseHelper = dbHelper
}

typealias AppDatabaseHelper = DatabaseHelper
