package com.duorhythm.app.data.local.db

import com.duorhythm.app.data.model.*
import database.dao.AssessmentDao
import database.dao.CourseDao
import database.entities.AssessmentEntity
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper private constructor(private val appContext: Context) :
    SQLiteOpenHelper(appContext.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "lifesync.db"
        const val DATABASE_VERSION = 9

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context).also { instance = it }
            }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.execSQL("PRAGMA encoding = 'UTF-8'")
        } catch (_: Exception) {}
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS cards (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                card_name TEXT NOT NULL,
                bank_name TEXT NOT NULL,
                card_number TEXT NOT NULL,
                holder_name TEXT NOT NULL,
                card_type TEXT NOT NULL,
                color_hex TEXT NOT NULL,
                balance REAL DEFAULT 0.0
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                title TEXT NOT NULL,
                amount REAL NOT NULL,
                type TEXT NOT NULL,
                category TEXT NOT NULL,
                date TEXT NOT NULL,
                card_id INTEGER DEFAULT 0,
                currency TEXT DEFAULT 'USD',
                notes TEXT DEFAULT '',
                bucket_id INTEGER DEFAULT 0
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS wallet_buckets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                name TEXT NOT NULL,
                icon TEXT DEFAULT 'wallet',
                color_hex TEXT DEFAULT '#0284C7',
                connected_accounts TEXT DEFAULT '',
                balance REAL DEFAULT 0.0,
                note TEXT DEFAULT ''
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS subscriptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                service_name TEXT NOT NULL,
                cost REAL NOT NULL,
                currency TEXT DEFAULT 'BDT',
                billing_cycle TEXT DEFAULT 'MONTHLY',
                renewal_date TEXT NOT NULL,
                bucket_id INTEGER DEFAULT 0,
                bucket_name TEXT DEFAULT '',
                category TEXT DEFAULT 'Entertainment',
                icon TEXT DEFAULT 'subscription',
                is_active INTEGER DEFAULT 1,
                auto_log INTEGER DEFAULT 1
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS currency_rates (
                code TEXT PRIMARY KEY,
                rate REAL NOT NULL,
                symbol TEXT NOT NULL,
                last_updated TEXT NOT NULL
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                title TEXT NOT NULL,
                description TEXT DEFAULT '',
                category TEXT DEFAULT 'General',
                due_date TEXT DEFAULT NULL,
                priority TEXT DEFAULT 'MEDIUM',
                is_completed INTEGER DEFAULT 0,
                is_goal INTEGER DEFAULT 0,
                progress INTEGER DEFAULT 0,
                start_date TEXT DEFAULT NULL
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS task_categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL
            )"""
        )
        ensureDefaultTaskCategories(db)
        ensureTransactionCategoriesTable(db)
        seedDefaultTransactionCategories(db)

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS classes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                subject_name TEXT NOT NULL,
                room TEXT DEFAULT '',
                teacher TEXT DEFAULT '',
                day_of_week INTEGER NOT NULL,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                color_hex TEXT DEFAULT '#2563EB',
                class_type TEXT DEFAULT 'LECTURE',
                building_or_link TEXT DEFAULT ''
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS attendance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_name TEXT UNIQUE NOT NULL,
                attended_classes INTEGER DEFAULT 0,
                total_classes INTEGER DEFAULT 0
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS attendance_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                subjectName TEXT NOT NULL,
                date TEXT NOT NULL,
                dayOfWeek TEXT NOT NULL,
                status TEXT NOT NULL,
                UNIQUE(subjectName, date) ON CONFLICT REPLACE
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS holiday_records (
                date TEXT PRIMARY KEY NOT NULL,
                profile_id INTEGER DEFAULT 1,
                title TEXT NOT NULL DEFAULT 'Holiday'
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS student_subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                name TEXT NOT NULL,
                code TEXT DEFAULT '',
                color_hex TEXT DEFAULT '#2563EB',
                notes TEXT DEFAULT '',
                credits INTEGER DEFAULT 3,
                instructor_name TEXT DEFAULT '',
                instructor_email TEXT DEFAULT '',
                instructor_phone TEXT DEFAULT '',
                syllabus_text TEXT DEFAULT '',
                syllabus_path TEXT DEFAULT ''
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS subject_materials (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                type TEXT NOT NULL,
                file_path TEXT NOT NULL,
                created_at TEXT NOT NULL,
                notes TEXT DEFAULT ''
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS subject_work_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                subject_id INTEGER NOT NULL,
                item_type TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT DEFAULT '',
                start_date TEXT DEFAULT '',
                end_date TEXT DEFAULT '',
                score_or_marks TEXT DEFAULT '',
                status TEXT DEFAULT 'PENDING',
                attachment_path TEXT DEFAULT '',
                exam_type TEXT DEFAULT '',
                attachment_uri TEXT DEFAULT '',
                attachment_name TEXT DEFAULT ''
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS work_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                client_name TEXT DEFAULT '',
                quantity INTEGER DEFAULT 1,
                unit_price REAL DEFAULT 0.0,
                total_price REAL DEFAULT 0.0,
                start_date TEXT DEFAULT '',
                end_date TEXT DEFAULT '',
                status TEXT DEFAULT 'PENDING',
                notes TEXT DEFAULT '',
                attachment_path TEXT DEFAULT ''
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                category TEXT DEFAULT 'General',
                color_hex TEXT DEFAULT '#2563EB',
                updated_at TEXT NOT NULL
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS contacts_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                platform TEXT NOT NULL,
                target_link TEXT NOT NULL,
                notes TEXT DEFAULT '',
                phone_number TEXT DEFAULT '',
                role TEXT DEFAULT 'Classmate',
                scope TEXT DEFAULT 'SHARED'
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS dues_debts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                person_name TEXT NOT NULL,
                phone_or_contact TEXT DEFAULT '',
                amount REAL NOT NULL,
                due_date TEXT DEFAULT '',
                type TEXT NOT NULL,
                status TEXT DEFAULT 'UNPAID',
                notes TEXT DEFAULT '',
                created_at TEXT DEFAULT ''
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                slotNumber INTEGER NOT NULL,
                name TEXT NOT NULL,
                email TEXT NOT NULL,
                passwordHash TEXT NOT NULL,
                institution TEXT DEFAULT '',
                department TEXT DEFAULT '',
                semester TEXT DEFAULT '',
                avatarUri TEXT,
                rememberEmail INTEGER DEFAULT 0
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS class_types (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                name TEXT NOT NULL,
                is_default INTEGER DEFAULT 0
            )"""
        )

        seedInitialData(db)
    }

    fun dropAllTables(db: SQLiteDatabase) {
        val tables = listOf(
            "cards", "transactions", "currency_rates", "tasks", "task_categories",
            "transaction_categories", "classes", "attendance", "attendance_records", "holiday_records", "student_subjects",
            "subject_materials", "subject_work_items", "work_orders", "notes",
            "contacts_groups", "wallet_buckets", "subscriptions", "dues_debts", "app_settings", "profiles"
        )
        for (table in tables) {
            try {
                db.execSQL("DROP TABLE IF EXISTS $table")
            } catch (_: Exception) {}
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            if (oldVersion < 3) {
                dropAllTables(db)
                onCreate(db)
            } else {
                if (oldVersion < 5) {
                    // Completely erase any legacy default/demo/seed categories, buckets, subscriptions, and transactions
                    try {
                        db.execSQL("DELETE FROM wallet_buckets")
                        db.execSQL("DELETE FROM subscriptions")
                        db.execSQL("DELETE FROM transactions")
                        db.execSQL("DELETE FROM cards")
                        appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .remove("cash_funds_usd")
                            .putBoolean("clean_zero_state_v5_applied", true)
                            .apply()
                    } catch (_: Exception) {}
                }
                if (oldVersion < 6) {
                    try {
                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS attendance_records (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                subjectName TEXT NOT NULL,
                                date TEXT NOT NULL,
                                dayOfWeek TEXT NOT NULL,
                                status TEXT NOT NULL,
                                UNIQUE(subjectName, date) ON CONFLICT REPLACE
                            )"""
                        )
                    } catch (_: Exception) {}
                }
                if (oldVersion < 7) {
                    try {
                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS holiday_records (
                                date TEXT PRIMARY KEY NOT NULL,
                                title TEXT NOT NULL DEFAULT 'Holiday'
                            )"""
                        )
                    } catch (_: Exception) {}
                }
                if (oldVersion < 8) {
                    try {
                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS profiles (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                slotNumber INTEGER NOT NULL,
                                name TEXT NOT NULL,
                                email TEXT NOT NULL,
                                passwordHash TEXT NOT NULL,
                                institution TEXT DEFAULT '',
                                department TEXT DEFAULT '',
                                semester TEXT DEFAULT '',
                                avatarUri TEXT,
                                rememberEmail INTEGER DEFAULT 0
                            )"""
                        )
                    } catch (_: Exception) {}

                    val tablesToAddProfileId = listOf(
                        "cards", "transactions", "wallet_buckets", "subscriptions",
                        "tasks", "classes", "attendance_records", "holiday_records",
                        "student_subjects", "subject_work_items", "notes", "dues_debts"
                    )
                    for (table in tablesToAddProfileId) {
                        try {
                            db.execSQL("ALTER TABLE $table ADD COLUMN profile_id INTEGER DEFAULT 1")
                        } catch (_: Exception) {}
                    }
                }
                if (oldVersion < 9) {
                    try {
                        ensureSubscriptionsTable()
                    } catch (_: Exception) {}
                }
                ensureDefaultCurrencies(db)
            }
        } catch (e: Exception) {
            // Fallback to destructive migration so older local installs don't crash on startup
            try {
                dropAllTables(db)
                onCreate(db)
            } catch (_: Exception) {}
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Fallback to destructive migration so downgraded installs don't crash on startup
        try {
            dropAllTables(db)
            onCreate(db)
        } catch (_: Exception) {}
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS attendance_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    subjectName TEXT NOT NULL,
                    date TEXT NOT NULL,
                    dayOfWeek TEXT NOT NULL,
                    status TEXT NOT NULL,
                    UNIQUE(subjectName, date) ON CONFLICT REPLACE
                )"""
            )
        } catch (_: Exception) {}
        try {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS holiday_records (
                    date TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL DEFAULT 'Holiday'
                )"""
            )
        } catch (_: Exception) {}
        try {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS app_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )"""
            )
        } catch (_: Exception) {}
        try {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    slotNumber INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL,
                    passwordHash TEXT NOT NULL,
                    institution TEXT DEFAULT '',
                    department TEXT DEFAULT '',
                    semester TEXT DEFAULT '',
                    avatarUri TEXT,
                    rememberEmail INTEGER DEFAULT 0
                )"""
            )
        } catch (_: Exception) {}

        // One-time fallback wipe to ensure 100% clean zero state
        val prefs = appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("clean_zero_state_v5_applied", false)) {
            try {
                db.execSQL("DELETE FROM wallet_buckets")
                db.execSQL("DELETE FROM subscriptions")
                db.execSQL("DELETE FROM transactions")
                db.execSQL("DELETE FROM cards")
                prefs.edit()
                    .remove("cash_funds_usd")
                    .putBoolean("clean_zero_state_v5_applied", true)
                    .apply()
            } catch (_: Exception) {}
        }

        // Ensure BDT and modern currencies are populated on every launch without wiping user data
        ensureDefaultCurrencies(db)

        // One-time migration to completely unscale any legacy USD-divided currency values back to 100% raw local currency
        if (!prefs.getBoolean("raw_currency_unscaled_v1", false)) {
            try {
                val cBuckets = db.rawQuery("SELECT id, balance FROM wallet_buckets", null)
                cBuckets.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val bal = it.getDouble(1)
                        if (bal > 0) {
                            val unscaled = Math.round(bal * 123.22 * 100.0) / 100.0
                            db.execSQL("UPDATE wallet_buckets SET balance = ? WHERE id = ?", arrayOf(unscaled, id))
                        }
                    }
                }
                val cTx = db.rawQuery("SELECT id, amount FROM transactions", null)
                cTx.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val amt = it.getDouble(1)
                        if (amt > 0) {
                            val unscaled = Math.round(amt * 123.22 * 100.0) / 100.0
                            db.execSQL("UPDATE transactions SET amount = ? WHERE id = ?", arrayOf(unscaled, id))
                        }
                    }
                }
                val cCards = db.rawQuery("SELECT id, balance FROM cards", null)
                cCards.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val bal = it.getDouble(1)
                        if (bal > 0) {
                            val unscaled = Math.round(bal * 123.22 * 100.0) / 100.0
                            db.execSQL("UPDATE cards SET balance = ? WHERE id = ?", arrayOf(unscaled, id))
                        }
                    }
                }
            } catch (_: Exception) {}
            prefs.edit().putBoolean("raw_currency_unscaled_v1", true).apply()
        }

        // Non-destructive migrations for upgraded columns
        ensureColumnExists(db, "classes", "class_type", "TEXT DEFAULT 'LECTURE'")
        ensureColumnExists(db, "classes", "building_or_link", "TEXT DEFAULT ''")
        ensureColumnExists(db, "student_subjects", "credits", "INTEGER DEFAULT 3")
        ensureColumnExists(db, "student_subjects", "instructor_name", "TEXT DEFAULT ''")
        ensureColumnExists(db, "student_subjects", "instructor_email", "TEXT DEFAULT ''")
        ensureColumnExists(db, "student_subjects", "instructor_phone", "TEXT DEFAULT ''")
        ensureColumnExists(db, "student_subjects", "syllabus_text", "TEXT DEFAULT ''")
        ensureColumnExists(db, "student_subjects", "syllabus_path", "TEXT DEFAULT ''")
        ensureColumnExists(db, "subject_work_items", "exam_type", "TEXT DEFAULT ''")
        ensureColumnExists(db, "subject_work_items", "attachment_uri", "TEXT DEFAULT ''")
        ensureColumnExists(db, "subject_work_items", "attachment_name", "TEXT DEFAULT ''")
        ensureColumnExists(db, "contacts_groups", "phone_number", "TEXT DEFAULT ''")
        ensureColumnExists(db, "contacts_groups", "role", "TEXT DEFAULT 'Classmate'")
        ensureColumnExists(db, "contacts_groups", "scope", "TEXT DEFAULT 'SHARED'")
        ensureColumnExists(db, "tasks", "start_date", "TEXT DEFAULT NULL")

        try {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS subject_materials (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    subject_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    type TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    notes TEXT DEFAULT ''
                )"""
            )
        } catch (_: Exception) {}

        ensureDefaultTaskCategories(db)
        ensureTransactionCategoriesTable(db)
        seedDefaultTransactionCategories(db)

        // Wipe out any legacy pre-populated/seeded transaction categories so categories start at 0
        try {
            db.execSQL("DELETE FROM transaction_categories WHERE is_default = 1")
            db.execSQL("DELETE FROM transaction_categories WHERE name IN ('Food', 'Transport', 'Bills', 'Freelance', 'Salary', 'Shopping', 'Education', 'Entertainment', 'Work Orders', 'Other')")
        } catch (_: Exception) {}
        val catPrefs = appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        if (!catPrefs.getBoolean("clean_zero_categories_wiped_v2", false)) {
            try {
                db.execSQL("DELETE FROM transaction_categories WHERE is_default = 1")
                db.execSQL("DELETE FROM transaction_categories WHERE name IN ('Food', 'Transport', 'Bills', 'Freelance', 'Salary', 'Shopping', 'Education', 'Entertainment', 'Work Orders', 'Other')")
                catPrefs.edit().putBoolean("clean_zero_categories_wiped_v2", true).apply()
            } catch (_: Exception) {}
        }

        // Ensure wallet_buckets and subscriptions tables exist
        try {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS wallet_buckets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    icon TEXT DEFAULT 'wallet',
                    color_hex TEXT DEFAULT '#0284C7',
                    connected_accounts TEXT DEFAULT '',
                    balance REAL DEFAULT 0.0,
                    note TEXT DEFAULT ''
                )"""
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS subscriptions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    service_name TEXT NOT NULL,
                    cost REAL NOT NULL,
                    currency TEXT DEFAULT 'BDT',
                    billing_cycle TEXT DEFAULT 'MONTHLY',
                    renewal_date TEXT NOT NULL,
                    bucket_id INTEGER DEFAULT 0,
                    bucket_name TEXT DEFAULT '',
                    category TEXT DEFAULT 'Entertainment',
                    icon TEXT DEFAULT 'subscription',
                    is_active INTEGER DEFAULT 1,
                    auto_log INTEGER DEFAULT 1
                )"""
            )
        } catch (_: Exception) {}

        ensureColumnExists(db, "subscriptions", "auto_log", "INTEGER DEFAULT 1")
        ensureColumnExists(db, "transactions", "bucket_id", "INTEGER DEFAULT 0")

        addColumnIfNotExists(db, "subscriptions", "profile_id", "INTEGER DEFAULT 1")
        addColumnIfNotExists(db, "subscriptions", "profileId", "INTEGER DEFAULT 1")
        addColumnIfNotExists(db, "subscriptions", "is_range", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, "subscriptions", "start_day", "INTEGER DEFAULT 1")
        addColumnIfNotExists(db, "subscriptions", "end_day", "INTEGER DEFAULT 1")
        addColumnIfNotExists(db, "subscriptions", "due_day", "INTEGER DEFAULT 1")
        addColumnIfNotExists(db, "subscriptions", "linked_account_id", "INTEGER DEFAULT NULL")
        addColumnIfNotExists(db, "subscriptions", "last_paid_month_year", "TEXT DEFAULT ''")
        addColumnIfNotExists(db, "subscriptions", "billing_cycle", "TEXT DEFAULT 'MONTHLY'")
        addColumnIfNotExists(db, "subscriptions", "next_renewal_date", "TEXT DEFAULT ''")
        addColumnIfNotExists(db, "subscriptions", "name", "TEXT DEFAULT ''")
        addColumnIfNotExists(db, "subscriptions", "amount", "REAL DEFAULT 0.0")
        addColumnIfNotExists(db, "subscriptions", "service_name", "TEXT DEFAULT ''")
        addColumnIfNotExists(db, "subscriptions", "cost", "REAL DEFAULT 0.0")
        addColumnIfNotExists(db, "subscriptions", "renewal_date", "TEXT DEFAULT ''")

        ensureDuesDebtsTable(db)
        migrateLegacyTransactionsAndAccounts(db)

        // Clean up any legacy test/mock data or hardcoded seed data
        try {
            db.execSQL("DELETE FROM subscriptions WHERE service_name IN ('YouTube Premium', 'ChatGPT Plus', 'Spotify Student')")
            db.execSQL("DELETE FROM cards WHERE holder_name = 'John Doe' OR card_name = 'Main Wallet'")
            db.execSQL("DELETE FROM work_orders WHERE client_name = 'Alex Rivera' OR title LIKE '%E-Commerce%'")
            db.execSQL("DELETE FROM notes WHERE title LIKE '%Cheat Sheet%'")
            db.execSQL("DELETE FROM tasks WHERE title LIKE '%Review Core Architecture%' OR title LIKE '%Master Android%'")
            db.execSQL("DELETE FROM classes WHERE subject_name LIKE '%Advanced Algorithms%' OR teacher LIKE '%David Miller%'")
            db.execSQL("DELETE FROM attendance WHERE subject_name LIKE '%Advanced Algorithms%'")
            db.execSQL("DELETE FROM student_subjects WHERE name LIKE '%Advanced Algorithms%'")
        } catch (_: Exception) {}
    }

    private fun ensureColumnExists(db: SQLiteDatabase, tableName: String, columnName: String, columnDef: String) {
        try {
            val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
            var exists = false
            cursor.use {
                val nameIdx = it.getColumnIndex("name")
                if (nameIdx >= 0) {
                    while (it.moveToNext()) {
                        if (it.getString(nameIdx).equals(columnName, ignoreCase = true)) {
                            exists = true
                            break
                        }
                    }
                }
            }
            if (!exists) {
                db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDef")
            }
        } catch (_: Exception) {}
    }

    fun addColumnIfNotExists(db: SQLiteDatabase, tableName: String, columnName: String, columnDef: String) {
        ensureColumnExists(db, tableName, columnName, columnDef)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        ensureDefaultCurrencies(db)
        // No personal mock cards, transactions, tasks, classes, or subjects are pre-seeded.
        // The app starts completely clean for the user.
    }

    fun migrateLegacyTransactionsAndAccounts(db: SQLiteDatabase) {
        try {
            val countCursor = db.rawQuery("SELECT COUNT(*) FROM wallet_buckets", null)
            val bucketCount = countCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }

            var legacyCash = 0.0
            val c = db.rawQuery("SELECT value FROM app_settings WHERE key = 'cash_funds_usd'", null)
            c.use {
                if (it.moveToFirst()) {
                    legacyCash = it.getString(0).toDoubleOrNull() ?: 0.0
                }
            }
            if (legacyCash <= 0.0) {
                val prefs = appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
                legacyCash = prefs.getString("cash_funds_usd", null)?.toDoubleOrNull() ?: 0.0
            }

            if (bucketCount == 0 && legacyCash > 0) {
                val cv = ContentValues().apply {
                    put("name", "Cash in Hand")
                    put("icon", "cash")
                    put("color_hex", "#10B981")
                    put("connected_accounts", "Physical Cash")
                    put("balance", legacyCash)
                    put("note", "Converted from legacy cash balance")
                }
                val newId = db.insertWithOnConflict("wallet_buckets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                if (newId > 0) {
                    db.execSQL("UPDATE transactions SET bucket_id = ? WHERE bucket_id = 0", arrayOf(newId))
                    db.execSQL("UPDATE subscriptions SET bucket_id = ?, bucket_name = 'Cash in Hand' WHERE bucket_id = 0", arrayOf(newId))
                }
            } else if (bucketCount > 0) {
                val firstAccCursor = db.rawQuery("SELECT id, name FROM wallet_buckets ORDER BY id ASC LIMIT 1", null)
                var firstId = 0L
                var firstName = ""
                firstAccCursor.use {
                    if (it.moveToFirst()) {
                        firstId = it.getLong(0)
                        firstName = it.getString(1)
                    }
                }
                if (firstId > 0) {
                    db.execSQL("UPDATE transactions SET bucket_id = ? WHERE bucket_id = 0", arrayOf(firstId))
                    db.execSQL("UPDATE subscriptions SET bucket_id = ?, bucket_name = ? WHERE bucket_id = 0", arrayOf(firstId, firstName))
                }
            }

            // Permanently zero out legacy cash_funds_usd
            val clearCv = ContentValues().apply {
                put("key", "cash_funds_usd")
                put("value", "0.0")
            }
            db.insertWithOnConflict("app_settings", null, clearCv, SQLiteDatabase.CONFLICT_REPLACE)
            val prefs = appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("cash_funds_usd").apply()
        } catch (_: Exception) {}
    }

    fun getCashFundsUSD(): Double {
        val db = readableDatabase
        try {
            val c = db.rawQuery("SELECT value FROM app_settings WHERE key = 'cash_funds_usd'", null)
            c.use {
                if (it.moveToFirst()) {
                    return it.getString(0).toDoubleOrNull() ?: 0.0
                }
            }
        } catch (_: Exception) {}
        // Fallback check in SharedPreferences for smooth migration
        val prefs = appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val legacy = prefs.getString("cash_funds_usd", null)?.toDoubleOrNull()
        if (legacy != null) {
            setCashFundsUSD(legacy)
            return legacy
        }
        return 0.0
    }

    fun setCashFundsUSD(usd: Double) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put("key", "cash_funds_usd")
                put("value", String.format(Locale.US, "%.4f", usd))
            }
            db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {}
        // Also persist in SharedPreferences for safety
        appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
            .edit().putString("cash_funds_usd", usd.toString()).apply()
    }

    fun isWorkPortalEnabled(): Boolean {
        val db = readableDatabase
        try {
            val c = db.rawQuery("SELECT value FROM app_settings WHERE key = 'work_portal_enabled'", null)
            c.use {
                if (it.moveToFirst()) {
                    return it.getString(0) == "1"
                }
            }
        } catch (_: Exception) {}
        val prefs = appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("work_portal_enabled", true)
    }

    fun setWorkPortalEnabled(enabled: Boolean) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put("key", "work_portal_enabled")
                put("value", if (enabled) "1" else "0")
            }
            db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {}
        appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("work_portal_enabled", enabled).apply()
    }

    /**
     * Inserts default system currency rates (including BDT - Bangladeshi Taka)
     * covering 28 global currencies if they do not already exist in the database.
     */
    fun ensureDefaultCurrencies(db: SQLiteDatabase) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currencies = listOf(
            Triple("USD", 1.0, "$"),
            Triple("BDT", 123.22, "৳"),
            Triple("EUR", 0.92, "€"),
            Triple("GBP", 0.79, "£"),
            Triple("INR", 83.5, "₹"),
            Triple("SAR", 3.75, "SAR"),
            Triple("CAD", 1.37, "C$"),
            Triple("AUD", 1.52, "A$"),
            Triple("JPY", 153.2, "¥"),
            Triple("AED", 3.67, "AED"),
            Triple("SGD", 1.35, "S$"),
            Triple("MYR", 4.40, "RM"),
            Triple("PKR", 278.0, "₨"),
            Triple("CHF", 0.88, "CHF"),
            Triple("CNY", 7.24, "¥"),
            Triple("KRW", 1380.0, "₩"),
            Triple("NZD", 1.65, "NZ$"),
            Triple("BRL", 5.60, "R$"),
            Triple("TRY", 34.2, "₺"),
            Triple("ZAR", 17.8, "R"),
            Triple("RUB", 96.5, "₽"),
            Triple("THB", 34.8, "฿"),
            Triple("IDR", 15600.0, "Rp"),
            Triple("KWD", 0.31, "KD"),
            Triple("QAR", 3.64, "QR"),
            Triple("OMR", 0.38, "OMR"),
            Triple("BHD", 0.38, "BD"),
            Triple("EGP", 48.8, "E£"),
            Triple("HKD", 7.78, "HK$"),
            Triple("SEK", 10.6, "kr"),
            Triple("NOK", 10.8, "kr"),
            Triple("DKK", 6.89, "kr"),
            Triple("PLN", 4.02, "zł"),
            Triple("PHP", 58.2, "₱"),
            Triple("VND", 25300.0, "₫"),
            Triple("MXN", 19.8, "Mex$"),
            Triple("NGN", 1650.0, "₦"),
            Triple("KES", 129.0, "KSh"),
            Triple("LKR", 293.0, "Rs"),
            Triple("NPR", 135.0, "Rs")
        )
        for ((code, rate, symbol) in currencies) {
            val cv = ContentValues().apply {
                put("code", code)
                put("rate", rate)
                put("symbol", symbol)
                put("last_updated", today)
            }
            db.insertWithOnConflict("currency_rates", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun getTaskCategories(): List<String> {
        val categories = mutableListOf<String>()
        val db = readableDatabase
        try {
            val cursor = db.rawQuery("SELECT name FROM task_categories ORDER BY name ASC", null)
            cursor.use {
                while (it.moveToNext()) {
                    categories.add(it.getString(0))
                }
            }
        } catch (_: Exception) {}
        return categories
    }

    fun addTaskCategory(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", trimmed)
        }
        val id = db.insertWithOnConflict("task_categories", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        return id != -1L
    }

    fun deleteTaskCategory(name: String): Boolean {
        val db = writableDatabase
        val deleted = db.delete("task_categories", "name = ?", arrayOf(name))
        return deleted > 0
    }

    fun ensureDefaultTaskCategories(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS task_categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL
                )"""
            )
            // Purge any legacy preset categories so categories are 100% user-created & dynamic
            db.execSQL("DELETE FROM task_categories WHERE name IN ('Assignments', 'Work Projects', 'Personal', 'Exams')")
        } catch (_: Exception) {}
    }

    fun ensureDefaultBucketsAndSubscriptions(db: SQLiteDatabase) {
        // Clean start: No hardcoded buckets or mock subscriptions are seeded.
        // The user starts with 0 buckets and 0 subscriptions.
    }


    /**
     * Wipes all user data across all modules, resetting the app to a clean slate.
     */
    fun clearAllUserData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM cards")
            db.execSQL("DELETE FROM transactions")
            db.execSQL("DELETE FROM transaction_categories")
            db.execSQL("DELETE FROM tasks")
            db.execSQL("DELETE FROM classes")
            db.execSQL("DELETE FROM attendance")
            db.execSQL("DELETE FROM attendance_records")
            db.execSQL("DELETE FROM holiday_records")
            db.execSQL("DELETE FROM student_subjects")
            db.execSQL("DELETE FROM subject_work_items")
            db.execSQL("DELETE FROM subject_materials")
            db.execSQL("DELETE FROM work_orders")
            db.execSQL("DELETE FROM notes")
            db.execSQL("DELETE FROM contacts_groups")
            db.execSQL("DELETE FROM app_settings")
            appContext.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
                .edit().remove("cash_funds_usd").apply()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Seeds sample demo data for presentation/preview if explicitly requested by the user.
     */
    fun seedDemoData() {
        val db = writableDatabase
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        db.beginTransaction()
        try {
            // Sample Cards
            db.execSQL(
                """INSERT INTO cards (card_name, bank_name, card_number, holder_name, card_type, color_hex, balance)
                   VALUES ('Personal Debit', 'Preferred Bank', '•••• 4128', 'Primary Account', 'VISA', '#1E293B', 2500.00)"""
            )
            db.execSQL(
                """INSERT INTO cards (card_name, bank_name, card_number, holder_name, card_type, color_hex, balance)
                   VALUES ('Virtual Card', 'Online Pay', '•••• 8832', 'Online Purchases', 'VIRTUAL', '#7C3AED', 450.00)"""
            )

            // Sample Transactions
            db.execSQL(
                """INSERT INTO transactions (title, amount, type, category, date, card_id, currency, notes)
                   VALUES ('Monthly Earnings', 3000.0, 'INCOME', 'Salary', '$today', 1, 'USD', 'Primary income')"""
            )
            db.execSQL(
                """INSERT INTO transactions (title, amount, type, category, date, card_id, currency, notes)
                   VALUES ('Study Books', 75.0, 'EXPENSE', 'Education', '$today', 1, 'USD', 'Course textbooks')"""
            )

            // Sample Tasks
            db.execSQL(
                """INSERT INTO tasks (title, description, category, due_date, priority, is_completed, is_goal, progress)
                   VALUES ('Review chapter notes', 'Active recall session for upcoming quiz', 'Study', '$today', 'HIGH', 0, 0, 0)"""
            )
            db.execSQL(
                """INSERT INTO tasks (title, description, category, due_date, priority, is_completed, is_goal, progress)
                   VALUES ('Complete project milestone', 'Finish mobile app module and tests', 'Goals', '2026-12-31', 'HIGH', 0, 1, 50)"""
            )

            // Sample Classes
            db.execSQL(
                """INSERT INTO classes (subject_name, room, teacher, day_of_week, start_time, end_time, color_hex)
                   VALUES ('Computer Science', 'Hall B-201', 'Prof. Smith', 1, '10:00 AM', '11:30 AM', '#2563EB')"""
            )

            // Sample Attendance
            db.execSQL(
                """INSERT INTO attendance (subject_name, attended_classes, total_classes)
                   VALUES ('Computer Science', 18, 20)"""
            )

            // Sample Subject
            db.execSQL(
                """INSERT INTO student_subjects (name, code, color_hex, notes)
                   VALUES ('Computer Science', 'CS-204', '#0D9488', 'Focus: Mobile architecture & databases')"""
            )
            db.execSQL(
                """INSERT INTO subject_work_items (subject_id, item_type, title, description, start_date, end_date, score_or_marks, status, attachment_path)
                   VALUES (1, 'HOMEWORK', 'Database Lab Assignment', 'Design schema migrations', '$today', '$today', '', 'PENDING', '')"""
            )

            // Sample Work Order
            db.execSQL(
                """INSERT INTO work_orders (title, client_name, quantity, unit_price, total_price, start_date, end_date, status, notes, attachment_path)
                   VALUES ('Brochure Print Run', 'Client Order', 25, 2.00, 50.00, '$today', '$today', 'IN_PROGRESS', 'Color double-sided print', '')"""
            )

            // Sample Note
            db.execSQL(
                """INSERT INTO notes (title, content, category, color_hex, updated_at)
                   VALUES ('Quick Reminder', 'DuoRhythm brings finance, schedule, and tasks together.', 'General', '#2563EB', '$today')"""
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ==========================================
    // CARDS & WALLET CRUD
    // ==========================================
    fun getAllCards(profileId: Long = getActiveProfileId()): List<CardItem> {
        val list = mutableListOf<CardItem>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM cards WHERE profile_id = ? OR profile_id = 0 OR profile_id IS NULL ORDER BY id ASC",
                arrayOf(profileId.toString())
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        CardItem(
                            id = it.safeGetLong("id"),
                            profileId = it.safeGetLong("profile_id", "profileId", profileId),
                            cardName = it.safeGetString("card_name", "name", "Account"),
                            bankName = it.safeGetString("bank_name", default = ""),
                            cardNumber = it.safeGetString("card_number", default = ""),
                            holderName = it.safeGetString("holder_name", default = ""),
                            cardType = it.safeGetString("card_type", "type", "CASH"),
                            colorHex = it.safeGetString("color_hex", default = "#1E293B"),
                            balance = it.safeGetDouble("balance", default = 0.0)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading cards table", e)
        }
        return list
    }

    fun getCards(profileId: Long = getActiveProfileId()): List<CardItem> = getAllCards(profileId)

    fun insertCard(card: CardItem): Long {
        val db = writableDatabase
        val pId = if (card.profileId > 0) card.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("profile_id", pId)
            put("card_name", card.cardName)
            put("bank_name", card.bankName)
            put("card_number", card.cardNumber)
            put("holder_name", card.holderName)
            put("card_type", card.cardType)
            put("color_hex", card.colorHex)
            put("balance", card.balance)
        }
        return db.insertWithOnConflict("cards", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateCardBalance(cardId: Long, newBalance: Double): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("balance", newBalance)
        }
        return db.update("cards", cv, "id = ?", arrayOf(cardId.toString()))
    }

    fun deleteCard(cardId: Long): Int {
        val db = writableDatabase
        return db.delete("cards", "id = ?", arrayOf(cardId.toString()))
    }

    // ==========================================
    // TRANSACTIONS CRUD
    // ==========================================
    fun getAllTransactions(typeFilter: String? = null, cardIdFilter: Long? = null, bucketIdFilter: Long? = null, profileId: Long = getActiveProfileId()): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        val db = readableDatabase
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        clauses.add("(profile_id = ? OR profile_id = 0 OR profile_id IS NULL)")
        args.add(profileId.toString())

        if (!typeFilter.isNullOrEmpty() && typeFilter != "ALL") {
            clauses.add("type = ?")
            args.add(typeFilter)
        }
        if (cardIdFilter != null && cardIdFilter > 0) {
            clauses.add("card_id = ?")
            args.add(cardIdFilter.toString())
        }
        if (bucketIdFilter != null && bucketIdFilter > 0) {
            clauses.add("bucket_id = ?")
            args.add(bucketIdFilter.toString())
        }

        try {
            val where = if (clauses.isNotEmpty()) "WHERE " + clauses.joinToString(" AND ") else ""
            val cursor = db.rawQuery("SELECT * FROM transactions $where ORDER BY date DESC, id DESC", args.toTypedArray())
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        TransactionItem(
                            id = it.safeGetLong("id"),
                            profileId = it.safeGetLong("profile_id", "profileId", profileId),
                            title = it.safeGetString("title", default = ""),
                            amount = it.safeGetDouble("amount", default = 0.0),
                            type = it.safeGetString("type", default = "EXPENSE"),
                            category = it.safeGetString("category", default = "General"),
                            date = it.safeGetString("date", default = ""),
                            cardId = it.safeGetLong("card_id", default = 0L),
                            currency = it.safeGetString("currency", default = "USD"),
                            notes = it.safeGetString("notes", default = ""),
                            bucketId = it.safeGetLong("bucket_id", default = 0L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading transactions table", e)
        }
        return list
    }

    fun insertTransaction(tx: TransactionItem): Long {
        val db = writableDatabase
        val pId = if (tx.profileId > 0) tx.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("profile_id", pId)
            put("title", tx.title)
            put("amount", tx.amount)
            put("type", tx.type)
            put("category", tx.category)
            put("date", tx.date)
            put("card_id", tx.cardId)
            put("currency", tx.currency)
            put("notes", tx.notes)
            put("bucket_id", tx.bucketId)
        }
        val id = db.insertWithOnConflict("transactions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        val delta = if (tx.type == "INCOME") tx.amount else -tx.amount
        var targetBucketId = tx.bucketId
        if (targetBucketId <= 0L) {
            val c = db.rawQuery("SELECT id FROM wallet_buckets ORDER BY id ASC LIMIT 1", null)
            c.use {
                if (it.moveToFirst()) targetBucketId = it.getLong(0)
            }
            if (targetBucketId > 0L) {
                db.execSQL("UPDATE transactions SET bucket_id = ? WHERE id = ?", arrayOf(targetBucketId, id))
            }
        }
        if (targetBucketId > 0L) {
            db.execSQL("UPDATE wallet_buckets SET balance = balance + ? WHERE id = ?", arrayOf(delta, targetBucketId))
        }
        transactionUpdateFlow.tryEmit(Unit)
        return id
    }

    val transactionUpdateFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    fun getMonthlyTransactions(profileId: Long, monthYear: String): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM transactions WHERE (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) AND date LIKE ? ORDER BY date DESC, id DESC",
                arrayOf(profileId.toString(), "$monthYear%")
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        TransactionItem(
                            id = it.safeGetLong("id"),
                            profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                            title = it.safeGetString("title"),
                            amount = it.safeGetDouble("amount"),
                            type = it.safeGetString("type", default = "EXPENSE"),
                            category = it.safeGetString("category", default = "General"),
                            date = it.safeGetString("date"),
                            cardId = it.safeGetLong("card_id", "cardId"),
                            currency = it.safeGetString("currency", default = "USD"),
                            notes = it.safeGetString("notes"),
                            bucketId = it.safeGetLong("bucket_id", "bucketId")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading monthly transactions for profile $profileId", e)
        }
        return list
    }

    fun getMonthlyTransactionsFlow(profileId: Long, monthYear: String): kotlinx.coroutines.flow.Flow<List<TransactionItem>> = kotlinx.coroutines.flow.flow {
        try {
            emit(getMonthlyTransactions(profileId, monthYear))
        } catch (e: Throwable) {
            e.printStackTrace()
            emit(emptyList())
        }
        transactionUpdateFlow.collect {
            try {
                emit(getMonthlyTransactions(profileId, monthYear))
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun deleteTransaction(txId: Long): Int {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT amount, type, card_id, bucket_id FROM transactions WHERE id = ?", arrayOf(txId.toString()))
        cursor.use {
            if (it.moveToFirst()) {
                val amt = it.getDouble(0)
                val type = it.getString(1)
                val bIdCol = it.getColumnIndex("bucket_id")
                val bucketId = if (bIdCol >= 0) it.getLong(bIdCol) else 0L
                val delta = if (type == "INCOME") -amt else amt
                if (bucketId > 0) {
                    db.execSQL("UPDATE wallet_buckets SET balance = balance + ? WHERE id = ?", arrayOf(delta, bucketId))
                }
            }
        }
        val count = db.delete("transactions", "id = ?", arrayOf(txId.toString()))
        transactionUpdateFlow.tryEmit(Unit)
        return count
    }

    fun updateTransaction(tx: TransactionItem): Int {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT amount, type, card_id, bucket_id FROM transactions WHERE id = ?", arrayOf(tx.id.toString()))
        cursor.use {
            if (it.moveToFirst()) {
                val oldAmt = it.getDouble(0)
                val oldType = it.getString(1)
                val bIdCol = it.getColumnIndex("bucket_id")
                val oldBucketId = if (bIdCol >= 0) it.getLong(bIdCol) else 0L

                // 1. Revert old transaction effect on old bucket
                val revertDelta = if (oldType == "INCOME") -oldAmt else oldAmt
                if (oldBucketId > 0) {
                    db.execSQL("UPDATE wallet_buckets SET balance = balance + ? WHERE id = ?", arrayOf(revertDelta, oldBucketId))
                }

                // 2. Apply new transaction effect on new bucket
                val targetBucketId = if (tx.bucketId > 0) tx.bucketId else oldBucketId
                val applyDelta = if (tx.type == "INCOME") tx.amount else -tx.amount
                if (targetBucketId > 0) {
                    db.execSQL("UPDATE wallet_buckets SET balance = balance + ? WHERE id = ?", arrayOf(applyDelta, targetBucketId))
                }
            }
        }
        val cv = ContentValues().apply {
            put("title", tx.title)
            put("amount", tx.amount)
            put("type", tx.type)
            put("category", tx.category)
            put("date", tx.date)
            put("card_id", tx.cardId)
            put("currency", tx.currency)
            put("notes", tx.notes)
            put("bucket_id", tx.bucketId)
        }
        val count = db.update("transactions", cv, "id = ?", arrayOf(tx.id.toString()))
        transactionUpdateFlow.tryEmit(Unit)
        return count
    }

    fun clearAllTransactions(): Int {
        val db = writableDatabase
        val count = db.delete("transactions", null, null)
        transactionUpdateFlow.tryEmit(Unit)
        return count
    }

    // ==========================================
    // DYNAMIC TRANSACTION CATEGORIES CRUD
    // ==========================================
    fun ensureTransactionCategoriesTable(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS transaction_categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    type TEXT DEFAULT 'BOTH',
                    is_default INTEGER DEFAULT 0,
                    color_hex TEXT DEFAULT '#0284C7',
                    icon TEXT DEFAULT 'category'
                )"""
            )
            ensureColumnExists(db, "transaction_categories", "color_hex", "TEXT DEFAULT '#0284C7'")
            ensureColumnExists(db, "transaction_categories", "icon", "TEXT DEFAULT 'category'")
        } catch (_: Exception) {}
    }

    fun seedDefaultTransactionCategories(db: SQLiteDatabase) {
        try {
            ensureTransactionCategoriesTable(db)
            val cursor = db.rawQuery("SELECT COUNT(*) FROM transaction_categories", null)
            val count = cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (count == 0) {
                db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Food & Dining', 'ic_food', '#F59E0B', 'EXPENSE')")
                db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Education', 'ic_book', '#00E5FF', 'EXPENSE')")
                db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Transport', 'ic_bus', '#10B981', 'EXPENSE')")
                db.execSQL("INSERT INTO transaction_categories (name, icon, color, type) VALUES ('Allowance', 'ic_wallet', '#8B5CF6', 'INCOME')")
            }
        } catch (_: Exception) {}
    }

    fun ensureDefaultTransactionCategories(db: SQLiteDatabase) {
        ensureTransactionCategoriesTable(db)
        seedDefaultTransactionCategories(db)
    }

    fun getAllTransactionCategories(typeFilter: String? = null): List<TransactionCategoryItem> {
        val list = mutableListOf<TransactionCategoryItem>()
        val db = readableDatabase
        ensureTransactionCategoriesTable(db)
        val query = if (typeFilter != null) {
            "SELECT id, name, type, is_default, color_hex, icon FROM transaction_categories WHERE type = ? OR type = 'BOTH' ORDER BY name ASC"
        } else {
            "SELECT id, name, type, is_default, color_hex, icon FROM transaction_categories ORDER BY name ASC"
        }
        val args = if (typeFilter != null) arrayOf(typeFilter) else null
        try {
            val cursor = db.rawQuery(query, args)
            cursor.use {
                val idCol = it.getColumnIndex("id")
                val nameCol = it.getColumnIndex("name")
                val typeCol = it.getColumnIndex("type")
                val defCol = it.getColumnIndex("is_default")
                val colorCol = it.getColumnIndex("color_hex")
                val iconCol = it.getColumnIndex("icon")
                while (it.moveToNext()) {
                    list.add(
                        TransactionCategoryItem(
                            id = if (idCol >= 0) it.getLong(idCol) else 0L,
                            name = if (nameCol >= 0) it.getString(nameCol) ?: "" else "",
                            type = if (typeCol >= 0) it.getString(typeCol) ?: "BOTH" else "BOTH",
                            isDefault = if (defCol >= 0) it.getInt(defCol) == 1 else false,
                            colorHex = if (colorCol >= 0) it.getString(colorCol) ?: "#0284C7" else "#0284C7",
                            icon = if (iconCol >= 0) it.getString(iconCol) ?: "category" else "category"
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun insertTransactionCategory(
        name: String,
        type: String = "BOTH",
        colorHex: String = "#0284C7",
        icon: String = "category"
    ): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        val db = writableDatabase
        ensureTransactionCategoriesTable(db)
        val cv = ContentValues().apply {
            put("name", trimmed)
            put("type", type)
            put("is_default", 0)
            put("color_hex", colorHex)
            put("icon", icon)
        }
        return db.insertWithOnConflict("transaction_categories", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun updateTransactionCategory(
        id: Long,
        newName: String,
        newType: String? = null,
        newColorHex: String? = null,
        newIcon: String? = null
    ): Int {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return 0
        val db = writableDatabase
        ensureTransactionCategoriesTable(db)
        var oldName: String? = null
        try {
            val cursor = db.rawQuery("SELECT name FROM transaction_categories WHERE id = ?", arrayOf(id.toString()))
            cursor.use {
                if (it.moveToFirst()) {
                    oldName = it.getString(0)
                }
            }
        } catch (_: Exception) {}

        val cv = ContentValues().apply {
            put("name", trimmed)
            if (newType != null) put("type", newType)
            if (newColorHex != null) put("color_hex", newColorHex)
            if (newIcon != null) put("icon", newIcon)
        }
        val count = db.update("transaction_categories", cv, "id = ?", arrayOf(id.toString()))
        if (count > 0 && oldName != null && oldName != trimmed) {
            try {
                val txCv = ContentValues().apply {
                    put("category", trimmed)
                }
                db.update("transactions", txCv, "category = ?", arrayOf(oldName))
            } catch (_: Exception) {}
        }
        return count
    }

    fun deleteTransactionCategory(id: Long): Int {
        val db = writableDatabase
        ensureTransactionCategoriesTable(db)
        return db.delete("transaction_categories", "id = ?", arrayOf(id.toString()))
    }

    fun getTransactionCountByCategory(categoryName: String): Int {
        val db = readableDatabase
        return try {
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE LOWER(category) = LOWER(?)",
                arrayOf(categoryName.trim())
            )
            cursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (_: Exception) {
            0
        }
    }

    fun deleteCategoryAndReassignTransactions(
        id: Long,
        categoryName: String,
        fallbackCategory: String = "Uncategorized"
    ): Boolean {
        val db = writableDatabase
        ensureTransactionCategoriesTable(db)
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put("category", fallbackCategory)
            }
            db.update("transactions", cv, "LOWER(category) = LOWER(?)", arrayOf(categoryName.trim()))
            db.delete("transaction_categories", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            db.endTransaction()
        }
    }

    fun getTransactionCategoryByName(name: String): TransactionCategoryItem? {
        val db = readableDatabase
        ensureTransactionCategoriesTable(db)
        try {
            val cursor = db.rawQuery(
                "SELECT id, name, type, is_default, color_hex, icon FROM transaction_categories WHERE LOWER(name) = LOWER(?)",
                arrayOf(name.trim())
            )
            cursor.use {
                if (it.moveToFirst()) {
                    return TransactionCategoryItem(
                        id = it.safeGetLong("id"),
                        name = it.safeGetString("name"),
                        type = it.safeGetString("type", default = "BOTH"),
                        isDefault = it.safeGetBoolean("is_default", "isDefault"),
                        colorHex = it.safeGetString("color_hex", "colorHex", default = "#0284C7"),
                        icon = it.safeGetString("icon", default = "category")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading transaction category $name", e)
        }
        return null
    }

    // ==========================================
    // WALLET BUCKETS CRUD
    // ==========================================
    fun getAllWalletBuckets(profileId: Long = getActiveProfileId()): List<WalletBucketItem> {
        val list = mutableListOf<WalletBucketItem>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM wallet_buckets WHERE (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) ORDER BY id ASC", arrayOf(profileId.toString()))
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        WalletBucketItem(
                            id = it.safeGetLong("id"),
                            profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                            name = it.safeGetString("name"),
                            icon = it.safeGetString("icon", default = "wallet"),
                            colorHex = it.safeGetString("color_hex", "colorHex", default = "#0284C7"),
                            connectedAccounts = it.safeGetString("connected_accounts", "connectedAccounts"),
                            balance = it.safeGetDouble("balance"),
                            note = it.safeGetString("note")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading wallet buckets for profile $profileId", e)
        }
        return list
    }

    fun getWalletBucketById(id: Long): WalletBucketItem? {
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM wallet_buckets WHERE id = ?", arrayOf(id.toString()))
            cursor.use {
                if (it.moveToFirst()) {
                    return WalletBucketItem(
                        id = it.safeGetLong("id", default = id),
                        profileId = it.safeGetLong("profile_id", "profileId", default = 1L),
                        name = it.safeGetString("name"),
                        icon = it.safeGetString("icon", default = "wallet"),
                        colorHex = it.safeGetString("color_hex", "colorHex", default = "#0284C7"),
                        connectedAccounts = it.safeGetString("connected_accounts", "connectedAccounts"),
                        balance = it.safeGetDouble("balance"),
                        note = it.safeGetString("note")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading wallet bucket $id", e)
        }
        return null
    }

    fun insertWalletBucket(bucket: WalletBucketItem): Long {
        val db = writableDatabase
        val pId = if (bucket.profileId > 0) bucket.profileId else getActiveProfileId()
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM wallet_buckets WHERE profile_id = ?", arrayOf(pId.toString()))
        var count = 0
        countCursor.use { if (it.moveToFirst()) count = it.getInt(0) }
        if (count >= 5) return -1L

        val cv = ContentValues().apply {
            put("profile_id", pId)
            put("name", bucket.name)
            put("icon", bucket.icon)
            put("color_hex", bucket.colorHex)
            put("connected_accounts", bucket.connectedAccounts)
            put("balance", bucket.balance)
            put("note", bucket.note)
        }
        return db.insertWithOnConflict("wallet_buckets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateWalletBucket(bucket: WalletBucketItem): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", bucket.name)
            put("icon", bucket.icon)
            put("color_hex", bucket.colorHex)
            put("connected_accounts", bucket.connectedAccounts)
            put("balance", bucket.balance)
            put("note", bucket.note)
        }
        return db.update("wallet_buckets", cv, "id = ?", arrayOf(bucket.id.toString()))
    }

    fun updateBucketBalance(bucketId: Long, newBalance: Double): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("balance", newBalance)
        }
        return db.update("wallet_buckets", cv, "id = ?", arrayOf(bucketId.toString()))
    }

    fun deleteWalletBucket(bucketId: Long): Int {
        val bucket = getWalletBucketById(bucketId)
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Delete all transactions associated with this bucket / category
            if (bucket != null) {
                db.delete("transactions", "bucket_id = ? OR category = ?", arrayOf(bucketId.toString(), bucket.name))
                // Clear bucket link from subscriptions
                val cv = ContentValues().apply {
                    put("bucket_id", 0)
                    put("bucket_name", "")
                }
                db.update("subscriptions", cv, "bucket_id = ? OR bucket_name = ?", arrayOf(bucketId.toString(), bucket.name))
            } else {
                db.delete("transactions", "bucket_id = ?", arrayOf(bucketId.toString()))
            }
            val deleted = db.delete("wallet_buckets", "id = ?", arrayOf(bucketId.toString()))
            db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    fun getTotalBucketBalance(profileId: Long = getActiveProfileId()): Double {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT SUM(balance) FROM wallet_buckets WHERE profile_id = ?", arrayOf(profileId.toString()))
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getDouble(0)
        }
        return 0.0
    }

    /**
     * Pure 100% raw database total balance without any currency exchange division or multiplication.
     */
    fun getTotalBalance(): Double = getTotalBucketBalance()

    /**
     * Pure 100% raw monthly income without currency exchange division.
     */
    fun getMonthlyIncome(): Double {
        val monthPrefix = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        return getTotalIncome(monthPrefix)
    }

    /**
     * Pure 100% raw monthly expenses without currency exchange division.
     */
    fun getMonthlyExpense(): Double {
        val monthPrefix = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        return getTotalExpenses(monthPrefix)
    }

    fun getBucketIncome(bucketId: Long): Double {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT SUM(amount) FROM transactions WHERE bucket_id = ? AND type = 'INCOME'", arrayOf(bucketId.toString()))
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getDouble(0)
        }
        return 0.0
    }

    fun getBucketExpenses(bucketId: Long): Double {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT SUM(amount) FROM transactions WHERE bucket_id = ? AND type = 'EXPENSE'", arrayOf(bucketId.toString()))
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getDouble(0)
        }
        return 0.0
    }

    fun getTransactionsByBucket(bucketId: Long, typeFilter: String? = null): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        try {
            val db = readableDatabase
            val clauses = mutableListOf("bucket_id = ?")
            val args = mutableListOf(bucketId.toString())
            if (!typeFilter.isNullOrEmpty() && typeFilter != "ALL") {
                clauses.add("type = ?")
                args.add(typeFilter)
            }
            val where = "WHERE " + clauses.joinToString(" AND ")
            val cursor = db.rawQuery("SELECT * FROM transactions $where ORDER BY date DESC, id DESC", args.toTypedArray())
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        TransactionItem(
                            id = it.safeGetLong("id"),
                            title = it.safeGetString("title"),
                            amount = it.safeGetDouble("amount"),
                            type = it.safeGetString("type", default = "EXPENSE"),
                            category = it.safeGetString("category", default = "General"),
                            date = it.safeGetString("date"),
                            cardId = it.safeGetLong("card_id", "cardId"),
                            currency = it.safeGetString("currency", default = "USD"),
                            notes = it.safeGetString("notes"),
                            bucketId = it.safeGetLong("bucket_id", "bucketId", default = bucketId),
                            profileId = it.safeGetLong("profile_id", "profileId", default = 1L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading transactions for bucket $bucketId", e)
        }
        return list
    }

    // ==========================================
    // RECURRING SUBSCRIPTIONS CRUD
    // ==========================================
    fun getAllSubscriptions(profileId: Long = getActiveProfileId()): List<SubscriptionItem> {
        val list = mutableListOf<SubscriptionItem>()
        try {
            ensureSubscriptionsTable()
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM subscriptions WHERE (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) ORDER BY id ASC", arrayOf(profileId.toString()))
            cursor.use {
                while (it.moveToNext()) {
                    val autoLog = it.safeGetBoolean("auto_log", "autoLog", default = true)
                    val pId = it.safeGetLong("profile_id", "profileId", default = profileId)
                    val sName = it.safeGetString("service_name", "name")
                    val cost = it.safeGetDouble("cost", "amount")
                    var renewal = it.safeGetString("renewal_date", "renewalDate")
                    if (renewal.isBlank()) {
                        val dueDay = it.safeGetInt("dueDay", "due_day", default = 0)
                        if (dueDay > 0) {
                            val d = dueDay.coerceIn(1, 28)
                            val cal = java.util.Calendar.getInstance()
                            renewal = String.format(Locale.US, "%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, d)
                        }
                    }

                    list.add(
                        SubscriptionItem(
                            id = it.safeGetLong("id"),
                            serviceName = sName,
                            cost = cost,
                            currency = it.safeGetString("currency", default = "BDT"),
                            billingCycle = it.safeGetString("billing_cycle", "billingCycle", default = "MONTHLY"),
                            renewalDate = renewal,
                            bucketId = it.safeGetLong("bucket_id", "linkedAccountId"),
                            bucketName = it.safeGetString("bucket_name", "bucketName"),
                            category = it.safeGetString("category", default = "Entertainment"),
                            icon = it.safeGetString("icon", default = "subscription"),
                            isActive = it.safeGetBoolean("is_active", "isActive", default = true),
                            autoLog = autoLog,
                            profileId = pId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading subscriptions for profile $profileId", e)
        }
        return list
    }

    fun insertSubscription(sub: SubscriptionItem): Long {
        ensureSubscriptionsTable()
        val db = writableDatabase
        val pId = if (sub.profileId > 0) sub.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("service_name", sub.serviceName)
            put("name", sub.serviceName)
            put("cost", sub.cost)
            put("amount", sub.cost)
            put("currency", sub.currency)
            put("billing_cycle", sub.billingCycle)
            put("renewal_date", sub.renewalDate)
            put("bucket_id", sub.bucketId)
            put("linkedAccountId", sub.bucketId)
            put("linked_account_id", sub.bucketId)
            put("bucket_name", sub.bucketName)
            put("category", sub.category)
            put("icon", sub.icon)
            put("is_active", if (sub.isActive) 1 else 0)
            put("auto_log", if (sub.autoLog) 1 else 0)
            put("profile_id", pId)
            put("profileId", pId)
        }
        return db.insertWithOnConflict("subscriptions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateSubscription(sub: SubscriptionItem): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("service_name", sub.serviceName)
            put("cost", sub.cost)
            put("currency", sub.currency)
            put("billing_cycle", sub.billingCycle)
            put("renewal_date", sub.renewalDate)
            put("bucket_id", sub.bucketId)
            put("bucket_name", sub.bucketName)
            put("category", sub.category)
            put("icon", sub.icon)
            put("is_active", if (sub.isActive) 1 else 0)
            put("auto_log", if (sub.autoLog) 1 else 0)
        }
        return db.update("subscriptions", cv, "id = ?", arrayOf(sub.id.toString()))
    }

    fun updateSubscriptionRenewalDate(subId: Long, newRenewalDate: String): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("renewal_date", newRenewalDate)
        }
        return db.update("subscriptions", cv, "id = ?", arrayOf(subId.toString()))
    }

    fun deleteSubscription(subId: Long): Int {
        val db = writableDatabase
        return db.delete("subscriptions", "id = ?", arrayOf(subId.toString()))
    }

    fun getTodayTotalExpense(dateStr: String): Double {
        val db = readableDatabase
        val c = db.rawQuery("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date = ?", arrayOf(dateStr))
        c.use {
            return if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0) else 0.0
        }
    }

    fun getTotalIncome(monthPrefix: String? = null, profileId: Long = getActiveProfileId()): Double {
        return try {
            val db = readableDatabase
            val query = if (!monthPrefix.isNullOrEmpty()) {
                "SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND date LIKE ? AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL)"
            } else {
                "SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL)"
            }
            val args = if (!monthPrefix.isNullOrEmpty()) {
                arrayOf("$monthPrefix%", profileId.toString())
            } else {
                arrayOf(profileId.toString())
            }
            val c = db.rawQuery(query, args)
            c.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0) else 0.0
            }
        } catch (_: Exception) {
            0.0
        }
    }

    fun getTotalExpenses(monthPrefix: String? = null, profileId: Long = getActiveProfileId()): Double {
        return try {
            val db = readableDatabase
            val query = if (!monthPrefix.isNullOrEmpty()) {
                "SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date LIKE ? AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL)"
            } else {
                "SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL)"
            }
            val args = if (!monthPrefix.isNullOrEmpty()) {
                arrayOf("$monthPrefix%", profileId.toString())
            } else {
                arrayOf(profileId.toString())
            }
            val c = db.rawQuery(query, args)
            c.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0) else 0.0
            }
        } catch (_: Exception) {
            0.0
        }
    }

    // ==========================================
    // CURRENCY RATES
    // ==========================================
    fun getAllCurrencyRates(): List<CurrencyRate> {
        val list = mutableListOf<CurrencyRate>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM currency_rates", null)
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        CurrencyRate(
                            code = it.safeGetString("code"),
                            rate = it.safeGetDouble("rate"),
                            symbol = it.safeGetString("symbol"),
                            lastUpdated = it.safeGetString("last_updated", "lastUpdated")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading currency rates", e)
        }
        val priorityOrder = listOf(
            "BDT", "USD", "EUR", "GBP", "INR", "JPY",
            "CAD", "AUD", "AED", "SAR", "SGD", "MYR", "PKR"
        )
        return list.sortedWith(compareBy({
            val idx = priorityOrder.indexOf(it.code)
            if (idx >= 0) idx else 999
        }, { it.code }))
    }

    fun updateCurrencyRate(code: String, rate: Double, lastUpdated: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("rate", rate)
            put("last_updated", lastUpdated)
        }
        val affected = db.update("currency_rates", cv, "code = ?", arrayOf(code))
        if (affected == 0) {
            val defaultSymbol = when (code) {
                "BDT" -> "৳"
                "USD" -> "$"
                "EUR" -> "€"
                "GBP" -> "£"
                "INR" -> "₹"
                "JPY" -> "¥"
                "CAD" -> "C$"
                "AUD" -> "A$"
                "AED" -> "AED"
                "SAR" -> "SAR"
                "SGD" -> "S$"
                "MYR" -> "RM"
                "PKR" -> "₨"
                else -> code
            }
            cv.put("code", code)
            cv.put("symbol", defaultSymbol)
            db.insertWithOnConflict("currency_rates", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // ==========================================
    // TASKS (TickTick style)
    // ==========================================
    fun getAllTasks(filter: String = "ALL", isGoal: Boolean? = null, profileId: Long = getActiveProfileId()): List<TaskItem> {
        val list = mutableListOf<TaskItem>()
        try {
            val db = readableDatabase
            val goalClause = when (isGoal) {
                true -> " AND is_goal = 1"
                false -> " AND is_goal = 0"
                null -> ""
            }
            val profileWhere = "(profile_id = ? OR profile_id = 0 OR profile_id IS NULL)"
            val (query, args) = when (filter) {
                "TODAY" -> {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    Pair("SELECT * FROM tasks WHERE due_date = '$today' AND is_completed = 0 AND $profileWhere$goalClause ORDER BY priority DESC, id DESC", arrayOf(profileId.toString()))
                }
                "COMPLETED" -> Pair("SELECT * FROM tasks WHERE is_completed = 1 AND $profileWhere$goalClause ORDER BY id DESC", arrayOf(profileId.toString()))
                "GOALS" -> Pair("SELECT * FROM tasks WHERE is_goal = 1 AND $profileWhere ORDER BY id DESC", arrayOf(profileId.toString()))
                "HIGH" -> Pair("SELECT * FROM tasks WHERE priority = 'HIGH' AND is_completed = 0 AND $profileWhere$goalClause ORDER BY id DESC", arrayOf(profileId.toString()))
                "ALL" -> Pair("SELECT * FROM tasks WHERE $profileWhere$goalClause ORDER BY is_completed ASC, priority DESC, id DESC", arrayOf(profileId.toString()))
                else -> Pair("SELECT * FROM tasks WHERE category = ? AND $profileWhere$goalClause ORDER BY is_completed ASC, priority DESC, id DESC", arrayOf(filter, profileId.toString()))
            }
            val cursor = db.rawQuery(query, args)
            cursor.use {
                while (it.moveToNext()) {
                    val rawDue = it.safeGetString("due_date", "dueDate")
                    val dueDate = if (rawDue.isBlank()) null else rawDue

                    val rawStart = it.safeGetString("start_date", "startDate")
                    val startDate = if (rawStart.isBlank()) null else rawStart
                    val pId = it.safeGetLong("profile_id", "profileId", default = profileId)

                    list.add(
                        TaskItem(
                            id = it.safeGetLong("id"),
                            profileId = pId,
                            title = it.safeGetString("title"),
                            description = it.safeGetString("description"),
                            category = it.safeGetString("category", default = "General"),
                            dueDate = dueDate,
                            priority = it.safeGetString("priority", default = "MEDIUM"),
                            isCompleted = it.safeGetBoolean("is_completed", "isCompleted"),
                            isGoal = it.safeGetBoolean("is_goal", "isGoal"),
                            progress = it.safeGetInt("progress"),
                            startDate = startDate
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading tasks for profile $profileId (filter=$filter)", e)
        }
        return list
    }

    fun getTasks(profileId: Long = getActiveProfileId()): List<TaskItem> = getAllTasks("ALL", null, profileId)

    fun insertTask(task: TaskItem): Long {
        val db = writableDatabase
        val pId = if (task.profileId > 0) task.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("profile_id", pId)
            put("title", task.title)
            put("description", task.description)
            put("category", task.category)
            if (!task.dueDate.isNullOrBlank()) {
                put("due_date", task.dueDate.trim())
            } else {
                putNull("due_date")
            }
            if (!task.startDate.isNullOrBlank()) {
                put("start_date", task.startDate.trim())
            } else {
                putNull("start_date")
            }
            put("priority", task.priority)
            put("is_completed", if (task.isCompleted) 1 else 0)
            put("is_goal", if (task.isGoal) 1 else 0)
            put("progress", task.progress)
        }
        return db.insertWithOnConflict("tasks", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateTaskCompletion(taskId: Long, isCompleted: Boolean) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("is_completed", if (isCompleted) 1 else 0)
        }
        db.update("tasks", cv, "id = ?", arrayOf(taskId.toString()))
    }

    fun updateTaskProgress(taskId: Long, progress: Int) {
        val db = writableDatabase
        val validProgress = progress.coerceIn(0, 100)
        val cv = ContentValues().apply {
            put("progress", validProgress)
            if (validProgress == 100) {
                put("is_completed", 1)
            }
        }
        db.update("tasks", cv, "id = ?", arrayOf(taskId.toString()))
    }

    fun updateTask(task: TaskItem): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", task.title)
            put("description", task.description)
            put("category", task.category)
            if (!task.dueDate.isNullOrBlank()) {
                put("due_date", task.dueDate.trim())
            } else {
                putNull("due_date")
            }
            if (!task.startDate.isNullOrBlank()) {
                put("start_date", task.startDate.trim())
            } else {
                putNull("start_date")
            }
            put("priority", task.priority)
            put("is_completed", if (task.isCompleted) 1 else 0)
            put("is_goal", if (task.isGoal) 1 else 0)
            put("progress", task.progress)
        }
        return db.update("tasks", cv, "id = ?", arrayOf(task.id.toString()))
    }

    fun deleteTask(taskId: Long): Int {
        val db = writableDatabase
        return db.delete("tasks", "id = ?", arrayOf(taskId.toString()))
    }

    // ==========================================
    // SCHEDULE & ATTENDANCE
    // ==========================================
    fun getScheduleForDay(dayOfWeek: Int, profileId: Long = getActiveProfileId()): List<ClassScheduleItem> {
        val list = mutableListOf<ClassScheduleItem>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM classes WHERE (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) AND day_of_week = ? ORDER BY start_time ASC",
                arrayOf(profileId.toString(), dayOfWeek.toString())
            )
            cursor.use {
                while (it.moveToNext()) {
                    val pId = it.safeGetLong("profile_id", "profileId", default = profileId)
                    list.add(
                        ClassScheduleItem(
                            id = it.safeGetLong("id"),
                            profileId = pId,
                            subjectName = it.safeGetString("subject_name", "subjectName"),
                            room = it.safeGetString("room"),
                            teacher = it.safeGetString("teacher"),
                            dayOfWeek = it.safeGetInt("day_of_week", "dayOfWeek", default = dayOfWeek),
                            startTime = it.safeGetString("start_time", "startTime"),
                            endTime = it.safeGetString("end_time", "endTime"),
                            colorHex = it.safeGetString("color_hex", "colorHex", default = "#2563EB"),
                            classType = it.safeGetString("class_type", "classType", default = "LECTURE"),
                            buildingOrLink = it.safeGetString("building_or_link", "buildingOrLink")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading schedule for day $dayOfWeek, profile $profileId", e)
        }
        return list
    }

    fun getAllClasses(profileId: Long = getActiveProfileId()): List<ClassScheduleItem> {
        val list = mutableListOf<ClassScheduleItem>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM classes WHERE (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) ORDER BY day_of_week ASC, start_time ASC",
                arrayOf(profileId.toString())
            )
            cursor.use {
                while (it.moveToNext()) {
                    val pId = it.safeGetLong("profile_id", "profileId", default = profileId)
                    list.add(
                        ClassScheduleItem(
                            id = it.safeGetLong("id"),
                            profileId = pId,
                            subjectName = it.safeGetString("subject_name", "subjectName"),
                            room = it.safeGetString("room"),
                            teacher = it.safeGetString("teacher"),
                            dayOfWeek = it.safeGetInt("day_of_week", "dayOfWeek", default = 1),
                            startTime = it.safeGetString("start_time", "startTime"),
                            endTime = it.safeGetString("end_time", "endTime"),
                            colorHex = it.safeGetString("color_hex", "colorHex", default = "#2563EB"),
                            classType = it.safeGetString("class_type", "classType", default = "LECTURE"),
                            buildingOrLink = it.safeGetString("building_or_link", "buildingOrLink")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading all classes for profile $profileId", e)
        }
        return list
    }

    fun getClasses(profileId: Long = getActiveProfileId()): List<ClassScheduleItem> = getAllClasses(profileId)

    fun insertClassSchedule(item: ClassScheduleItem): Long {
        val db = writableDatabase
        val pId = if (item.profileId > 0) item.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("profile_id", pId)
            put("subject_name", item.subjectName)
            put("room", item.room)
            put("teacher", item.teacher)
            put("day_of_week", item.dayOfWeek)
            put("start_time", item.startTime)
            put("end_time", item.endTime)
            put("color_hex", item.colorHex)
            put("class_type", item.classType)
            put("building_or_link", item.buildingOrLink)
        }
        // Auto add to attendance if not already present
        ensureSubjectAttendance(item.subjectName)
        return db.insertWithOnConflict("classes", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateClassSchedule(item: ClassScheduleItem): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("subject_name", item.subjectName)
            put("room", item.room)
            put("teacher", item.teacher)
            put("day_of_week", item.dayOfWeek)
            put("start_time", item.startTime)
            put("end_time", item.endTime)
            put("color_hex", item.colorHex)
            put("class_type", item.classType)
            put("building_or_link", item.buildingOrLink)
        }
        return db.update("classes", cv, "id = ?", arrayOf(item.id.toString()))
    }

    fun deleteClassSchedule(id: Long): Int {
        val db = writableDatabase
        return db.delete("classes", "id = ?", arrayOf(id.toString()))
    }

    fun getAllAttendance(): List<SubjectAttendanceItem> {
        val list = mutableListOf<SubjectAttendanceItem>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM attendance ORDER BY subject_name ASC", null)
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        SubjectAttendanceItem(
                            id = it.safeGetLong("id"),
                            subjectName = it.safeGetString("subject_name", "subjectName"),
                            attendedClasses = it.safeGetInt("attended_classes", "attendedClasses"),
                            totalClasses = it.safeGetInt("total_classes", "totalClasses")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading attendance", e)
        }
        return list
    }

    fun recordAttendance(subjectName: String, isPresent: Boolean) {
        val db = writableDatabase
        ensureSubjectAttendance(subjectName)
        if (isPresent) {
            db.execSQL(
                "UPDATE attendance SET attended_classes = attended_classes + 1, total_classes = total_classes + 1 WHERE subject_name = ?",
                arrayOf(subjectName)
            )
        } else {
            db.execSQL(
                "UPDATE attendance SET total_classes = total_classes + 1 WHERE subject_name = ?",
                arrayOf(subjectName)
            )
        }
    }

    fun resetAttendance(subjectName: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("attended_classes", 0)
            put("total_classes", 0)
        }
        db.update("attendance", cv, "subject_name = ?", arrayOf(subjectName))
    }

    fun deleteAttendance(subjectName: String): Int {
        val db = writableDatabase
        return db.delete("attendance", "subject_name = ?", arrayOf(subjectName))
    }

    private fun ensureSubjectAttendance(subjectName: String) {
        val db = writableDatabase
        val c = db.rawQuery("SELECT id FROM attendance WHERE subject_name = ?", arrayOf(subjectName))
        val exists = c.use { it.moveToFirst() }
        if (!exists) {
            val cv = ContentValues().apply {
                put("subject_name", subjectName)
                put("attended_classes", 0)
                put("total_classes", 0)
            }
            db.insertWithOnConflict("attendance", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // ==========================================
    // CALENDAR ATTENDANCE RECORDS (Room Entity / DAO backing)
    // ==========================================
    private val attendanceRecordFlowTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    fun recordAttendance(record: database.entities.AttendanceRecord) {
        val db = writableDatabase
        val pId = if (record.profileId > 0) record.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("profile_id", pId)
            put("subjectName", record.subjectName)
            put("date", record.date)
            put("dayOfWeek", record.dayOfWeek)
            put("status", record.status)
        }
        db.insertWithOnConflict("attendance_records", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        attendanceRecordFlowTrigger.tryEmit(Unit)
    }

    fun deleteAttendanceRecord(subjectName: String, date: String, profileId: Long = getActiveProfileId()) {
        val db = writableDatabase
        db.delete("attendance_records", "subjectName = ? AND date = ? AND profile_id = ?", arrayOf(subjectName, date, profileId.toString()))
        attendanceRecordFlowTrigger.tryEmit(Unit)
    }

    fun getAttendanceRecordsByDate(date: String, profileId: Long = getActiveProfileId()): List<database.entities.AttendanceRecord> {
        val list = mutableListOf<database.entities.AttendanceRecord>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM attendance_records WHERE date = ? AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL)", arrayOf(date, profileId.toString()))
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        database.entities.AttendanceRecord(
                            id = it.safeGetLong("id"),
                            profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                            subjectName = it.safeGetString("subjectName", "subject_name"),
                            date = it.safeGetString("date", default = date),
                            dayOfWeek = it.safeGetString("dayOfWeek", "day_of_week"),
                            status = it.safeGetString("status")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading attendance records for date $date, profile $profileId", e)
        }
        return list
    }

    fun getAttendanceRecordsFlow(date: String, profileId: Long = getActiveProfileId()): kotlinx.coroutines.flow.Flow<List<database.entities.AttendanceRecord>> = kotlinx.coroutines.flow.flow {
        emit(getAttendanceRecordsByDate(date, profileId))
        attendanceRecordFlowTrigger.collect {
            emit(getAttendanceRecordsByDate(date, profileId))
        }
    }

    val attendanceDao: database.dao.AttendanceDao by lazy {
        object : database.dao.AttendanceDao {
            override fun getRecordsByDate(date: String, currentProfileId: Long): kotlinx.coroutines.flow.Flow<List<database.entities.AttendanceRecord>> {
                return getAttendanceRecordsFlow(date, currentProfileId)
            }

            override fun getRecordsByDate(date: String): kotlinx.coroutines.flow.Flow<List<database.entities.AttendanceRecord>> {
                return getAttendanceRecordsFlow(date, getActiveProfileId())
            }

            override suspend fun recordAttendance(record: database.entities.AttendanceRecord) {
                this@DatabaseHelper.recordAttendance(record)
            }

            override suspend fun deleteRecord(subjectName: String, date: String, currentProfileId: Long) {
                this@DatabaseHelper.deleteAttendanceRecord(subjectName, date, currentProfileId)
            }

            override suspend fun deleteRecord(subjectName: String, date: String) {
                this@DatabaseHelper.deleteAttendanceRecord(subjectName, date, getActiveProfileId())
            }

            override fun deleteByProfileId(profileId: Long) {
                writableDatabase.delete("attendance_records", "profile_id = ?", arrayOf(profileId.toString()))
            }

            override fun syncCourseDetailsToAttendance(
                profileId: Long,
                oldCourseName: String,
                newCourseName: String
            ): Int {
                return this@DatabaseHelper.syncCourseDetailsToAttendance(profileId, oldCourseName, newCourseName)
            }
        }
    }

    val timetableDao: database.dao.TimetableDao by lazy {
        object : database.dao.TimetableDao {
            override fun getClassesForDay(dayOfWeek: Int, currentProfileId: Long): List<ClassScheduleItem> {
                return this@DatabaseHelper.getScheduleForDay(dayOfWeek, currentProfileId)
            }

            override fun getClassesForDay(dayOfWeek: Int): List<ClassScheduleItem> {
                return this@DatabaseHelper.getScheduleForDay(dayOfWeek, getActiveProfileId())
            }

            override fun getClassesForDay(dayOfWeekName: String): List<ClassScheduleItem> {
                return getScheduleForDay(dayOfWeekName)
            }

            override fun getScheduleForDay(dayOfWeek: Int): List<ClassScheduleItem> {
                return this@DatabaseHelper.getScheduleForDay(dayOfWeek, getActiveProfileId())
            }

            override fun getScheduleForDay(dayOfWeekName: String): List<ClassScheduleItem> {
                val dbDay = when (dayOfWeekName.trim().lowercase()) {
                    "monday", "mon" -> 1
                    "tuesday", "tue" -> 2
                    "wednesday", "wed" -> 3
                    "thursday", "thu" -> 4
                    "friday", "fri" -> 5
                    "saturday", "sat" -> 6
                    "sunday", "sun" -> 7
                    else -> 1
                }
                return this@DatabaseHelper.getScheduleForDay(dbDay, getActiveProfileId())
            }

            override fun deleteByProfileId(profileId: Long) {
                writableDatabase.delete("classes", "profile_id = ?", arrayOf(profileId.toString()))
            }

            override fun syncCourseDetailsToClasses(
                profileId: Long,
                courseCode: String,
                courseName: String,
                teacherName: String,
                room: String
            ): Int {
                return this@DatabaseHelper.syncCourseDetailsToClasses(profileId, courseCode, courseName, teacherName, room)
            }

            override fun getNextImmediateClass(
                profileId: Long,
                dayOfWeek: Int,
                currentClassEndTime: String
            ): ClassScheduleItem? {
                return this@DatabaseHelper.getNextImmediateClass(profileId, dayOfWeek, currentClassEndTime)
            }
        }
    }

    fun getNextImmediateClass(
        profileId: Long,
        dayOfWeek: Int,
        currentClassEndTime: String
    ): ClassScheduleItem? {
        val targetDay = if (dayOfWeek in 1..7) dayOfWeek else 1
        val pId = if (profileId > 0) profileId else getActiveProfileId()
        val classes = getScheduleForDay(targetDay, pId)
        val endMinutes = com.duorhythm.app.scheduler.ClassAlarmScheduler.parseMinutes(currentClassEndTime)
        if (endMinutes <= 0) return null

        return classes
            .filter { candidate ->
                val startMinutes = com.duorhythm.app.scheduler.ClassAlarmScheduler.parseMinutes(candidate.startTime)
                val diff = startMinutes - endMinutes
                diff in -5..5
            }
            .minByOrNull { candidate ->
                val startMinutes = com.duorhythm.app.scheduler.ClassAlarmScheduler.parseMinutes(candidate.startTime)
                kotlin.math.abs(startMinutes - endMinutes)
            }
    }

    fun syncCourseDetailsToClasses(
        profileId: Long,
        courseCode: String,
        courseName: String,
        teacherName: String,
        room: String
    ): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            if (teacherName.isNotBlank()) put("teacher", teacherName)
            if (room.isNotBlank()) put("room", room)
            if (courseName.isNotBlank()) put("subject_name", courseName)
        }
        val pId = if (profileId > 0) profileId else getActiveProfileId()
        val hasCode = courseCode.isNotBlank()
        val where = if (hasCode) {
            "profile_id = ? AND (LOWER(subject_name) = LOWER(?) OR LOWER(subject_name) = LOWER(?))"
        } else {
            "profile_id = ? AND LOWER(subject_name) = LOWER(?)"
        }
        val args = if (hasCode) arrayOf(pId.toString(), courseName, courseCode) else arrayOf(pId.toString(), courseName)
        return try {
            db.update("classes", cv, where, args)
        } catch (_: Exception) {
            0
        }
    }

    fun syncCourseDetailsToAttendance(
        profileId: Long,
        oldCourseName: String,
        newCourseName: String
    ): Int {
        if (oldCourseName.isBlank() || oldCourseName.equals(newCourseName, ignoreCase = true)) return 0
        val db = writableDatabase
        val pId = if (profileId > 0) profileId else getActiveProfileId()
        var updated = 0
        try {
            val cv = ContentValues().apply { put("subjectName", newCourseName) }
            updated += db.update("attendance_records", cv, "profile_id = ? AND LOWER(subjectName) = LOWER(?)", arrayOf(pId.toString(), oldCourseName))
            val cvSummary = ContentValues().apply { put("subject_name", newCourseName) }
            db.update("attendance", cvSummary, "LOWER(subject_name) = LOWER(?)", arrayOf(oldCourseName))
        } catch (_: Exception) {}
        return updated
    }

    private val holidayFlowTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    fun isHoliday(date: String, profileId: Long = getActiveProfileId()): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM holiday_records WHERE date = ? AND profile_id = ? LIMIT 1", arrayOf(date, profileId.toString()))
        return cursor.use { it.moveToFirst() }
    }

    fun setHoliday(date: String, title: String = "Holiday", profileId: Long = getActiveProfileId()) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("date", date)
            put("title", title)
            put("profile_id", profileId)
        }
        db.insertWithOnConflict("holiday_records", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        holidayFlowTrigger.tryEmit(Unit)
    }

    fun removeHoliday(date: String, profileId: Long = getActiveProfileId()) {
        val db = writableDatabase
        db.delete("holiday_records", "date = ? AND profile_id = ?", arrayOf(date, profileId.toString()))
        holidayFlowTrigger.tryEmit(Unit)
    }

    fun isHolidayFlow(date: String, profileId: Long = getActiveProfileId()): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flow {
        emit(isHoliday(date, profileId))
        holidayFlowTrigger.collect {
            emit(isHoliday(date, profileId))
        }
    }

    val holidayDao: database.dao.HolidayDao by lazy {
        object : database.dao.HolidayDao {
            override fun isHoliday(date: String, currentProfileId: Long): kotlinx.coroutines.flow.Flow<Boolean> {
                return isHolidayFlow(date, currentProfileId)
            }

            override fun isHoliday(profileId: Long, date: String): Boolean {
                return this@DatabaseHelper.isHoliday(date, profileId)
            }

            override fun isHoliday(date: String): kotlinx.coroutines.flow.Flow<Boolean> {
                return isHolidayFlow(date, getActiveProfileId())
            }

            override suspend fun insertHoliday(record: database.entities.HolidayRecord) {
                this@DatabaseHelper.setHoliday(record.date, record.title, record.profileId)
            }

            override suspend fun setHoliday(date: String) {
                this@DatabaseHelper.setHoliday(date)
            }

            override suspend fun setHoliday(record: database.entities.HolidayRecord) {
                this@DatabaseHelper.setHoliday(record.date, record.title, record.profileId)
            }

            override suspend fun removeHoliday(date: String, currentProfileId: Long) {
                this@DatabaseHelper.removeHoliday(date, currentProfileId)
            }

            override suspend fun removeHoliday(date: String) {
                this@DatabaseHelper.removeHoliday(date, getActiveProfileId())
            }

            override fun deleteByProfileId(profileId: Long) {
                writableDatabase.delete("holiday_records", "profile_id = ?", arrayOf(profileId.toString()))
            }
        }
    }

    val categoryDao: database.dao.CategoryDao by lazy {
        object : database.dao.CategoryDao {
            override fun getAllCategories(): List<database.entities.CategoryEntity> {
                return getAllTransactionCategories(null).map { database.entities.CategoryEntity.fromItem(it) }
            }

            override fun getAllCategoriesFlow(): kotlinx.coroutines.flow.Flow<List<database.entities.CategoryEntity>> = kotlinx.coroutines.flow.flow {
                emit(getAllCategories())
            }

            override fun getCategoriesByType(type: String): List<database.entities.CategoryEntity> {
                return getAllTransactionCategories(type).map { database.entities.CategoryEntity.fromItem(it) }
            }

            override suspend fun insertCategory(category: database.entities.CategoryEntity): Long {
                return insertTransactionCategory(category.name, category.type, category.color_hex, category.icon)
            }

            override suspend fun deleteCategory(id: Long) {
                deleteTransactionCategory(id)
            }

            override fun getTransactionCountByCategory(categoryName: String): Int {
                return this@DatabaseHelper.getTransactionCountByCategory(categoryName)
            }

            override suspend fun deleteCategoryAndReassign(id: Long, categoryName: String): Boolean {
                return this@DatabaseHelper.deleteCategoryAndReassignTransactions(id, categoryName)
            }
        }
    }

    val classTypeDao: database.dao.ClassTypeDao by lazy {
        object : database.dao.ClassTypeDao {
            override fun getTypesByProfile(profileId: Long): List<database.entities.ClassTypeEntity> {
                ensureClassTypesTable()
                ensureDefaultTypes(profileId)
                val list = mutableListOf<database.entities.ClassTypeEntity>()
                val db = readableDatabase
                val cursor = db.rawQuery(
                    "SELECT * FROM class_types WHERE profile_id = ? ORDER BY is_default DESC, name ASC",
                    arrayOf(profileId.toString())
                )
                cursor.use {
                    while (it.moveToNext()) {
                        val colorHex = it.safeGetString("color_hex", default = "#0284C7")
                        list.add(
                            database.entities.ClassTypeEntity(
                                id = it.safeGetLong("id"),
                                profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                                name = it.safeGetString("name"),
                                isDefault = it.safeGetBoolean("is_default", "isDefault"),
                                colorHex = if (colorHex.isNotBlank()) colorHex else "#0284C7"
                            )
                        )
                    }
                }
                return list
            }

            override fun insertType(type: database.entities.ClassTypeEntity): Long {
                ensureClassTypesTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put("profile_id", type.profileId)
                    put("name", type.name)
                    put("color_hex", type.colorHex)
                    put("is_default", if (type.isDefault) 1 else 0)
                }
                return db.insert("class_types", null, cv)
            }

            override fun deleteType(id: Long) {
                ensureClassTypesTable()
                val db = writableDatabase
                db.delete("class_types", "id = ?", arrayOf(id.toString()))
            }

            override fun updateClassTypeName(id: Long, newName: String) {
                ensureClassTypesTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put("name", newName)
                }
                db.update("class_types", cv, "id = ?", arrayOf(id.toString()))
            }

            override fun updateClassType(id: Long, newName: String, newColorHex: String) {
                ensureClassTypesTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put("name", newName)
                    put("color_hex", newColorHex)
                }
                db.update("class_types", cv, "id = ?", arrayOf(id.toString()))
            }

            override fun updateClassTypeColor(id: Long, newColorHex: String) {
                ensureClassTypesTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put("color_hex", newColorHex)
                }
                db.update("class_types", cv, "id = ?", arrayOf(id.toString()))
            }

            override fun ensureDefaultTypes(profileId: Long) {
                ensureClassTypesTable()
                val db = writableDatabase
                val cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM class_types WHERE profile_id = ?",
                    arrayOf(profileId.toString())
                )
                var count = 0
                cursor.use {
                    if (it.moveToFirst()) count = it.getInt(0)
                }
                if (count == 0) {
                    val defaults = listOf(
                        Pair("Lecture", "#0284C7"),
                        Pair("Lab", "#10B981"),
                        Pair("Tutorial", "#F59E0B"),
                        Pair("Seminar", "#8B5CF6")
                    )
                    for ((d, color) in defaults) {
                        val cv = ContentValues().apply {
                            put("profile_id", profileId)
                            put("name", d)
                            put("color_hex", color)
                            put("is_default", 1)
                        }
                        db.insert("class_types", null, cv)
                    }
                }
            }
        }
    }

    val courseDao: CourseDao by lazy {
        object : CourseDao {
            override fun getCoursesByProfile(profileId: Long): List<StudentSubject> = getAllStudentSubjects(profileId)
            override fun getCourseById(id: Long): StudentSubject? = getStudentSubjectById(id)
            override fun deleteCourseById(id: Long) {
                val course = getStudentSubjectById(id)
                deleteCourseCascade(id, course?.code ?: "", course?.profileId ?: getActiveProfileId())
            }
        }
    }

    val assessmentDao: AssessmentDao by lazy {
        object : AssessmentDao {
            override fun getAssessmentsByProfile(profileId: Long): List<AssessmentEntity> {
                return getAllExams(profileId = profileId).map { (item, subject) ->
                    AssessmentEntity.fromSubjectWorkItem(item, subject.name, subject.code, subject.teacher)
                }
            }

            override fun getAssessmentsByCourse(profileId: Long, courseId: Long): List<AssessmentEntity> {
                val subject = getStudentSubjectById(courseId)
                val name = subject?.name ?: ""
                val code = subject?.code ?: ""
                val teacher = subject?.teacher ?: ""
                return getItemsForSubject(courseId).filter { it.itemType == "EXAM" }.map {
                    AssessmentEntity.fromSubjectWorkItem(it, name, code, teacher)
                }
            }

            override fun insertAssessment(assessment: AssessmentEntity): Long {
                return insertSubjectWorkItem(assessment.toSubjectWorkItem())
            }

            override fun updateAssessment(assessment: AssessmentEntity) {
                updateSubjectWorkItem(assessment.toSubjectWorkItem())
            }

            override fun deleteAssessment(id: Long) {
                deleteSubjectWorkItem(id)
            }

            override fun deleteAssessmentsByCourse(profileId: Long, courseId: Long) {
                writableDatabase.delete("subject_work_items", "subject_id = ? AND item_type = 'EXAM'", arrayOf(courseId.toString()))
            }
        }
    }

    val assessmentCategoryDao: database.dao.AssessmentCategoryDao by lazy {
        object : database.dao.AssessmentCategoryDao {
            override fun getCategoriesByProfile(profileId: Long): List<database.entities.AssessmentCategoryEntity> {
                ensureAssessmentCategoriesTable()
                ensureDefaultCategories(profileId)
                val list = mutableListOf<database.entities.AssessmentCategoryEntity>()
                val db = readableDatabase
                val cursor = db.rawQuery(
                    "SELECT * FROM assessment_categories WHERE profile_id = ? ORDER BY is_default DESC, name ASC",
                    arrayOf(profileId.toString())
                )
                cursor.use {
                    while (it.moveToNext()) {
                        val colorHex = it.safeGetString("color_hex", default = "#8B5CF6")
                        list.add(
                            database.entities.AssessmentCategoryEntity(
                                id = it.safeGetLong("id"),
                                profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                                name = it.safeGetString("name"),
                                isDefault = it.safeGetBoolean("is_default", "isDefault"),
                                colorHex = if (colorHex.isNotBlank()) colorHex else "#8B5CF6"
                            )
                        )
                    }
                }
                return list
            }

            override fun insertCategory(category: database.entities.AssessmentCategoryEntity): Long {
                ensureAssessmentCategoriesTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put("profile_id", category.profileId)
                    put("name", category.name)
                    put("color_hex", category.colorHex)
                    put("is_default", if (category.isDefault) 1 else 0)
                }
                return db.insert("assessment_categories", null, cv)
            }

            override fun deleteCategory(id: Long) {
                ensureAssessmentCategoriesTable()
                val db = writableDatabase
                db.delete("assessment_categories", "id = ?", arrayOf(id.toString()))
            }

            override fun updateCategoryName(id: Long, newName: String) {
                ensureAssessmentCategoriesTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put("name", newName)
                }
                db.update("assessment_categories", cv, "id = ?", arrayOf(id.toString()))
            }

            override fun updateCategory(id: Long, newName: String, newColorHex: String) {
                ensureAssessmentCategoriesTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put("name", newName)
                    put("color_hex", newColorHex)
                }
                db.update("assessment_categories", cv, "id = ?", arrayOf(id.toString()))
            }

            override fun updateCategoryColor(id: Long, newColorHex: String) {
                ensureAssessmentCategoriesTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put("color_hex", newColorHex)
                }
                db.update("assessment_categories", cv, "id = ?", arrayOf(id.toString()))
            }

            override fun ensureDefaultCategories(profileId: Long) {
                ensureAssessmentCategoriesTable()
                val db = writableDatabase
                val cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM assessment_categories WHERE profile_id = ?",
                    arrayOf(profileId.toString())
                )
                var count = 0
                cursor.use {
                    if (it.moveToFirst()) count = it.getInt(0)
                }
                if (count == 0) {
                    val defaults = listOf(
                        Pair("Quiz", "#8B5CF6"),
                        Pair("Class Test", "#06B6D4"),
                        Pair("Midterm", "#F59E0B"),
                        Pair("Final", "#EF4444"),
                        Pair("Lab Exam", "#10B981"),
                        Pair("Viva", "#EC4899")
                    )
                    for ((d, color) in defaults) {
                        val cv = ContentValues().apply {
                            put("profile_id", profileId)
                            put("name", d)
                            put("color_hex", color)
                            put("is_default", 1)
                        }
                        db.insert("assessment_categories", null, cv)
                    }
                }
            }
        }
    }

    fun ensureClassTypesTable() {
        val db = writableDatabase
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS class_types (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                name TEXT NOT NULL,
                is_default INTEGER DEFAULT 0,
                color_hex TEXT DEFAULT '#0284C7'
            )"""
        )
        ensureColumnExists(db, "class_types", "color_hex", "TEXT DEFAULT '#0284C7'")
    }

    fun ensureAssessmentCategoriesTable() {
        val db = writableDatabase
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS assessment_categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                name TEXT NOT NULL,
                is_default INTEGER DEFAULT 0,
                color_hex TEXT DEFAULT '#8B5CF6'
            )"""
        )
        ensureColumnExists(db, "assessment_categories", "color_hex", "TEXT DEFAULT '#8B5CF6'")
    }

    fun ensureSubscriptionsTable() {
        val db = writableDatabase
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS subscriptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER DEFAULT 1,
                profileId INTEGER DEFAULT 1,
                name TEXT NOT NULL DEFAULT '',
                amount REAL NOT NULL DEFAULT 0.0,
                service_name TEXT,
                cost REAL,
                currency TEXT DEFAULT 'BDT',
                billing_cycle TEXT DEFAULT 'MONTHLY',
                renewal_date TEXT,
                bucket_id INTEGER DEFAULT 0,
                bucket_name TEXT DEFAULT '',
                category TEXT DEFAULT 'Entertainment',
                icon TEXT DEFAULT 'subscription',
                is_active INTEGER DEFAULT 1,
                auto_log INTEGER DEFAULT 1,
                isRange INTEGER DEFAULT 0,
                is_range INTEGER DEFAULT 0,
                startDay INTEGER DEFAULT 1,
                start_day INTEGER DEFAULT 1,
                endDay INTEGER DEFAULT 8,
                end_day INTEGER DEFAULT 8,
                dueDay INTEGER DEFAULT 5,
                due_day INTEGER DEFAULT 5,
                linkedAccountId INTEGER,
                linked_account_id INTEGER,
                lastPaidMonthYear TEXT,
                last_paid_month_year TEXT
            )"""
        )
        ensureColumnExists(db, "subscriptions", "profileId", "INTEGER DEFAULT 1")
        ensureColumnExists(db, "subscriptions", "profile_id", "INTEGER DEFAULT 1")
        ensureColumnExists(db, "subscriptions", "name", "TEXT DEFAULT ''")
        ensureColumnExists(db, "subscriptions", "amount", "REAL DEFAULT 0.0")
        ensureColumnExists(db, "subscriptions", "service_name", "TEXT DEFAULT ''")
        ensureColumnExists(db, "subscriptions", "cost", "REAL DEFAULT 0.0")
        ensureColumnExists(db, "subscriptions", "isRange", "INTEGER DEFAULT 0")
        ensureColumnExists(db, "subscriptions", "is_range", "INTEGER DEFAULT 0")
        ensureColumnExists(db, "subscriptions", "startDay", "INTEGER DEFAULT 1")
        ensureColumnExists(db, "subscriptions", "start_day", "INTEGER DEFAULT 1")
        ensureColumnExists(db, "subscriptions", "endDay", "INTEGER DEFAULT 8")
        ensureColumnExists(db, "subscriptions", "end_day", "INTEGER DEFAULT 8")
        ensureColumnExists(db, "subscriptions", "dueDay", "INTEGER DEFAULT 5")
        ensureColumnExists(db, "subscriptions", "due_day", "INTEGER DEFAULT 5")
        ensureColumnExists(db, "subscriptions", "linkedAccountId", "INTEGER")
        ensureColumnExists(db, "subscriptions", "linked_account_id", "INTEGER")
        ensureColumnExists(db, "subscriptions", "lastPaidMonthYear", "TEXT")
        ensureColumnExists(db, "subscriptions", "last_paid_month_year", "TEXT DEFAULT ''")
        ensureColumnExists(db, "subscriptions", "billing_cycle", "TEXT DEFAULT 'MONTHLY'")
        ensureColumnExists(db, "subscriptions", "next_renewal_date", "TEXT DEFAULT ''")
        ensureColumnExists(db, "subscriptions", "renewal_date", "TEXT DEFAULT ''")
        try {
            db.execSQL("UPDATE subscriptions SET name = service_name WHERE (name IS NULL OR name = '') AND service_name IS NOT NULL AND service_name != ''")
            db.execSQL("UPDATE subscriptions SET service_name = name WHERE (service_name IS NULL OR service_name = '') AND name IS NOT NULL AND name != ''")
            db.execSQL("UPDATE subscriptions SET amount = cost WHERE (amount IS NULL OR amount = 0) AND cost IS NOT NULL AND cost != 0")
            db.execSQL("UPDATE subscriptions SET cost = amount WHERE (cost IS NULL OR cost = 0) AND amount IS NOT NULL AND amount != 0")
        } catch (_: Exception) {}
    }

    private fun mapCursorToSubscriptionEntity(it: android.database.Cursor): database.entities.SubscriptionEntity {
        val idCol = it.getColumnIndex("id")
        val id = if (idCol >= 0) it.getLong(idCol) else 0L
        val pIdCol = it.getColumnIndex("profileId").takeIf { it >= 0 } ?: it.getColumnIndex("profile_id")
        val profileId = if (pIdCol >= 0) it.getLong(pIdCol) else 1L

        val nameCol = it.getColumnIndex("name").takeIf { it >= 0 } ?: it.getColumnIndex("service_name")
        val name = if (nameCol >= 0) it.getString(nameCol) ?: "" else ""

        val amtCol = it.getColumnIndex("amount").takeIf { it >= 0 } ?: it.getColumnIndex("cost")
        val amount = if (amtCol >= 0) it.getDouble(amtCol) else 0.0

        val isRangeCol = it.getColumnIndex("isRange").takeIf { it >= 0 } ?: it.getColumnIndex("is_range")
        val isRange = if (isRangeCol >= 0) it.getInt(isRangeCol) == 1 else false

        val startDayCol = it.getColumnIndex("startDay").takeIf { it >= 0 } ?: it.getColumnIndex("start_day")
        val startDay = if (startDayCol >= 0) it.getInt(startDayCol).takeIf { it in 1..31 } ?: 1 else 1

        val endDayCol = it.getColumnIndex("endDay").takeIf { it >= 0 } ?: it.getColumnIndex("end_day")
        val endDay = if (endDayCol >= 0) it.getInt(endDayCol).takeIf { it in 1..31 } ?: 8 else 8

        val dueDayCol = it.getColumnIndex("dueDay").takeIf { it >= 0 } ?: it.getColumnIndex("due_day")
        val dueDay = if (dueDayCol >= 0) it.getInt(dueDayCol).takeIf { it in 1..31 } ?: 5 else 5

        val linkedCol = it.getColumnIndex("linkedAccountId").takeIf { it >= 0 }
            ?: it.getColumnIndex("linked_account_id").takeIf { it >= 0 }
            ?: it.getColumnIndex("bucket_id")
        val linkedAccountId = if (linkedCol >= 0 && !it.isNull(linkedCol)) {
            val v = it.getLong(linkedCol)
            if (v > 0) v else null
        } else null

        val paidCol = it.getColumnIndex("lastPaidMonthYear").takeIf { it >= 0 } ?: it.getColumnIndex("last_paid_month_year")
        val lastPaidMonthYear = if (paidCol >= 0 && !it.isNull(paidCol)) it.getString(paidCol) else null

        val entity = database.entities.SubscriptionEntity(
            id = id,
            profileId = profileId,
            name = name,
            amount = amount,
            isRange = isRange,
            startDay = startDay,
            endDay = endDay,
            dueDay = dueDay,
            linkedAccountId = linkedAccountId,
            lastPaidMonthYear = lastPaidMonthYear
        )

        if (linkedAccountId != null) {
            val bucket = getWalletBucketById(linkedAccountId)
            entity.accountName = bucket?.name ?: "Account #$linkedAccountId"
        } else {
            entity.accountName = "None / External"
        }

        return entity
    }

    val subscriptionUpdateFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    fun insertSubscription(sub: database.entities.SubscriptionEntity): Long {
        ensureSubscriptionsTable()
        val db = this.writableDatabase
        val pId = if (sub.profileId <= 0L) 1L else sub.profileId
        val values = ContentValues().apply {
            put("profile_id", pId)
            put("profileId", pId)
            put("name", sub.name.trim())
            put("service_name", sub.name.trim())
            put("amount", sub.amount)
            put("cost", sub.amount)
            put("is_range", if (sub.isRange) 1 else 0)
            put("isRange", if (sub.isRange) 1 else 0)
            put("start_day", sub.startDay)
            put("startDay", sub.startDay)
            put("end_day", sub.endDay)
            put("endDay", sub.endDay)
            put("due_day", sub.dueDay)
            put("dueDay", sub.dueDay)
            if (sub.linkedAccountId != null && sub.linkedAccountId > 0) {
                put("linked_account_id", sub.linkedAccountId)
                put("linkedAccountId", sub.linkedAccountId)
                put("bucket_id", sub.linkedAccountId)
                val bucket = getWalletBucketById(sub.linkedAccountId)
                put("bucket_name", bucket?.name ?: "")
            } else {
                putNull("linked_account_id")
                putNull("linkedAccountId")
                put("bucket_id", 0)
                put("bucket_name", "")
            }
            put("last_paid_month_year", sub.lastPaidMonthYear ?: "")
            put("lastPaidMonthYear", sub.lastPaidMonthYear ?: "")
            put("billing_cycle", "MONTHLY")
            put("next_renewal_date", "")
            put("renewal_date", "")
            put("is_active", 1)
        }
        val rowId = db.insertWithOnConflict("subscriptions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Log.d("DUO_SUB_INSERT", "Inserted subscription: ${sub.name}, rowId=$rowId")
        subscriptionUpdateFlow.tryEmit(Unit)
        return rowId
    }

    fun getSubscriptions(profileId: Long): List<database.entities.SubscriptionEntity> {
        ensureSubscriptionsTable()
        val list = mutableListOf<database.entities.SubscriptionEntity>()
        val db = this.readableDatabase
        val query = "SELECT * FROM subscriptions WHERE profile_id = ? OR profile_id = 0 OR profile_id IS NULL ORDER BY id DESC"
        var cursor: android.database.Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(profileId.toString()))
            val idIdx = cursor.getColumnIndexSafe("id")
            val nameIdx = cursor.getColumnIndexSafe("name").takeIf { it != -1 } ?: cursor.getColumnIndexSafe("service_name")
            val amountIdx = cursor.getColumnIndexSafe("amount").takeIf { it != -1 } ?: cursor.getColumnIndexSafe("cost")
            val isRangeIdx = cursor.getColumnIndexSafe("is_range").takeIf { it != -1 } ?: cursor.getColumnIndexSafe("isRange")
            val startDayIdx = cursor.getColumnIndexSafe("start_day").takeIf { it != -1 } ?: cursor.getColumnIndexSafe("startDay")
            val endDayIdx = cursor.getColumnIndexSafe("end_day").takeIf { it != -1 } ?: cursor.getColumnIndexSafe("endDay")
            val dueDayIdx = cursor.getColumnIndexSafe("due_day").takeIf { it != -1 } ?: cursor.getColumnIndexSafe("dueDay")
            val linkedAccIdx = cursor.getColumnIndexSafe("linked_account_id").takeIf { it != -1 }
                ?: cursor.getColumnIndexSafe("linkedAccountId").takeIf { it != -1 }
                ?: cursor.getColumnIndexSafe("bucket_id")
            val lastPaidIdx = cursor.getColumnIndexSafe("last_paid_month_year").takeIf { it != -1 } ?: cursor.getColumnIndexSafe("lastPaidMonthYear")

            while (cursor.moveToNext()) {
                val entity = database.entities.SubscriptionEntity(
                    id = if (idIdx != -1) cursor.getLong(idIdx) else 0L,
                    profileId = profileId,
                    name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else "",
                    amount = if (amountIdx != -1) cursor.getDouble(amountIdx) else 0.0,
                    isRange = if (isRangeIdx != -1) cursor.getInt(isRangeIdx) == 1 else false,
                    startDay = if (startDayIdx != -1) cursor.getInt(startDayIdx).coerceIn(1, 31) else 1,
                    endDay = if (endDayIdx != -1) cursor.getInt(endDayIdx).coerceIn(1, 31) else 8,
                    dueDay = if (dueDayIdx != -1) cursor.getInt(dueDayIdx).coerceIn(1, 31) else 5,
                    linkedAccountId = if (linkedAccIdx != -1 && !cursor.isNull(linkedAccIdx)) {
                        val v = cursor.getLong(linkedAccIdx)
                        if (v > 0) v else null
                    } else null,
                    lastPaidMonthYear = if (lastPaidIdx != -1 && !cursor.isNull(lastPaidIdx)) cursor.getString(lastPaidIdx) else null
                )
                if (entity.linkedAccountId != null) {
                    val bucket = getWalletBucketById(entity.linkedAccountId)
                    entity.accountName = bucket?.name ?: "Account #${entity.linkedAccountId}"
                } else {
                    entity.accountName = "None / External"
                }
                list.add(entity)
            }
        } catch (e: Exception) {
            Log.e("DUO_SUB_QUERY", "Error fetching subscriptions", e)
        } finally {
            cursor?.close()
        }
        return list
    }

    val subscriptionDao: database.dao.SubscriptionDao by lazy {
        object : database.dao.SubscriptionDao {
            override fun getSubscriptions(profileId: Long): List<database.entities.SubscriptionEntity> {
                return this@DatabaseHelper.getSubscriptions(profileId)
            }

            override fun getSubscriptionsSync(profileId: Long): List<database.entities.SubscriptionEntity> =
                this@DatabaseHelper.getSubscriptions(profileId)

            override fun getSubscriptionsFlow(profileId: Long): kotlinx.coroutines.flow.Flow<List<database.entities.SubscriptionEntity>> = kotlinx.coroutines.flow.flow {
                emit(this@DatabaseHelper.getSubscriptions(profileId))
                subscriptionUpdateFlow.collect {
                    emit(this@DatabaseHelper.getSubscriptions(profileId))
                }
            }

            override fun getActiveSubscriptions(profileId: Long): List<database.entities.SubscriptionEntity> {
                return this@DatabaseHelper.getSubscriptions(profileId)
            }

            override fun getSubscriptionById(id: Long): database.entities.SubscriptionEntity? {
                ensureSubscriptionsTable()
                val db = readableDatabase
                val cursor = db.rawQuery("SELECT * FROM subscriptions WHERE id = ? LIMIT 1", arrayOf(id.toString()))
                return cursor.use {
                    if (it.moveToNext()) mapCursorToSubscriptionEntity(it) else null
                }
            }

            override fun insertSubscription(subscription: database.entities.SubscriptionEntity): Long {
                return this@DatabaseHelper.insertSubscription(subscription)
            }

            override fun updateSubscription(subscription: database.entities.SubscriptionEntity): Int {
                ensureSubscriptionsTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put("profileId", subscription.profileId)
                    put("profile_id", subscription.profileId)
                    put("name", subscription.name)
                    put("service_name", subscription.name)
                    put("amount", subscription.amount)
                    put("cost", subscription.amount)
                    put("isRange", if (subscription.isRange) 1 else 0)
                    put("is_range", if (subscription.isRange) 1 else 0)
                    put("startDay", subscription.startDay)
                    put("start_day", subscription.startDay)
                    put("endDay", subscription.endDay)
                    put("end_day", subscription.endDay)
                    put("dueDay", subscription.dueDay)
                    put("due_day", subscription.dueDay)
                    if (subscription.linkedAccountId != null) {
                        put("linkedAccountId", subscription.linkedAccountId)
                        put("linked_account_id", subscription.linkedAccountId)
                        put("bucket_id", subscription.linkedAccountId)
                        val bucket = getWalletBucketById(subscription.linkedAccountId)
                        put("bucket_name", bucket?.name ?: "")
                    } else {
                        putNull("linkedAccountId")
                        putNull("linked_account_id")
                        put("bucket_id", 0)
                        put("bucket_name", "")
                    }
                    if (subscription.lastPaidMonthYear != null) {
                        put("lastPaidMonthYear", subscription.lastPaidMonthYear)
                        put("last_paid_month_year", subscription.lastPaidMonthYear)
                    } else {
                        putNull("lastPaidMonthYear")
                        putNull("last_paid_month_year")
                    }
                }
                val updatedCount = db.update("subscriptions", cv, "id = ?", arrayOf(subscription.id.toString()))
                subscriptionUpdateFlow.tryEmit(Unit)
                return updatedCount
            }

            override fun updatePaidMonth(id: Long, monthYear: String?): Int {
                ensureSubscriptionsTable()
                val db = writableDatabase
                val cv = ContentValues().apply {
                    if (monthYear != null) {
                        put("lastPaidMonthYear", monthYear)
                        put("last_paid_month_year", monthYear)
                    } else {
                        putNull("lastPaidMonthYear")
                        putNull("last_paid_month_year")
                    }
                }
                val updatedCount = db.update("subscriptions", cv, "id = ?", arrayOf(id.toString()))
                subscriptionUpdateFlow.tryEmit(Unit)
                return updatedCount
            }

            override fun deleteSubscription(id: Long): Int {
                ensureSubscriptionsTable()
                val db = writableDatabase
                val deletedCount = db.delete("subscriptions", "id = ?", arrayOf(id.toString()))
                subscriptionUpdateFlow.tryEmit(Unit)
                return deletedCount
            }

            override fun deleteByProfileId(profileId: Long): Int {
                ensureSubscriptionsTable()
                val db = writableDatabase
                val deletedCount = db.delete("subscriptions", "profile_id = ?", arrayOf(profileId.toString()))
                subscriptionUpdateFlow.tryEmit(Unit)
                return deletedCount
            }
        }
    }

    val accountDao: database.dao.AccountDao by lazy {
        object : database.dao.AccountDao {
            override fun getAccounts(profileId: Long): List<WalletBucketItem> {
                return getAllWalletBuckets(profileId)
            }

            override fun getAccountById(id: Long): WalletBucketItem? {
                return getWalletBucketById(id)
            }
        }
    }

    fun getActiveProfileId(): Long {
        return com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(appContext)
    }

    fun ensureDefaultProfileIfEmpty() {
        if (getProfileCount() == 0) {
            val userName = com.duorhythm.app.data.local.pref.UserProfileManager.getUserName(appContext, "Scholar")
            val institution = com.duorhythm.app.data.local.pref.UserProfileManager.getInstitutionName(appContext)
            val faculty = com.duorhythm.app.data.local.pref.UserProfileManager.getAcademicFaculty(appContext)
            val avatar = com.duorhythm.app.data.local.pref.UserProfileManager.getAvatarUri(appContext)
            val profile = database.entities.ProfileEntity(
                slotNumber = 1,
                name = userName.ifBlank { "Scholar" },
                email = "student@duorhythm.app",
                passwordHash = com.duorhythm.app.data.local.pref.ProfileManager.hashPassword("1234"),
                institution = institution,
                department = faculty,
                semester = "1st",
                avatarUri = avatar.ifBlank { null },
                rememberEmail = true
            )
            val id = insertProfile(profile)
            if (id > 0) {
                com.duorhythm.app.data.local.pref.ProfileManager.setActiveProfileId(appContext, id)
            }
        }
    }

    fun getAllProfiles(): List<database.entities.ProfileEntity> {
        val list = mutableListOf<database.entities.ProfileEntity>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM profiles ORDER BY slotNumber ASC", null)
        cursor.use {
            val idIdx = it.getColumnIndex("id")
            val slotIdx = it.getColumnIndex("slotNumber")
            val nameIdx = it.getColumnIndex("name")
            val emailIdx = it.getColumnIndex("email")
            val passIdx = it.getColumnIndex("passwordHash")
            val instIdx = it.getColumnIndex("institution")
            val deptIdx = it.getColumnIndex("department")
            val semIdx = it.getColumnIndex("semester")
            val avIdx = it.getColumnIndex("avatarUri")
            val remIdx = it.getColumnIndex("rememberEmail")
            while (it.moveToNext()) {
                list.add(
                    database.entities.ProfileEntity(
                        id = if (idIdx >= 0) it.getLong(idIdx) else 0L,
                        slotNumber = if (slotIdx >= 0) it.getInt(slotIdx) else 1,
                        name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else "",
                        email = if (emailIdx >= 0) it.getString(emailIdx) ?: "" else "",
                        passwordHash = if (passIdx >= 0) it.getString(passIdx) ?: "" else "",
                        institution = if (instIdx >= 0) it.getString(instIdx) ?: "" else "",
                        department = if (deptIdx >= 0) it.getString(deptIdx) ?: "" else "",
                        semester = if (semIdx >= 0) it.getString(semIdx) ?: "" else "",
                        avatarUri = if (avIdx >= 0) it.getString(avIdx) else null,
                        rememberEmail = if (remIdx >= 0) it.getInt(remIdx) == 1 else false
                    )
                )
            }
        }
        return list
    }

    fun getProfileById(id: Long): database.entities.ProfileEntity? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM profiles WHERE id = ? LIMIT 1", arrayOf(id.toString()))
        cursor.use {
            if (it.moveToNext()) {
                val idIdx = it.getColumnIndex("id")
                val slotIdx = it.getColumnIndex("slotNumber")
                val nameIdx = it.getColumnIndex("name")
                val emailIdx = it.getColumnIndex("email")
                val passIdx = it.getColumnIndex("passwordHash")
                val instIdx = it.getColumnIndex("institution")
                val deptIdx = it.getColumnIndex("department")
                val semIdx = it.getColumnIndex("semester")
                val avIdx = it.getColumnIndex("avatarUri")
                val remIdx = it.getColumnIndex("rememberEmail")
                return database.entities.ProfileEntity(
                    id = if (idIdx >= 0) it.getLong(idIdx) else id,
                    slotNumber = if (slotIdx >= 0) it.getInt(slotIdx) else 1,
                    name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else "",
                    email = if (emailIdx >= 0) it.getString(emailIdx) ?: "" else "",
                    passwordHash = if (passIdx >= 0) it.getString(passIdx) ?: "" else "",
                    institution = if (instIdx >= 0) it.getString(instIdx) ?: "" else "",
                    department = if (deptIdx >= 0) it.getString(deptIdx) ?: "" else "",
                    semester = if (semIdx >= 0) it.getString(semIdx) ?: "" else "",
                    avatarUri = if (avIdx >= 0) it.getString(avIdx) else null,
                    rememberEmail = if (remIdx >= 0) it.getInt(remIdx) == 1 else false
                )
            }
        }
        return null
    }

    fun getProfileBySlot(slotNumber: Int): database.entities.ProfileEntity? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM profiles WHERE slotNumber = ? LIMIT 1", arrayOf(slotNumber.toString()))
        cursor.use {
            if (it.moveToNext()) {
                val idIdx = it.getColumnIndex("id")
                val slotIdx = it.getColumnIndex("slotNumber")
                val nameIdx = it.getColumnIndex("name")
                val emailIdx = it.getColumnIndex("email")
                val passIdx = it.getColumnIndex("passwordHash")
                val instIdx = it.getColumnIndex("institution")
                val deptIdx = it.getColumnIndex("department")
                val semIdx = it.getColumnIndex("semester")
                val avIdx = it.getColumnIndex("avatarUri")
                val remIdx = it.getColumnIndex("rememberEmail")
                return database.entities.ProfileEntity(
                    id = if (idIdx >= 0) it.getLong(idIdx) else 0L,
                    slotNumber = if (slotIdx >= 0) it.getInt(slotIdx) else slotNumber,
                    name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else "",
                    email = if (emailIdx >= 0) it.getString(emailIdx) ?: "" else "",
                    passwordHash = if (passIdx >= 0) it.getString(passIdx) ?: "" else "",
                    institution = if (instIdx >= 0) it.getString(instIdx) ?: "" else "",
                    department = if (deptIdx >= 0) it.getString(deptIdx) ?: "" else "",
                    semester = if (semIdx >= 0) it.getString(semIdx) ?: "" else "",
                    avatarUri = if (avIdx >= 0) it.getString(avIdx) else null,
                    rememberEmail = if (remIdx >= 0) it.getInt(remIdx) == 1 else false
                )
            }
        }
        return null
    }

    fun getProfileCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM profiles", null)
        cursor.use {
            if (it.moveToNext()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    fun insertProfile(profile: database.entities.ProfileEntity): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("slotNumber", profile.slotNumber)
            put("name", profile.name)
            put("email", profile.email)
            put("passwordHash", profile.passwordHash)
            put("institution", profile.institution)
            put("department", profile.department)
            put("semester", profile.semester)
            put("avatarUri", profile.avatarUri)
            put("rememberEmail", if (profile.rememberEmail) 1 else 0)
        }
        return db.insertWithOnConflict("profiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateProfile(profile: database.entities.ProfileEntity) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("slotNumber", profile.slotNumber)
            put("name", profile.name)
            put("email", profile.email)
            put("passwordHash", profile.passwordHash)
            put("institution", profile.institution)
            put("department", profile.department)
            put("semester", profile.semester)
            put("avatarUri", profile.avatarUri)
            put("rememberEmail", if (profile.rememberEmail) 1 else 0)
        }
        db.update("profiles", cv, "id = ?", arrayOf(profile.id.toString()))
    }

    fun deleteProfile(profile: database.entities.ProfileEntity) {
        val db = writableDatabase
        db.delete("profiles", "id = ?", arrayOf(profile.id.toString()))
    }

    interface WalletDao {
        fun deleteByProfileId(profileId: Long)
    }

    interface TaskDao : database.dao.TaskDao {
        override fun deleteByProfileId(profileId: Long): Int
    }

    val walletDao: WalletDao by lazy {
        object : WalletDao {
            override fun deleteByProfileId(profileId: Long) {
                val db = writableDatabase
                db.delete("wallet_buckets", "profile_id = ?", arrayOf(profileId.toString()))
                db.delete("cards", "profile_id = ?", arrayOf(profileId.toString()))
                db.delete("transactions", "profile_id = ?", arrayOf(profileId.toString()))
                db.delete("subscriptions", "profile_id = ?", arrayOf(profileId.toString()))
                db.delete("dues_debts", "profile_id = ?", arrayOf(profileId.toString()))
            }
        }
    }

    val taskDao: TaskDao by lazy {
        object : TaskDao {
            override fun getAllTasks(filter: String, isGoal: Boolean?, profileId: Long): List<TaskItem> {
                return this@DatabaseHelper.getAllTasks(filter, isGoal, profileId)
            }
            override fun insertTask(task: TaskItem): Long = this@DatabaseHelper.insertTask(task)
            override fun updateTask(task: TaskItem): Int = this@DatabaseHelper.updateTask(task)
            override fun deleteTask(id: Long): Int = this@DatabaseHelper.deleteTask(id)
            override fun deleteByProfileId(profileId: Long): Int {
                return writableDatabase.delete("tasks", "profile_id = ?", arrayOf(profileId.toString()))
            }
        }
    }

    val cardDao: database.dao.CardDao by lazy {
        object : database.dao.CardDao {
            override fun getCards(profileId: Long): List<CardItem> = getAllCards(profileId)
            override fun insertCard(card: CardItem): Long = this@DatabaseHelper.insertCard(card)
            override fun updateCardBalance(cardId: Long, newBalance: Double): Int = this@DatabaseHelper.updateCardBalance(cardId, newBalance)
            override fun deleteCard(cardId: Long): Int = this@DatabaseHelper.deleteCard(cardId)
        }
    }

    val studentSubjectDao: database.dao.StudentSubjectDao get() = courseDao

    fun deleteWorkspaceData(profileId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            timetableDao.deleteByProfileId(profileId)
            walletDao.deleteByProfileId(profileId)
            taskDao.deleteByProfileId(profileId)
            attendanceDao.deleteByProfileId(profileId)
            holidayDao.deleteByProfileId(profileId)
            db.delete("subject_work_items", "profile_id = ?", arrayOf(profileId.toString()))
            db.delete("student_subjects", "profile_id = ?", arrayOf(profileId.toString()))
            db.delete("notes", "profile_id = ?", arrayOf(profileId.toString()))
            db.delete("work_orders", "profile_id = ?", arrayOf(profileId.toString()))
            db.delete("work_clients", "profile_id = ?", arrayOf(profileId.toString()))
            db.delete("profiles", "id = ?", arrayOf(profileId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun promoteProfileToSlot1(fromProfileId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val slot1 = getProfileBySlot(1)
            val fromProfile = getProfileById(fromProfileId)
            val slot1Id = slot1?.id ?: 1L

            if (fromProfile != null) {
                // Delete existing Slot 1 data
                deleteWorkspaceData(slot1Id)

                // Re-link all records with profile_id = fromProfileId to slot1Id
                val tables = listOf(
                    "classes", "attendance_records", "holiday_records", "tasks",
                    "transactions", "wallet_buckets", "cards", "subscriptions",
                    "dues_debts", "subject_work_items", "student_subjects", "notes",
                    "work_orders", "work_clients"
                )
                for (table in tables) {
                    db.execSQL("UPDATE $table SET profile_id = ? WHERE profile_id = ?", arrayOf(slot1Id, fromProfileId))
                }

                // Copy avatar file if present
                var newAvatarUri: String? = null
                val oldFile = java.io.File(appContext.filesDir, "avatar_profile_${fromProfileId}.jpg")
                val newFile = java.io.File(appContext.filesDir, "avatar_profile_${slot1Id}.jpg")
                if (oldFile.exists()) {
                    try {
                        oldFile.copyTo(newFile, overwrite = true)
                        newAvatarUri = android.net.Uri.fromFile(newFile).toString() + "?t=" + System.currentTimeMillis()
                    } catch (_: Exception) {}
                }

                // Update Slot 1 entity with fromProfile's details
                val updatedSlot1 = fromProfile.copy(
                    id = slot1Id,
                    slotNumber = 1,
                    avatarUri = newAvatarUri ?: fromProfile.avatarUri
                )
                val cv = android.content.ContentValues().apply {
                    put("id", slot1Id)
                    put("slotNumber", 1)
                    put("name", updatedSlot1.name)
                    put("email", updatedSlot1.email)
                    put("passwordHash", updatedSlot1.passwordHash)
                    put("institution", updatedSlot1.institution)
                    put("department", updatedSlot1.department)
                    put("semester", updatedSlot1.semester)
                    put("avatarUri", updatedSlot1.avatarUri)
                    put("rememberEmail", if (updatedSlot1.rememberEmail) 1 else 0)
                }
                db.insertWithOnConflict("profiles", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)

                // Delete old profile from profiles table
                db.delete("profiles", "id = ?", arrayOf(fromProfileId.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    val profileDao: database.dao.ProfileDao by lazy {
        object : database.dao.ProfileDao {
            override fun getAllProfiles(): List<database.entities.ProfileEntity> {
                return this@DatabaseHelper.getAllProfiles()
            }

            override fun getProfileById(id: Long): database.entities.ProfileEntity? {
                return this@DatabaseHelper.getProfileById(id)
            }

            override fun getProfileBySlot(slotNumber: Int): database.entities.ProfileEntity? {
                return this@DatabaseHelper.getProfileBySlot(slotNumber)
            }

            override fun getProfileCount(): Int {
                return this@DatabaseHelper.getProfileCount()
            }

            override fun insertProfile(profile: database.entities.ProfileEntity): Long {
                return this@DatabaseHelper.insertProfile(profile)
            }

            override fun updateProfile(profile: database.entities.ProfileEntity) {
                this@DatabaseHelper.updateProfile(profile)
            }

            override fun deleteProfile(profile: database.entities.ProfileEntity) {
                this@DatabaseHelper.deleteProfile(profile)
            }
        }
    }

    // ==========================================
    // STUDENT PORTAL (Sections, Homework, Exams)
    // ==========================================
    fun getAllStudentSubjects(profileId: Long = getActiveProfileId()): List<StudentSubject> {
        val list = mutableListOf<StudentSubject>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM student_subjects WHERE profile_id = ? OR profile_id = 0 OR profile_id IS NULL ORDER BY id ASC", arrayOf(profileId.toString()))
            cursor.use {
                while (it.moveToNext()) {
                    val codeIdx = it.getColumnIndexSafe("subject_code").takeIf { idx -> idx != -1 } ?: it.getColumnIndexSafe("code")
                    val nameIdx = it.getColumnIndexSafe("subject_name").takeIf { idx -> idx != -1 } ?: it.getColumnIndexSafe("name")
                    val teacherIdx = it.getColumnIndexSafe("teacher_name").takeIf { idx -> idx != -1 } ?: it.getColumnIndexSafe("instructor_name")
                    val roomIdx = it.getColumnIndexSafe("default_room").takeIf { idx -> idx != -1 } ?: it.getColumnIndexSafe("room")
                    val creditIdx = it.getColumnIndexSafe("credits")

                    val subjectCode = if (codeIdx != -1) it.getString(codeIdx) ?: "" else it.safeGetString("code")
                    val subjectName = if (nameIdx != -1) it.getString(nameIdx) ?: "" else it.safeGetString("name")
                    val teacherName = if (teacherIdx != -1) it.getString(teacherIdx) ?: "" else it.safeGetString("instructor_name", "instructorName")
                    val defaultRoom = if (roomIdx != -1) it.getString(roomIdx) ?: "" else it.safeGetString("room", "default_room")
                    val credits = if (creditIdx != -1) it.getInt(creditIdx) else 1

                    list.add(
                        StudentSubject(
                            id = it.safeGetLong("id"),
                            profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                            name = subjectName,
                            code = subjectCode,
                            colorHex = it.safeGetString("color_hex", "colorHex", default = "#2563EB"),
                            notes = it.safeGetString("notes"),
                            credits = credits,
                            instructorName = teacherName,
                            instructorEmail = it.safeGetString("instructor_email", "instructorEmail"),
                            instructorPhone = it.safeGetString("instructor_phone", "instructorPhone"),
                            syllabusText = it.safeGetString("syllabus_text", "syllabusText"),
                            syllabusPath = it.safeGetString("syllabus_path", "syllabusPath"),
                            room = defaultRoom
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading student subjects for profile $profileId", e)
        }
        return list
    }

    fun getStudentSubjects(profileId: Long = getActiveProfileId()): List<StudentSubject> = getAllStudentSubjects(profileId)

    fun getAllSubjectsSync(): List<StudentSubject> = getAllStudentSubjects()

    fun getStudentSubjectById(subjectId: Long): StudentSubject? {
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM student_subjects WHERE id = ?", arrayOf(subjectId.toString()))
            cursor.use {
                if (it.moveToFirst()) {
                    val codeIdx = it.getColumnIndexSafe("subject_code").takeIf { idx -> idx != -1 } ?: it.getColumnIndexSafe("code")
                    val nameIdx = it.getColumnIndexSafe("subject_name").takeIf { idx -> idx != -1 } ?: it.getColumnIndexSafe("name")
                    val teacherIdx = it.getColumnIndexSafe("teacher_name").takeIf { idx -> idx != -1 } ?: it.getColumnIndexSafe("instructor_name")
                    val roomIdx = it.getColumnIndexSafe("default_room").takeIf { idx -> idx != -1 } ?: it.getColumnIndexSafe("room")
                    val creditIdx = it.getColumnIndexSafe("credits")

                    val subjectCode = if (codeIdx != -1) it.getString(codeIdx) ?: "" else it.safeGetString("code")
                    val subjectName = if (nameIdx != -1) it.getString(nameIdx) ?: "" else it.safeGetString("name")
                    val teacherName = if (teacherIdx != -1) it.getString(teacherIdx) ?: "" else it.safeGetString("instructor_name", "instructorName")
                    val defaultRoom = if (roomIdx != -1) it.getString(roomIdx) ?: "" else it.safeGetString("room", "default_room")
                    val credits = if (creditIdx != -1) it.getInt(creditIdx) else 1

                    return StudentSubject(
                        id = it.safeGetLong("id", default = subjectId),
                        profileId = it.safeGetLong("profile_id", "profileId", default = 1L),
                        name = subjectName,
                        code = subjectCode,
                        colorHex = it.safeGetString("color_hex", "colorHex", default = "#2563EB"),
                        notes = it.safeGetString("notes"),
                        credits = credits,
                        instructorName = teacherName,
                        instructorEmail = it.safeGetString("instructor_email", "instructorEmail"),
                        instructorPhone = it.safeGetString("instructor_phone", "instructorPhone"),
                        syllabusText = it.safeGetString("syllabus_text", "syllabusText"),
                        syllabusPath = it.safeGetString("syllabus_path", "syllabusPath"),
                        room = defaultRoom
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading student subject $subjectId", e)
        }
        return null
    }

    fun insertStudentSubject(subject: StudentSubject, autoTrackAttendance: Boolean = false): Long {
        val db = writableDatabase
        val pId = if (subject.profileId > 0) subject.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("profile_id", pId)
            put("name", subject.name)
            put("code", subject.code)
            put("color_hex", subject.colorHex)
            put("notes", subject.notes)
            put("credits", subject.credits)
            put("instructor_name", subject.instructorName)
            put("instructor_email", subject.instructorEmail)
            put("instructor_phone", subject.instructorPhone)
            put("syllabus_text", subject.syllabusText)
            put("syllabus_path", subject.syllabusPath)
        }
        if (autoTrackAttendance) {
            ensureSubjectAttendance(subject.name)
        }
        return db.insertWithOnConflict("student_subjects", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateStudentSubject(subject: StudentSubject): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", subject.name)
            put("code", subject.code)
            put("color_hex", subject.colorHex)
            put("notes", subject.notes)
            put("credits", subject.credits)
            put("instructor_name", subject.instructorName)
            put("instructor_email", subject.instructorEmail)
            put("instructor_phone", subject.instructorPhone)
            put("syllabus_text", subject.syllabusText)
            put("syllabus_path", subject.syllabusPath)
        }
        return db.update("student_subjects", cv, "id = ?", arrayOf(subject.id.toString()))
    }

    fun deleteCourseCascade(courseId: Long, courseCode: String = "", profileId: Long = getActiveProfileId()): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val course = getStudentSubjectById(courseId)
            val courseName = course?.name ?: ""
            val code = if (courseCode.isNotBlank()) courseCode else (course?.code ?: "")

            // 1. Delete Timetable Classes matching course name or course code
            if (courseName.isNotBlank() && code.isNotBlank()) {
                db.delete("classes", "profile_id = ? AND (subject_name = ? OR subject_name LIKE ?)", arrayOf(profileId.toString(), courseName, "%$code%"))
            } else if (courseName.isNotBlank()) {
                db.delete("classes", "profile_id = ? AND subject_name = ?", arrayOf(profileId.toString(), courseName))
            } else if (code.isNotBlank()) {
                db.delete("classes", "profile_id = ? AND subject_name LIKE ?", arrayOf(profileId.toString(), "%$code%"))
            }

            // 2. Delete subject_work_items (exams, assignments, homework)
            db.delete("subject_work_items", "subject_id = ?", arrayOf(courseId.toString()))

            // 3. Delete subject_materials
            db.delete("subject_materials", "subject_id = ?", arrayOf(courseId.toString()))

            // 4. Delete attendance_records matching course name
            if (courseName.isNotBlank()) {
                try {
                    db.delete("attendance_records", "subjectName = ? AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL)", arrayOf(courseName, profileId.toString()))
                } catch (_: Exception) {}
            }

            // 5. Delete student_subjects entry
            db.delete("student_subjects", "id = ?", arrayOf(courseId.toString()))

            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            db.endTransaction()
        }
    }

    fun deleteStudentSubject(id: Long): Int {
        val course = getStudentSubjectById(id)
        val success = deleteCourseCascade(id, course?.code ?: "", course?.profileId ?: getActiveProfileId())
        return if (success) 1 else 0
    }

    // ==========================================
    // SUBJECT MATERIALS (PDFs, Images, Syllabus)
    // ==========================================
    fun getMaterialsForSubject(subjectId: Long): List<SubjectMaterial> {
        val list = mutableListOf<SubjectMaterial>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM subject_materials WHERE subject_id = ? ORDER BY id DESC",
                arrayOf(subjectId.toString())
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        SubjectMaterial(
                            id = it.safeGetLong("id"),
                            subjectId = it.safeGetLong("subject_id", "subjectId", default = subjectId),
                            title = it.safeGetString("title"),
                            type = it.safeGetString("type", default = "PDF_DOCUMENT"),
                            filePath = it.safeGetString("file_path", "filePath"),
                            createdAt = it.safeGetString("created_at", "createdAt"),
                            notes = it.safeGetString("notes")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading materials for subject $subjectId", e)
        }
        return list
    }

    fun insertSubjectMaterial(material: SubjectMaterial): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("subject_id", material.subjectId)
            put("title", material.title)
            put("type", material.type)
            put("file_path", material.filePath)
            put("created_at", material.createdAt)
            put("notes", material.notes)
        }
        return db.insertWithOnConflict("subject_materials", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteSubjectMaterial(id: Long): Int {
        val db = writableDatabase
        return db.delete("subject_materials", "id = ?", arrayOf(id.toString()))
    }

    // ==========================================
    // EXPENSE ANALYTICS
    // ==========================================
    fun getCategoryExpenses(monthPrefix: String? = null): List<CategoryExpense> {
        val list = mutableListOf<CategoryExpense>()
        val db = readableDatabase
        val query = if (!monthPrefix.isNullOrEmpty()) {
            "SELECT category, SUM(amount) as cat_sum FROM transactions WHERE type = 'EXPENSE' AND date LIKE ? GROUP BY category ORDER BY cat_sum DESC"
        } else {
            "SELECT category, SUM(amount) as cat_sum FROM transactions WHERE type = 'EXPENSE' GROUP BY category ORDER BY cat_sum DESC"
        }
        val args = if (!monthPrefix.isNullOrEmpty()) arrayOf("$monthPrefix%") else null
        val cursor = db.rawQuery(query, args)
        var total = 0.0
        val rawList = mutableListOf<Pair<String, Double>>()
        cursor.use {
            while (it.moveToNext()) {
                val cat = it.getString(0) ?: "Uncategorized"
                val sum = it.getDouble(1)
                rawList.add(Pair(cat, sum))
                total += sum
            }
        }
        val fallbackColors = listOf("#0284C7", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6", "#64748B")
        for ((idx, pair) in rawList.withIndex()) {
            val pct = if (total > 0) (pair.second / total * 100).toFloat() else 0f
            val customCat = getTransactionCategoryByName(pair.first)
            val color = customCat?.colorHex ?: fallbackColors[idx % fallbackColors.size]
            list.add(CategoryExpense(pair.first, pair.second, pct, color))
        }
        return list
    }

    fun getItemsForSubject(subjectId: Long, itemTypeFilter: String? = null, profileId: Long = getActiveProfileId()): List<SubjectWorkItem> {
        val list = mutableListOf<SubjectWorkItem>()
        try {
            val db = readableDatabase
            val query = if (itemTypeFilter != null && itemTypeFilter != "ALL") {
                "SELECT * FROM subject_work_items WHERE subject_id = ? AND item_type = ? AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) ORDER BY id DESC"
            } else {
                "SELECT * FROM subject_work_items WHERE subject_id = ? AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) ORDER BY id DESC"
            }
            val args = if (itemTypeFilter != null && itemTypeFilter != "ALL") {
                arrayOf(subjectId.toString(), itemTypeFilter, profileId.toString())
            } else {
                arrayOf(subjectId.toString(), profileId.toString())
            }
            val cursor = db.rawQuery(query, args)
            cursor.use {
                val attachUriIdx = it.getColumnIndex("attachment_uri")
                val attachNameIdx = it.getColumnIndex("attachment_name")
                while (it.moveToNext()) {
                    list.add(
                        SubjectWorkItem(
                            id = it.safeGetLong("id"),
                            profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                            subjectId = it.safeGetLong("subject_id", "subjectId", default = subjectId),
                            itemType = it.safeGetString("item_type", "itemType", default = "HOMEWORK"),
                            title = it.safeGetString("title"),
                            description = it.safeGetString("description"),
                            startDate = it.safeGetString("start_date", "startDate"),
                            endDate = it.safeGetString("end_date", "endDate"),
                            scoreOrMarks = it.safeGetString("score_or_marks", "scoreOrMarks"),
                            status = it.safeGetString("status", default = "PENDING"),
                            attachmentPath = it.safeGetString("attachment_path", "attachmentPath"),
                            examType = it.safeGetString("exam_type", "examType"),
                            attachmentUri = if (attachUriIdx >= 0) it.getString(attachUriIdx) else null,
                            attachmentName = if (attachNameIdx >= 0) it.getString(attachNameIdx) else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading items for subject $subjectId", e)
        }
        return list
    }

    fun getAllExams(examTypeFilter: String? = null, profileId: Long = getActiveProfileId()): List<Pair<SubjectWorkItem, StudentSubject>> {
        val list = mutableListOf<Pair<SubjectWorkItem, StudentSubject>>()
        try {
            val subjects = getAllStudentSubjects(profileId).associateBy { it.id }
            val db = readableDatabase
            val query = if (!examTypeFilter.isNullOrBlank()) {
                "SELECT * FROM subject_work_items WHERE item_type = 'EXAM' AND exam_type = ? AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) ORDER BY end_date ASC, start_date ASC"
            } else {
                "SELECT * FROM subject_work_items WHERE item_type = 'EXAM' AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) ORDER BY end_date ASC, start_date ASC"
            }
            val args = if (!examTypeFilter.isNullOrBlank()) arrayOf(examTypeFilter, profileId.toString()) else arrayOf(profileId.toString())
            val cursor = db.rawQuery(query, args)
            cursor.use {
                val attachUriIdx = it.getColumnIndex("attachment_uri")
                val attachNameIdx = it.getColumnIndex("attachment_name")
                while (it.moveToNext()) {
                    val subjectId = it.safeGetLong("subject_id", "subjectId")
                    val subject = subjects[subjectId] ?: StudentSubject(id = subjectId, profileId = profileId, name = "Subject")
                    val item = SubjectWorkItem(
                        id = it.safeGetLong("id"),
                        profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                        subjectId = subjectId,
                        itemType = it.safeGetString("item_type", "itemType", default = "EXAM"),
                        title = it.safeGetString("title"),
                        description = it.safeGetString("description"),
                        startDate = it.safeGetString("start_date", "startDate"),
                        endDate = it.safeGetString("end_date", "endDate"),
                        scoreOrMarks = it.safeGetString("score_or_marks", "scoreOrMarks"),
                        status = it.safeGetString("status", default = "PENDING"),
                        attachmentPath = it.safeGetString("attachment_path", "attachmentPath"),
                        examType = it.safeGetString("exam_type", "examType"),
                        attachmentUri = if (attachUriIdx >= 0) it.getString(attachUriIdx) else null,
                        attachmentName = if (attachNameIdx >= 0) it.getString(attachNameIdx) else null
                    )
                    list.add(Pair(item, subject))
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading all exams for profile $profileId", e)
        }
        return list
    }

    fun getTodayHomeworkDue(todayDate: String, profileId: Long = getActiveProfileId()): List<Pair<SubjectWorkItem, StudentSubject>> {
        val list = mutableListOf<Pair<SubjectWorkItem, StudentSubject>>()
        try {
            val subjects = getAllStudentSubjects(profileId).associateBy { it.id }
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM subject_work_items WHERE status != 'DONE' AND (item_type = 'HOMEWORK' OR item_type = 'ASSIGNMENT') AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) AND (end_date = ? OR (end_date = '' AND start_date = ?))",
                arrayOf(profileId.toString(), todayDate, todayDate)
            )
            cursor.use {
                while (it.moveToNext()) {
                    val subjectId = it.safeGetLong("subject_id", "subjectId")
                    val subject = subjects[subjectId] ?: StudentSubject(id = subjectId, profileId = profileId, name = "Subject")
                    list.add(
                        Pair(
                            SubjectWorkItem(
                                id = it.safeGetLong("id"),
                                profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                                subjectId = subjectId,
                                itemType = it.safeGetString("item_type", "itemType", default = "HOMEWORK"),
                                title = it.safeGetString("title"),
                                description = it.safeGetString("description"),
                                startDate = it.safeGetString("start_date", "startDate"),
                                endDate = it.safeGetString("end_date", "endDate"),
                                scoreOrMarks = it.safeGetString("score_or_marks", "scoreOrMarks"),
                                status = it.safeGetString("status", default = "PENDING"),
                                attachmentPath = it.safeGetString("attachment_path", "attachmentPath"),
                                examType = it.safeGetString("exam_type", "examType")
                            ),
                            subject
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading today's homework for profile $profileId", e)
        }
        return list
    }

    fun getTodayClassTests(todayDate: String, profileId: Long = getActiveProfileId()): List<Pair<SubjectWorkItem, StudentSubject>> {
        val list = mutableListOf<Pair<SubjectWorkItem, StudentSubject>>()
        try {
            val subjects = getAllStudentSubjects(profileId).associateBy { it.id }
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM subject_work_items WHERE item_type = 'EXAM' AND (exam_type = 'CLASS_TEST_QUIZ' OR exam_type = '') AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) AND (end_date = ? OR start_date = ?)",
                arrayOf(profileId.toString(), todayDate, todayDate)
            )
            cursor.use {
                while (it.moveToNext()) {
                    val subjectId = it.safeGetLong("subject_id", "subjectId")
                    val subject = subjects[subjectId] ?: StudentSubject(id = subjectId, profileId = profileId, name = "Subject")
                    list.add(
                        Pair(
                            SubjectWorkItem(
                                id = it.safeGetLong("id"),
                                profileId = it.safeGetLong("profile_id", "profileId", default = profileId),
                                subjectId = subjectId,
                                itemType = it.safeGetString("item_type", "itemType", default = "EXAM"),
                                title = it.safeGetString("title"),
                                description = it.safeGetString("description"),
                                startDate = it.safeGetString("start_date", "startDate"),
                                endDate = it.safeGetString("end_date", "endDate"),
                                scoreOrMarks = it.safeGetString("score_or_marks", "scoreOrMarks"),
                                status = it.safeGetString("status", default = "PENDING"),
                                attachmentPath = it.safeGetString("attachment_path", "attachmentPath"),
                                examType = it.safeGetString("exam_type", "examType")
                            ),
                            subject
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading today's class tests for profile $profileId", e)
        }
        return list
    }

    fun insertSubjectWorkItem(item: SubjectWorkItem): Long {
        val db = writableDatabase
        val pId = if (item.profileId > 0) item.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("profile_id", pId)
            put("subject_id", item.subjectId)
            put("item_type", item.itemType)
            put("title", item.title)
            put("description", item.description)
            put("start_date", item.startDate)
            put("end_date", item.endDate)
            put("score_or_marks", item.scoreOrMarks)
            put("status", item.status)
            put("attachment_path", item.attachmentPath)
            put("exam_type", item.examType)
            put("attachment_uri", item.attachmentUri ?: "")
            put("attachment_name", item.attachmentName ?: "")
        }
        return db.insertWithOnConflict("subject_work_items", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateSubjectWorkItemStatus(id: Long, status: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("status", status) }
        db.update("subject_work_items", cv, "id = ?", arrayOf(id.toString()))
    }

    fun updateSubjectWorkItem(item: SubjectWorkItem): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("subject_id", item.subjectId)
            put("item_type", item.itemType)
            put("title", item.title)
            put("description", item.description)
            put("start_date", item.startDate)
            put("end_date", item.endDate)
            put("score_or_marks", item.scoreOrMarks)
            put("status", item.status)
            put("attachment_path", item.attachmentPath)
            put("exam_type", item.examType)
            put("attachment_uri", item.attachmentUri ?: "")
            put("attachment_name", item.attachmentName ?: "")
        }
        return db.update("subject_work_items", cv, "id = ?", arrayOf(item.id.toString()))
    }

    fun deleteSubjectWorkItem(id: Long): Int {
        val db = writableDatabase
        return db.delete("subject_work_items", "id = ?", arrayOf(id.toString()))
    }

    fun deleteById(id: Long): Int = deleteSubjectWorkItem(id)
    fun deleteExamById(id: Long): Int = deleteSubjectWorkItem(id)

    // ==========================================
    // WORK PORTAL (Orders & Jobs)
    // ==========================================
    fun getAllWorkOrders(): List<WorkOrder> {
        val list = mutableListOf<WorkOrder>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM work_orders ORDER BY id DESC", null)
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        WorkOrder(
                            id = it.safeGetLong("id"),
                            title = it.safeGetString("title"),
                            clientName = it.safeGetString("client_name", "clientName"),
                            quantity = it.safeGetInt("quantity"),
                            unitPrice = it.safeGetDouble("unit_price", "unitPrice"),
                            totalPrice = it.safeGetDouble("total_price", "totalPrice"),
                            startDate = it.safeGetString("start_date", "startDate"),
                            endDate = it.safeGetString("end_date", "endDate"),
                            status = it.safeGetString("status"),
                            notes = it.safeGetString("notes"),
                            attachmentPath = it.safeGetString("attachment_path", "attachmentPath")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading work orders", e)
        }
        return list
    }

    fun insertWorkOrder(order: WorkOrder): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", order.title)
            put("client_name", order.clientName)
            put("quantity", order.quantity)
            put("unit_price", order.unitPrice)
            put("total_price", order.totalPrice)
            put("start_date", order.startDate)
            put("end_date", order.endDate)
            put("status", order.status)
            put("notes", order.notes)
            put("attachment_path", order.attachmentPath)
        }
        return db.insertWithOnConflict("work_orders", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateWorkOrder(order: WorkOrder): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", order.title)
            put("client_name", order.clientName)
            put("quantity", order.quantity)
            put("unit_price", order.unitPrice)
            put("total_price", order.totalPrice)
            put("start_date", order.startDate)
            put("end_date", order.endDate)
            put("status", order.status)
            put("notes", order.notes)
            put("attachment_path", order.attachmentPath)
        }
        return db.update("work_orders", cv, "id = ?", arrayOf(order.id.toString()))
    }

    fun updateWorkOrderStatus(orderId: Long, status: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("status", status) }
        db.update("work_orders", cv, "id = ?", arrayOf(orderId.toString()))
    }

    fun deleteWorkOrder(orderId: Long): Int {
        val db = writableDatabase
        return db.delete("work_orders", "id = ?", arrayOf(orderId.toString()))
    }

    // ==========================================
    // NOTES & CONTACTS
    // ==========================================
    fun getAllNotes(profileId: Long = getActiveProfileId()): List<NoteItem> {
        val list = mutableListOf<NoteItem>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM notes WHERE (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) ORDER BY id DESC", arrayOf(profileId.toString()))
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        NoteItem(
                            id = it.safeGetLong("id"),
                            title = it.safeGetString("title"),
                            content = it.safeGetString("content"),
                            category = it.safeGetString("category"),
                            colorHex = it.safeGetString("color_hex", "colorHex"),
                            updatedAt = it.safeGetString("updated_at", "updatedAt"),
                            profileId = it.safeGetLong("profile_id", "profileId", default = profileId)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading notes for profile $profileId", e)
        }
        return list
    }

    fun insertNote(note: NoteItem): Long {
        val db = writableDatabase
        val pId = if (note.profileId > 0) note.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("title", note.title)
            put("content", note.content)
            put("category", note.category)
            put("color_hex", note.colorHex)
            put("updated_at", note.updatedAt)
            put("profile_id", pId)
        }
        return db.insertWithOnConflict("notes", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateNote(note: NoteItem): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", note.title)
            put("content", note.content)
            put("category", note.category)
            put("color_hex", note.colorHex)
            put("updated_at", note.updatedAt)
        }
        return db.update("notes", cv, "id = ?", arrayOf(note.id.toString()))
    }

    fun deleteNote(id: Long): Int {
        val db = writableDatabase
        return db.delete("notes", "id = ?", arrayOf(id.toString()))
    }

    fun getAllContactsGroups(): List<ContactGroupItem> {
        val list = mutableListOf<ContactGroupItem>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM contacts_groups ORDER BY id DESC", null)
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        ContactGroupItem(
                            id = it.safeGetLong("id"),
                            name = it.safeGetString("name"),
                            type = it.safeGetString("type"),
                            platform = it.safeGetString("platform"),
                            targetLink = it.safeGetString("target_link", "targetLink"),
                            notes = it.safeGetString("notes"),
                            phoneNumber = it.safeGetString("phone_number", "phoneNumber"),
                            role = it.safeGetString("role", default = "Classmate"),
                            scope = it.safeGetString("scope", default = "SHARED")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading contacts groups", e)
        }
        return list
    }

    fun getContactsForPortal(isStudent: Boolean, roleFilter: String? = null): List<ContactGroupItem> {
        val list = mutableListOf<ContactGroupItem>()
        try {
            val db = readableDatabase
            val scopeClause = if (isStudent) {
                "(scope = 'STUDENT_ONLY' OR scope = 'SHARED' OR scope = '')"
            } else {
                "(scope = 'WORK_ONLY' OR scope = 'SHARED' OR scope = '')"
            }

            val query: String
            val args: Array<String>?
            if (!roleFilter.isNullOrBlank() && roleFilter != "ALL") {
                query = "SELECT * FROM contacts_groups WHERE $scopeClause AND role = ? ORDER BY id DESC"
                args = arrayOf(roleFilter)
            } else {
                query = "SELECT * FROM contacts_groups WHERE $scopeClause ORDER BY id DESC"
                args = null
            }

            val cursor = db.rawQuery(query, args)
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        ContactGroupItem(
                            id = it.safeGetLong("id"),
                            name = it.safeGetString("name"),
                            type = it.safeGetString("type"),
                            platform = it.safeGetString("platform"),
                            targetLink = it.safeGetString("target_link", "targetLink"),
                            notes = it.safeGetString("notes"),
                            phoneNumber = it.safeGetString("phone_number", "phoneNumber"),
                            role = it.safeGetString("role", default = "Classmate"),
                            scope = it.safeGetString("scope", default = "SHARED")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading contacts for portal (isStudent=$isStudent)", e)
        }
        return list
    }

    fun insertContactGroup(item: ContactGroupItem): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", item.name)
            put("type", item.type)
            put("platform", item.platform)
            put("target_link", item.targetLink)
            put("notes", item.notes)
            put("phone_number", item.phoneNumber)
            put("role", item.role)
            put("scope", item.scope)
        }
        return db.insertWithOnConflict("contacts_groups", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateContactGroup(item: ContactGroupItem): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", item.name)
            put("type", item.type)
            put("platform", item.platform)
            put("target_link", item.targetLink)
            put("notes", item.notes)
            put("phone_number", item.phoneNumber)
            put("role", item.role)
            put("scope", item.scope)
        }
        return db.update("contacts_groups", cv, "id = ?", arrayOf(item.id.toString()))
    }

    fun deleteContactGroup(id: Long): Int {
        val db = writableDatabase
        return db.delete("contacts_groups", "id = ?", arrayOf(id.toString()))
    }

    // ==========================================
    // BACKUP & CLOUD EXPORT / IMPORT (JSON)
    // ==========================================
    fun exportDatabaseToJson(): String {
        val root = JSONObject()
        val db = readableDatabase

        val tables = listOf(
            "app_settings", "cards", "transactions", "transaction_categories", "wallet_buckets", "subscriptions",
            "currency_rates", "tasks", "classes", "attendance", "attendance_records", "student_subjects",
            "subject_materials", "subject_work_items", "work_orders", "notes", "contacts_groups", "dues_debts",
            "profiles", "class_types", "assessment_categories"
        )

        for (table in tables) {
            val arr = JSONArray()
            val cursor = try { db.rawQuery("SELECT * FROM $table", null) } catch (_: Exception) { null }
            cursor?.use {
                val colCount = it.columnCount
                while (it.moveToNext()) {
                    val obj = JSONObject()
                    for (i in 0 until colCount) {
                        val colName = it.getColumnName(i)
                        when (it.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> obj.put(colName, it.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> obj.put(colName, it.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> obj.put(colName, it.getString(i))
                            Cursor.FIELD_TYPE_NULL -> obj.put(colName, JSONObject.NULL)
                            else -> obj.put(colName, it.getString(i))
                        }
                    }
                    arr.put(obj)
                }
            }
            root.put(table, arr)
        }

        // Export Base64 profile avatars
        val avatarsObj = JSONObject()
        try {
            val profiles = getAllProfiles()
            for (p in profiles) {
                val avatarFile = java.io.File(appContext.filesDir, "avatar_profile_${p.id}.jpg")
                val b64 = com.duorhythm.app.util.ImageUtils.encodeFileToBase64(avatarFile)
                if (!b64.isNullOrBlank()) {
                    avatarsObj.put(p.id.toString(), b64)
                    avatarsObj.put("slot_${p.slotNumber}", b64)
                }
            }
        } catch (_: Exception) {}
        root.put("profile_avatars", avatarsObj)

        root.put("exported_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        return root.toString(2)
    }

    fun importDatabaseFromJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            val db = writableDatabase
            db.beginTransaction()
            try {
                val tables = listOf(
                    "app_settings", "cards", "transactions", "transaction_categories", "wallet_buckets", "subscriptions",
                    "currency_rates", "tasks", "classes", "attendance", "attendance_records", "student_subjects",
                    "subject_materials", "subject_work_items", "work_orders", "notes", "contacts_groups", "dues_debts",
                    "profiles", "class_types", "assessment_categories"
                )
                for (table in tables) {
                    if (root.has(table)) {
                        try {
                            db.delete(table, null, null)
                        } catch (_: Exception) {}
                        val arr = root.getJSONArray(table)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val cv = ContentValues()
                            val keys = obj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = obj.get(key)
                                if (value is Int || value is Long) {
                                    cv.put(key, (value as Number).toLong())
                                } else if (value is Double || value is Float) {
                                    cv.put(key, (value as Number).toDouble())
                                } else if (value is String) {
                                    cv.put(key, value)
                                }
                            }
                            try {
                                db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Restore profile avatars from Base64
                if (root.has("profile_avatars")) {
                    try {
                        val avatarsObj = root.getJSONObject("profile_avatars")
                        val profiles = getAllProfiles()
                        for (p in profiles) {
                            var b64: String? = null
                            if (avatarsObj.has(p.id.toString())) {
                                b64 = avatarsObj.optString(p.id.toString())
                            } else if (avatarsObj.has("slot_${p.slotNumber}")) {
                                b64 = avatarsObj.optString("slot_${p.slotNumber}")
                            }

                            if (!b64.isNullOrBlank()) {
                                val destFile = java.io.File(appContext.filesDir, "avatar_profile_${p.id}.jpg")
                                if (com.duorhythm.app.util.ImageUtils.decodeBase64ToFile(b64, destFile)) {
                                    val updatedUri = android.net.Uri.fromFile(destFile).toString() + "?t=" + System.currentTimeMillis()
                                    val pCv = ContentValues().apply { put("avatarUri", updatedUri) }
                                    db.update("profiles", pCv, "id = ?", arrayOf(p.id.toString()))
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                db.setTransactionSuccessful()
                true
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun executeTransfer(
        fromBucketId: Long,
        toBucketId: Long,
        amount: Double,
        date: String,
        currency: String = "BDT",
        note: String = "",
        title: String = ""
    ): Boolean {
        if (fromBucketId <= 0L || toBucketId <= 0L || fromBucketId == toBucketId || amount <= 0.0) {
            return false
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val fromBucket = getWalletBucketById(fromBucketId) ?: return false
            val toBucket = getWalletBucketById(toBucketId) ?: return false

            val debitTitle = if (title.isNotBlank()) title else "Transfer to ${toBucket.name}"
            val creditTitle = if (title.isNotBlank()) title else "Transfer from ${fromBucket.name}"

            val debitTx = TransactionItem(
                title = debitTitle,
                amount = amount,
                type = "EXPENSE",
                category = "Transfer",
                date = date,
                cardId = 0L,
                currency = currency,
                notes = note,
                bucketId = fromBucketId
            )
            val creditTx = TransactionItem(
                title = creditTitle,
                amount = amount,
                type = "INCOME",
                category = "Transfer",
                date = date,
                cardId = 0L,
                currency = currency,
                notes = note,
                bucketId = toBucketId
            )

            insertTransaction(debitTx)
            insertTransaction(creditTx)

            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            db.endTransaction()
        }
    }

    fun ensureDuesDebtsTable(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS dues_debts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    person_name TEXT NOT NULL,
                    phone_or_contact TEXT DEFAULT '',
                    amount REAL NOT NULL,
                    due_date TEXT DEFAULT '',
                    type TEXT NOT NULL,
                    status TEXT DEFAULT 'UNPAID',
                    notes TEXT DEFAULT '',
                    created_at TEXT DEFAULT '',
                    profile_id INTEGER DEFAULT 1
                )"""
            )
            ensureColumnExists(db, "dues_debts", "profile_id", "INTEGER DEFAULT 1")
        } catch (_: Exception) {}
    }

    fun insertDueDebt(item: DueDebtItem): Long {
        val db = writableDatabase
        ensureDuesDebtsTable(db)
        val pId = if (item.profileId > 0) item.profileId else getActiveProfileId()
        val cv = ContentValues().apply {
            put("person_name", item.personName.trim())
            put("phone_or_contact", item.phoneOrContact.trim())
            put("amount", item.amount)
            put("due_date", item.dueDate.trim())
            put("type", item.type)
            put("status", item.status)
            put("notes", item.notes.trim())
            put("created_at", if (item.createdAt.isNotBlank()) item.createdAt else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            put("profile_id", pId)
        }
        return db.insertWithOnConflict("dues_debts", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateDueDebt(item: DueDebtItem): Boolean {
        val db = writableDatabase
        ensureDuesDebtsTable(db)
        val cv = ContentValues().apply {
            put("person_name", item.personName.trim())
            put("phone_or_contact", item.phoneOrContact.trim())
            put("amount", item.amount)
            put("due_date", item.dueDate.trim())
            put("type", item.type)
            put("status", item.status)
            put("notes", item.notes.trim())
        }
        return db.update("dues_debts", cv, "id = ?", arrayOf(item.id.toString())) > 0
    }

    fun deleteDueDebt(id: Long): Boolean {
        val db = writableDatabase
        ensureDuesDebtsTable(db)
        return db.delete("dues_debts", "id = ?", arrayOf(id.toString())) > 0
    }

    fun getAllDuesDebts(profileId: Long = getActiveProfileId()): List<DueDebtItem> {
        val list = mutableListOf<DueDebtItem>()
        val db = readableDatabase
        ensureDuesDebtsTable(db)
        try {
            val cursor = db.rawQuery("SELECT * FROM dues_debts WHERE (profile_id = ? OR profile_id = 0 OR profile_id IS NULL) ORDER BY status ASC, id DESC", arrayOf(profileId.toString()))
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        DueDebtItem(
                            id = it.safeGetLong("id"),
                            personName = it.safeGetString("person_name", "personName"),
                            phoneOrContact = it.safeGetString("phone_or_contact", "phoneOrContact"),
                            amount = it.safeGetDouble("amount"),
                            dueDate = it.safeGetString("due_date", "dueDate"),
                            type = it.safeGetString("type"),
                            status = it.safeGetString("status"),
                            notes = it.safeGetString("notes"),
                            createdAt = it.safeGetString("created_at", "createdAt"),
                            profileId = it.safeGetLong("profile_id", "profileId", default = profileId)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading dues debts for profile $profileId", e)
        }
        return list
    }

    fun getDueDebtById(id: Long): DueDebtItem? {
        val db = readableDatabase
        ensureDuesDebtsTable(db)
        try {
            val cursor = db.rawQuery("SELECT * FROM dues_debts WHERE id = ?", arrayOf(id.toString()))
            cursor.use {
                if (it.moveToFirst()) {
                    return DueDebtItem(
                        id = it.safeGetLong("id", default = id),
                        personName = it.safeGetString("person_name", "personName"),
                        phoneOrContact = it.safeGetString("phone_or_contact", "phoneOrContact"),
                        amount = it.safeGetDouble("amount"),
                        dueDate = it.safeGetString("due_date", "dueDate"),
                        type = it.safeGetString("type"),
                        status = it.safeGetString("status"),
                        notes = it.safeGetString("notes"),
                        createdAt = it.safeGetString("created_at", "createdAt"),
                        profileId = it.safeGetLong("profile_id", "profileId", default = 1L)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading due debt $id", e)
        }
        return null
    }

    fun getDuesDebts(profileId: Long = getActiveProfileId()): List<DueDebtItem> = getAllDuesDebts(profileId)

    fun settleDueDebt(
        id: Long,
        bucketId: Long? = null,
        recordTransaction: Boolean = false,
        note: String = "",
        currency: String = "BDT"
    ): Boolean {
        val db = writableDatabase
        ensureDuesDebtsTable(db)
        db.beginTransaction()
        try {
            val item = getDueDebtById(id) ?: return false
            val cv = ContentValues().apply {
                put("status", "SETTLED")
            }
            val updated = db.update("dues_debts", cv, "id = ?", arrayOf(id.toString()))
            if (updated <= 0) return false

            if (recordTransaction && bucketId != null && bucketId > 0) {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val tx = if (item.type == "LENT") {
                    // Money was lent to them, now received back -> INCOME into bucket
                    TransactionItem(
                        title = "Due Settled: ${item.personName}",
                        amount = item.amount,
                        type = "INCOME",
                        category = "Transfer",
                        date = today,
                        currency = currency,
                        notes = if (note.isNotBlank()) note else "Settled lent due from ${item.personName}",
                        bucketId = bucketId
                    )
                } else {
                    // Money was borrowed from them, now paid back -> EXPENSE from bucket
                    TransactionItem(
                        title = "Debt Paid: ${item.personName}",
                        amount = item.amount,
                        type = "EXPENSE",
                        category = "Bills",
                        date = today,
                        currency = currency,
                        notes = if (note.isNotBlank()) note else "Paid borrowed debt to ${item.personName}",
                        bucketId = bucketId
                    )
                }
                insertTransaction(tx)
            }

            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            db.endTransaction()
        }
    }

    fun getDuesDebtsSummary(profileId: Long = getActiveProfileId()): Pair<Double, Double> {
        val db = readableDatabase
        ensureDuesDebtsTable(db)
        var totalLentUnpaid = 0.0
        var totalBorrowedUnpaid = 0.0
        try {
            val cursorLent = db.rawQuery("SELECT SUM(amount) FROM dues_debts WHERE type = 'LENT' AND status = 'UNPAID' AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL)", arrayOf(profileId.toString()))
            cursorLent.use {
                if (it.moveToFirst() && !it.isNull(0)) totalLentUnpaid = it.getDouble(0)
            }
            val cursorBorrowed = db.rawQuery("SELECT SUM(amount) FROM dues_debts WHERE type = 'BORROWED' AND status = 'UNPAID' AND (profile_id = ? OR profile_id = 0 OR profile_id IS NULL)", arrayOf(profileId.toString()))
            cursorBorrowed.use {
                if (it.moveToFirst() && !it.isNull(0)) totalBorrowedUnpaid = it.getDouble(0)
            }
        } catch (e: Exception) {
            Log.e("DUO_DB_READ", "Failed reading dues debts summary for profile $profileId", e)
        }
        return Pair(totalLentUnpaid, totalBorrowedUnpaid)
    }
}
