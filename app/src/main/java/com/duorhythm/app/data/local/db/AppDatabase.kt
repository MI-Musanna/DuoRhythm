package com.duorhythm.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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

        val roomDatabaseCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Food & Dining', 'ic_food', '#F59E0B', 'EXPENSE')")
                db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Education', 'ic_book', '#00E5FF', 'EXPENSE')")
                db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Transport', 'ic_bus', '#10B981', 'EXPENSE')")
                db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Allowance', 'ic_wallet', '#8B5CF6', 'INCOME')")
            }
        }

        fun databaseBuilder(context: Context, klass: Class<AppDatabase> = AppDatabase::class.java, name: String = DATABASE_NAME): Builder {
            return Builder(context)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Food & Dining', 'ic_food', '#F59E0B', 'EXPENSE')")
                        db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Education', 'ic_book', '#00E5FF', 'EXPENSE')")
                        db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Transport', 'ic_bus', '#10B981', 'EXPENSE')")
                        db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Allowance', 'ic_wallet', '#8B5CF6', 'INCOME')")
                    }
                })
        }

        private fun createSupportSQLiteDatabaseWrapper(sqliteDb: android.database.sqlite.SQLiteDatabase): SupportSQLiteDatabase {
            return java.lang.reflect.Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "execSQL" -> {
                        if (args != null && args.size == 1) {
                            sqliteDb.execSQL(args[0] as String)
                        } else if (args != null && args.size == 2) {
                            sqliteDb.execSQL(args[0] as String, args[1] as Array<out Any?>)
                        }
                        null
                    }
                    "isOpen" -> sqliteDb.isOpen
                    "isReadOnly" -> sqliteDb.isReadOnly
                    "close" -> {
                        sqliteDb.close()
                        null
                    }
                    else -> null
                }
            } as SupportSQLiteDatabase
        }

        class Builder(private val context: Context) {
            private val callbacks = mutableListOf<RoomDatabase.Callback>()
            private var fallbackDestructive = false

            fun addCallback(callback: RoomDatabase.Callback): Builder {
                callbacks.add(callback)
                return this
            }

            fun fallbackToDestructiveMigration(): Builder {
                fallbackDestructive = true
                return this
            }

            fun build(): AppDatabase {
                val db = getInstance(context)
                val helper = db.getDatabaseHelper()
                try {
                    val sqliteDb = helper.writableDatabase
                    val cursor = sqliteDb.rawQuery("SELECT COUNT(*) FROM transaction_categories", null)
                    val count = cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
                    if (count == 0) {
                        val supportDb = createSupportSQLiteDatabaseWrapper(sqliteDb)
                        for (cb in callbacks) {
                            cb.onCreate(supportDb)
                        }
                    }
                } catch (_: Throwable) {}
                return db
            }
        }

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
            try {
                val sqliteDb = DatabaseHelper.getInstance(context).writableDatabase
                val cursor = sqliteDb.rawQuery("SELECT COUNT(*) FROM transaction_categories", null)
                val count = cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
                if (count == 0) {
                    val supportDb = createSupportSQLiteDatabaseWrapper(sqliteDb)
                    roomDatabaseCallback.onCreate(supportDb)
                }
            } catch (_: Throwable) {}
        }
    }

    fun getDatabaseHelper(): DatabaseHelper = dbHelper
}

typealias AppDatabaseHelper = DatabaseHelper
