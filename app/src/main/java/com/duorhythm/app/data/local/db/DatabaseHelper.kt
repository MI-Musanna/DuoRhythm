package com.duorhythm.app.data.local.db

import com.duorhythm.app.data.model.*
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper private constructor(private val appContext: Context) :
    SQLiteOpenHelper(appContext.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "lifesync.db"
        const val DATABASE_VERSION = 5

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

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS classes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
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
            """CREATE TABLE IF NOT EXISTS student_subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
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
                subject_id INTEGER NOT NULL,
                item_type TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT DEFAULT '',
                start_date TEXT DEFAULT '',
                end_date TEXT DEFAULT '',
                score_or_marks TEXT DEFAULT '',
                status TEXT DEFAULT 'PENDING',
                attachment_path TEXT DEFAULT '',
                exam_type TEXT DEFAULT ''
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

        seedInitialData(db)
    }

    fun dropAllTables(db: SQLiteDatabase) {
        val tables = listOf(
            "cards", "transactions", "currency_rates", "tasks", "task_categories",
            "transaction_categories", "classes", "attendance", "student_subjects",
            "subject_materials", "subject_work_items", "work_orders", "notes",
            "contacts_groups", "wallet_buckets", "subscriptions", "dues_debts", "app_settings"
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
                """CREATE TABLE IF NOT EXISTS app_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
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
    fun getAllCards(): List<CardItem> {
        val list = mutableListOf<CardItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM cards ORDER BY id ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CardItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        cardName = it.getString(it.getColumnIndexOrThrow("card_name")) ?: "",
                        bankName = it.getString(it.getColumnIndexOrThrow("bank_name")) ?: "",
                        cardNumber = it.getString(it.getColumnIndexOrThrow("card_number")) ?: "",
                        holderName = it.getString(it.getColumnIndexOrThrow("holder_name")) ?: "",
                        cardType = it.getString(it.getColumnIndexOrThrow("card_type")) ?: "VISA",
                        colorHex = it.getString(it.getColumnIndexOrThrow("color_hex")) ?: "#1E293B",
                        balance = it.getDouble(it.getColumnIndexOrThrow("balance"))
                    )
                )
            }
        }
        return list
    }

    fun insertCard(card: CardItem): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
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
    fun getAllTransactions(typeFilter: String? = null, cardIdFilter: Long? = null, bucketIdFilter: Long? = null): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        val db = readableDatabase
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

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

        val where = if (clauses.isNotEmpty()) "WHERE " + clauses.joinToString(" AND ") else ""
        val cursor = db.rawQuery("SELECT * FROM transactions $where ORDER BY date DESC, id DESC", args.toTypedArray())
        cursor.use {
            val bIdCol = it.getColumnIndex("bucket_id")
            while (it.moveToNext()) {
                val bId = if (bIdCol >= 0) it.getLong(bIdCol) else 0L
                list.add(
                    TransactionItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        type = it.getString(it.getColumnIndexOrThrow("type")) ?: "EXPENSE",
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "General",
                        date = it.getString(it.getColumnIndexOrThrow("date")) ?: "",
                        cardId = it.getLong(it.getColumnIndexOrThrow("card_id")),
                        currency = it.getString(it.getColumnIndexOrThrow("currency")) ?: "USD",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                        bucketId = bId
                    )
                )
            }
        }
        return list
    }

    fun insertTransaction(tx: TransactionItem): Long {
        val db = writableDatabase
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
        return id
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
        return db.delete("transactions", "id = ?", arrayOf(txId.toString()))
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
        return db.update("transactions", cv, "id = ?", arrayOf(tx.id.toString()))
    }

    fun clearAllTransactions(): Int {
        val db = writableDatabase
        return db.delete("transactions", null, null)
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

    fun ensureDefaultTransactionCategories(db: SQLiteDatabase) {
        ensureTransactionCategoriesTable(db)
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
                    val idCol = it.getColumnIndex("id")
                    val nameCol = it.getColumnIndex("name")
                    val typeCol = it.getColumnIndex("type")
                    val defCol = it.getColumnIndex("is_default")
                    val colorCol = it.getColumnIndex("color_hex")
                    val iconCol = it.getColumnIndex("icon")
                    return TransactionCategoryItem(
                        id = if (idCol >= 0) it.getLong(idCol) else 0L,
                        name = if (nameCol >= 0) it.getString(nameCol) ?: "" else "",
                        type = if (typeCol >= 0) it.getString(typeCol) ?: "BOTH" else "BOTH",
                        isDefault = if (defCol >= 0) it.getInt(defCol) == 1 else false,
                        colorHex = if (colorCol >= 0) it.getString(colorCol) ?: "#0284C7" else "#0284C7",
                        icon = if (iconCol >= 0) it.getString(iconCol) ?: "category" else "category"
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // ==========================================
    // WALLET BUCKETS CRUD
    // ==========================================
    fun getAllWalletBuckets(): List<WalletBucketItem> {
        val list = mutableListOf<WalletBucketItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM wallet_buckets ORDER BY id ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    WalletBucketItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        icon = it.getString(it.getColumnIndexOrThrow("icon")) ?: "wallet",
                        colorHex = it.getString(it.getColumnIndexOrThrow("color_hex")) ?: "#0284C7",
                        connectedAccounts = it.getString(it.getColumnIndexOrThrow("connected_accounts")) ?: "",
                        balance = it.getDouble(it.getColumnIndexOrThrow("balance")),
                        note = it.getString(it.getColumnIndexOrThrow("note")) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getWalletBucketById(id: Long): WalletBucketItem? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM wallet_buckets WHERE id = ?", arrayOf(id.toString()))
        cursor.use {
            if (it.moveToFirst()) {
                return WalletBucketItem(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                    icon = it.getString(it.getColumnIndexOrThrow("icon")) ?: "wallet",
                    colorHex = it.getString(it.getColumnIndexOrThrow("color_hex")) ?: "#0284C7",
                    connectedAccounts = it.getString(it.getColumnIndexOrThrow("connected_accounts")) ?: "",
                    balance = it.getDouble(it.getColumnIndexOrThrow("balance")),
                    note = it.getString(it.getColumnIndexOrThrow("note")) ?: ""
                )
            }
        }
        return null
    }

    fun insertWalletBucket(bucket: WalletBucketItem): Long {
        val db = writableDatabase
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM wallet_buckets", null)
        var count = 0
        countCursor.use { if (it.moveToFirst()) count = it.getInt(0) }
        if (count >= 5) return -1L

        val cv = ContentValues().apply {
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

    fun getTotalBucketBalance(): Double {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT SUM(balance) FROM wallet_buckets", null)
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
            val bIdCol = it.getColumnIndex("bucket_id")
            while (it.moveToNext()) {
                val bId = if (bIdCol >= 0) it.getLong(bIdCol) else bucketId
                list.add(
                    TransactionItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        type = it.getString(it.getColumnIndexOrThrow("type")) ?: "EXPENSE",
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "General",
                        date = it.getString(it.getColumnIndexOrThrow("date")) ?: "",
                        cardId = it.getLong(it.getColumnIndexOrThrow("card_id")),
                        currency = it.getString(it.getColumnIndexOrThrow("currency")) ?: "USD",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                        bucketId = bId
                    )
                )
            }
        }
        return list
    }

    // ==========================================
    // RECURRING SUBSCRIPTIONS CRUD
    // ==========================================
    fun getAllSubscriptions(): List<SubscriptionItem> {
        val list = mutableListOf<SubscriptionItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM subscriptions ORDER BY renewal_date ASC, id ASC", null)
        cursor.use {
            val autoLogIdx = it.getColumnIndex("auto_log")
            while (it.moveToNext()) {
                val autoLog = if (autoLogIdx >= 0) it.getInt(autoLogIdx) == 1 else true
                list.add(
                    SubscriptionItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        serviceName = it.getString(it.getColumnIndexOrThrow("service_name")) ?: "",
                        cost = it.getDouble(it.getColumnIndexOrThrow("cost")),
                        currency = it.getString(it.getColumnIndexOrThrow("currency")) ?: "BDT",
                        billingCycle = it.getString(it.getColumnIndexOrThrow("billing_cycle")) ?: "MONTHLY",
                        renewalDate = it.getString(it.getColumnIndexOrThrow("renewal_date")) ?: "",
                        bucketId = it.getLong(it.getColumnIndexOrThrow("bucket_id")),
                        bucketName = it.getString(it.getColumnIndexOrThrow("bucket_name")) ?: "",
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "Entertainment",
                        icon = it.getString(it.getColumnIndexOrThrow("icon")) ?: "subscription",
                        isActive = it.getInt(it.getColumnIndexOrThrow("is_active")) == 1,
                        autoLog = autoLog
                    )
                )
            }
        }
        return list
    }

    fun insertSubscription(sub: SubscriptionItem): Long {
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

    fun getTotalIncome(monthPrefix: String? = null): Double {
        val db = readableDatabase
        val query = if (!monthPrefix.isNullOrEmpty()) {
            "SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND date LIKE ?"
        } else {
            "SELECT SUM(amount) FROM transactions WHERE type = 'INCOME'"
        }
        val args = if (!monthPrefix.isNullOrEmpty()) arrayOf("$monthPrefix%") else null
        val c = db.rawQuery(query, args)
        c.use {
            return if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0) else 0.0
        }
    }

    fun getTotalExpenses(monthPrefix: String? = null): Double {
        val db = readableDatabase
        val query = if (!monthPrefix.isNullOrEmpty()) {
            "SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date LIKE ?"
        } else {
            "SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE'"
        }
        val args = if (!monthPrefix.isNullOrEmpty()) arrayOf("$monthPrefix%") else null
        val c = db.rawQuery(query, args)
        c.use {
            return if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0) else 0.0
        }
    }

    // ==========================================
    // CURRENCY RATES
    // ==========================================
    fun getAllCurrencyRates(): List<CurrencyRate> {
        val list = mutableListOf<CurrencyRate>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM currency_rates", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CurrencyRate(
                        code = it.getString(it.getColumnIndexOrThrow("code")) ?: "",
                        rate = it.getDouble(it.getColumnIndexOrThrow("rate")),
                        symbol = it.getString(it.getColumnIndexOrThrow("symbol")) ?: "",
                        lastUpdated = it.getString(it.getColumnIndexOrThrow("last_updated")) ?: ""
                    )
                )
            }
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
    fun getAllTasks(filter: String = "ALL", isGoal: Boolean? = null): List<TaskItem> {
        val list = mutableListOf<TaskItem>()
        val db = readableDatabase
        val goalClause = when (isGoal) {
            true -> " AND is_goal = 1"
            false -> " AND is_goal = 0"
            null -> ""
        }
        val goalWhereOnly = when (isGoal) {
            true -> " WHERE is_goal = 1"
            false -> " WHERE is_goal = 0"
            null -> ""
        }
        val (query, args) = when (filter) {
            "TODAY" -> {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                Pair("SELECT * FROM tasks WHERE due_date = '$today' AND is_completed = 0$goalClause ORDER BY priority DESC, id DESC", null)
            }
            "COMPLETED" -> Pair("SELECT * FROM tasks WHERE is_completed = 1$goalClause ORDER BY id DESC", null)
            "GOALS" -> Pair("SELECT * FROM tasks WHERE is_goal = 1 ORDER BY id DESC", null)
            "HIGH" -> Pair("SELECT * FROM tasks WHERE priority = 'HIGH' AND is_completed = 0$goalClause ORDER BY id DESC", null)
            "ALL" -> Pair("SELECT * FROM tasks$goalWhereOnly ORDER BY is_completed ASC, priority DESC, id DESC", null)
            else -> Pair("SELECT * FROM tasks WHERE category = ?$goalClause ORDER BY is_completed ASC, priority DESC, id DESC", arrayOf(filter))
        }
        val cursor = db.rawQuery(query, args)
        cursor.use {
            while (it.moveToNext()) {
                val dueCol = it.getColumnIndex("due_date")
                val rawDue = if (dueCol >= 0 && !it.isNull(dueCol)) it.getString(dueCol) else null
                val dueDate = if (rawDue.isNullOrBlank()) null else rawDue

                val startCol = it.getColumnIndex("start_date")
                val rawStart = if (startCol >= 0 && !it.isNull(startCol)) it.getString(startCol) else null
                val startDate = if (rawStart.isNullOrBlank()) null else rawStart

                list.add(
                    TaskItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                        description = it.getString(it.getColumnIndexOrThrow("description")) ?: "",
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "General",
                        dueDate = dueDate,
                        priority = it.getString(it.getColumnIndexOrThrow("priority")) ?: "MEDIUM",
                        isCompleted = it.getInt(it.getColumnIndexOrThrow("is_completed")) == 1,
                        isGoal = it.getInt(it.getColumnIndexOrThrow("is_goal")) == 1,
                        progress = it.getInt(it.getColumnIndexOrThrow("progress")),
                        startDate = startDate
                    )
                )
            }
        }
        return list
    }

    fun insertTask(task: TaskItem): Long {
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

    fun updateTask(task: TaskItem) {
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
        db.update("tasks", cv, "id = ?", arrayOf(task.id.toString()))
    }

    fun deleteTask(taskId: Long): Int {
        val db = writableDatabase
        return db.delete("tasks", "id = ?", arrayOf(taskId.toString()))
    }

    // ==========================================
    // SCHEDULE & ATTENDANCE
    // ==========================================
    fun getScheduleForDay(dayOfWeek: Int): List<ClassScheduleItem> {
        val list = mutableListOf<ClassScheduleItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM classes WHERE day_of_week = ? ORDER BY start_time ASC",
            arrayOf(dayOfWeek.toString())
        )
        cursor.use {
            val classTypeIdx = it.getColumnIndex("class_type")
            val buildingIdx = it.getColumnIndex("building_or_link")
            while (it.moveToNext()) {
                list.add(
                    ClassScheduleItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        subjectName = it.getString(it.getColumnIndexOrThrow("subject_name")) ?: "",
                        room = it.getString(it.getColumnIndexOrThrow("room")) ?: "",
                        teacher = it.getString(it.getColumnIndexOrThrow("teacher")) ?: "",
                        dayOfWeek = it.getInt(it.getColumnIndexOrThrow("day_of_week")),
                        startTime = it.getString(it.getColumnIndexOrThrow("start_time")) ?: "",
                        endTime = it.getString(it.getColumnIndexOrThrow("end_time")) ?: "",
                        colorHex = it.getString(it.getColumnIndexOrThrow("color_hex")) ?: "#2563EB",
                        classType = if (classTypeIdx >= 0) it.getString(classTypeIdx) ?: "LECTURE" else "LECTURE",
                        buildingOrLink = if (buildingIdx >= 0) it.getString(buildingIdx) ?: "" else ""
                    )
                )
            }
        }
        return list
    }

    fun getAllClasses(): List<ClassScheduleItem> {
        val list = mutableListOf<ClassScheduleItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM classes ORDER BY day_of_week ASC, start_time ASC", null)
        cursor.use {
            val classTypeIdx = it.getColumnIndex("class_type")
            val buildingIdx = it.getColumnIndex("building_or_link")
            while (it.moveToNext()) {
                list.add(
                    ClassScheduleItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        subjectName = it.getString(it.getColumnIndexOrThrow("subject_name")) ?: "",
                        room = it.getString(it.getColumnIndexOrThrow("room")) ?: "",
                        teacher = it.getString(it.getColumnIndexOrThrow("teacher")) ?: "",
                        dayOfWeek = it.getInt(it.getColumnIndexOrThrow("day_of_week")),
                        startTime = it.getString(it.getColumnIndexOrThrow("start_time")) ?: "",
                        endTime = it.getString(it.getColumnIndexOrThrow("end_time")) ?: "",
                        colorHex = it.getString(it.getColumnIndexOrThrow("color_hex")) ?: "#2563EB",
                        classType = if (classTypeIdx >= 0) it.getString(classTypeIdx) ?: "LECTURE" else "LECTURE",
                        buildingOrLink = if (buildingIdx >= 0) it.getString(buildingIdx) ?: "" else ""
                    )
                )
            }
        }
        return list
    }

    fun insertClassSchedule(item: ClassScheduleItem): Long {
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
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM attendance ORDER BY subject_name ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SubjectAttendanceItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        subjectName = it.getString(it.getColumnIndexOrThrow("subject_name")) ?: "",
                        attendedClasses = it.getInt(it.getColumnIndexOrThrow("attended_classes")),
                        totalClasses = it.getInt(it.getColumnIndexOrThrow("total_classes"))
                    )
                )
            }
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
    // STUDENT PORTAL (Sections, Homework, Exams)
    // ==========================================
    fun getAllStudentSubjects(): List<StudentSubject> {
        val list = mutableListOf<StudentSubject>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM student_subjects ORDER BY name ASC", null)
        cursor.use {
            val creditsIdx = it.getColumnIndex("credits")
            val instructorNameIdx = it.getColumnIndex("instructor_name")
            val instructorEmailIdx = it.getColumnIndex("instructor_email")
            val instructorPhoneIdx = it.getColumnIndex("instructor_phone")
            val syllabusTextIdx = it.getColumnIndex("syllabus_text")
            val syllabusPathIdx = it.getColumnIndex("syllabus_path")
            while (it.moveToNext()) {
                list.add(
                    StudentSubject(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        code = it.getString(it.getColumnIndexOrThrow("code")) ?: "",
                        colorHex = it.getString(it.getColumnIndexOrThrow("color_hex")) ?: "#2563EB",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                        credits = if (creditsIdx >= 0) it.getInt(creditsIdx) else 3,
                        instructorName = if (instructorNameIdx >= 0) it.getString(instructorNameIdx) ?: "" else "",
                        instructorEmail = if (instructorEmailIdx >= 0) it.getString(instructorEmailIdx) ?: "" else "",
                        instructorPhone = if (instructorPhoneIdx >= 0) it.getString(instructorPhoneIdx) ?: "" else "",
                        syllabusText = if (syllabusTextIdx >= 0) it.getString(syllabusTextIdx) ?: "" else "",
                        syllabusPath = if (syllabusPathIdx >= 0) it.getString(syllabusPathIdx) ?: "" else ""
                    )
                )
            }
        }
        return list
    }

    fun getAllSubjectsSync(): List<StudentSubject> = getAllStudentSubjects()

    fun getStudentSubjectById(subjectId: Long): StudentSubject? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM student_subjects WHERE id = ?", arrayOf(subjectId.toString()))
        cursor.use {
            if (it.moveToFirst()) {
                val creditsIdx = it.getColumnIndex("credits")
                val instructorNameIdx = it.getColumnIndex("instructor_name")
                val instructorEmailIdx = it.getColumnIndex("instructor_email")
                val instructorPhoneIdx = it.getColumnIndex("instructor_phone")
                val syllabusTextIdx = it.getColumnIndex("syllabus_text")
                val syllabusPathIdx = it.getColumnIndex("syllabus_path")
                return StudentSubject(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                    code = it.getString(it.getColumnIndexOrThrow("code")) ?: "",
                    colorHex = it.getString(it.getColumnIndexOrThrow("color_hex")) ?: "#2563EB",
                    notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                    credits = if (creditsIdx >= 0) it.getInt(creditsIdx) else 3,
                    instructorName = if (instructorNameIdx >= 0) it.getString(instructorNameIdx) ?: "" else "",
                    instructorEmail = if (instructorEmailIdx >= 0) it.getString(instructorEmailIdx) ?: "" else "",
                    instructorPhone = if (instructorPhoneIdx >= 0) it.getString(instructorPhoneIdx) ?: "" else "",
                    syllabusText = if (syllabusTextIdx >= 0) it.getString(syllabusTextIdx) ?: "" else "",
                    syllabusPath = if (syllabusPathIdx >= 0) it.getString(syllabusPathIdx) ?: "" else ""
                )
            }
        }
        return null
    }

    fun insertStudentSubject(subject: StudentSubject, autoTrackAttendance: Boolean = false): Long {
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

    fun deleteStudentSubject(id: Long): Int {
        val db = writableDatabase
        db.delete("subject_work_items", "subject_id = ?", arrayOf(id.toString()))
        db.delete("subject_materials", "subject_id = ?", arrayOf(id.toString()))
        return db.delete("student_subjects", "id = ?", arrayOf(id.toString()))
    }

    // ==========================================
    // SUBJECT MATERIALS (PDFs, Images, Syllabus)
    // ==========================================
    fun getMaterialsForSubject(subjectId: Long): List<SubjectMaterial> {
        val list = mutableListOf<SubjectMaterial>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM subject_materials WHERE subject_id = ? ORDER BY id DESC",
            arrayOf(subjectId.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SubjectMaterial(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        subjectId = it.getLong(it.getColumnIndexOrThrow("subject_id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                        type = it.getString(it.getColumnIndexOrThrow("type")) ?: "PDF_DOCUMENT",
                        filePath = it.getString(it.getColumnIndexOrThrow("file_path")) ?: "",
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at")) ?: "",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: ""
                    )
                )
            }
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

    fun getItemsForSubject(subjectId: Long, itemTypeFilter: String? = null): List<SubjectWorkItem> {
        val list = mutableListOf<SubjectWorkItem>()
        val db = readableDatabase
        val query = if (itemTypeFilter != null && itemTypeFilter != "ALL") {
            "SELECT * FROM subject_work_items WHERE subject_id = ? AND item_type = ? ORDER BY id DESC"
        } else {
            "SELECT * FROM subject_work_items WHERE subject_id = ? ORDER BY id DESC"
        }
        val args = if (itemTypeFilter != null && itemTypeFilter != "ALL") {
            arrayOf(subjectId.toString(), itemTypeFilter)
        } else {
            arrayOf(subjectId.toString())
        }
        val cursor = db.rawQuery(query, args)
        cursor.use {
            val examTypeIdx = it.getColumnIndex("exam_type")
            while (it.moveToNext()) {
                list.add(
                    SubjectWorkItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        subjectId = it.getLong(it.getColumnIndexOrThrow("subject_id")),
                        itemType = it.getString(it.getColumnIndexOrThrow("item_type")) ?: "HOMEWORK",
                        title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                        description = it.getString(it.getColumnIndexOrThrow("description")) ?: "",
                        startDate = it.getString(it.getColumnIndexOrThrow("start_date")) ?: "",
                        endDate = it.getString(it.getColumnIndexOrThrow("end_date")) ?: "",
                        scoreOrMarks = it.getString(it.getColumnIndexOrThrow("score_or_marks")) ?: "",
                        status = it.getString(it.getColumnIndexOrThrow("status")) ?: "PENDING",
                        attachmentPath = it.getString(it.getColumnIndexOrThrow("attachment_path")) ?: "",
                        examType = if (examTypeIdx >= 0) it.getString(examTypeIdx) ?: "" else ""
                    )
                )
            }
        }
        return list
    }

    fun getAllExams(examTypeFilter: String? = null): List<Pair<SubjectWorkItem, StudentSubject>> {
        val list = mutableListOf<Pair<SubjectWorkItem, StudentSubject>>()
        val subjects = getAllStudentSubjects().associateBy { it.id }
        val db = readableDatabase
        val query = if (!examTypeFilter.isNullOrBlank()) {
            "SELECT * FROM subject_work_items WHERE item_type = 'EXAM' AND exam_type = ? ORDER BY end_date ASC, start_date ASC"
        } else {
            "SELECT * FROM subject_work_items WHERE item_type = 'EXAM' ORDER BY end_date ASC, start_date ASC"
        }
        val args = if (!examTypeFilter.isNullOrBlank()) arrayOf(examTypeFilter) else null
        val cursor = db.rawQuery(query, args)
        cursor.use {
            val examTypeIdx = it.getColumnIndex("exam_type")
            while (it.moveToNext()) {
                val subjectId = it.getLong(it.getColumnIndexOrThrow("subject_id"))
                val subject = subjects[subjectId] ?: StudentSubject(id = subjectId, name = "Subject")
                val item = SubjectWorkItem(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    subjectId = subjectId,
                    itemType = it.getString(it.getColumnIndexOrThrow("item_type")) ?: "EXAM",
                    title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                    description = it.getString(it.getColumnIndexOrThrow("description")) ?: "",
                    startDate = it.getString(it.getColumnIndexOrThrow("start_date")) ?: "",
                    endDate = it.getString(it.getColumnIndexOrThrow("end_date")) ?: "",
                    scoreOrMarks = it.getString(it.getColumnIndexOrThrow("score_or_marks")) ?: "",
                    status = it.getString(it.getColumnIndexOrThrow("status")) ?: "PENDING",
                    attachmentPath = it.getString(it.getColumnIndexOrThrow("attachment_path")) ?: "",
                    examType = if (examTypeIdx >= 0) it.getString(examTypeIdx) ?: "" else ""
                )
                list.add(Pair(item, subject))
            }
        }
        return list
    }

    fun getTodayHomeworkDue(todayDate: String): List<Pair<SubjectWorkItem, StudentSubject>> {
        val list = mutableListOf<Pair<SubjectWorkItem, StudentSubject>>()
        val subjects = getAllStudentSubjects().associateBy { it.id }
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM subject_work_items WHERE status != 'DONE' AND (item_type = 'HOMEWORK' OR item_type = 'ASSIGNMENT') AND (end_date = ? OR (end_date = '' AND start_date = ?))",
            arrayOf(todayDate, todayDate)
        )
        cursor.use {
            val examTypeIdx = it.getColumnIndex("exam_type")
            while (it.moveToNext()) {
                val subjectId = it.getLong(it.getColumnIndexOrThrow("subject_id"))
                val subject = subjects[subjectId] ?: StudentSubject(id = subjectId, name = "Subject")
                list.add(
                    Pair(
                        SubjectWorkItem(
                            id = it.getLong(it.getColumnIndexOrThrow("id")),
                            subjectId = subjectId,
                            itemType = it.getString(it.getColumnIndexOrThrow("item_type")) ?: "HOMEWORK",
                            title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                            description = it.getString(it.getColumnIndexOrThrow("description")) ?: "",
                            startDate = it.getString(it.getColumnIndexOrThrow("start_date")) ?: "",
                            endDate = it.getString(it.getColumnIndexOrThrow("end_date")) ?: "",
                            scoreOrMarks = it.getString(it.getColumnIndexOrThrow("score_or_marks")) ?: "",
                            status = it.getString(it.getColumnIndexOrThrow("status")) ?: "PENDING",
                            attachmentPath = it.getString(it.getColumnIndexOrThrow("attachment_path")) ?: "",
                            examType = if (examTypeIdx >= 0) it.getString(examTypeIdx) ?: "" else ""
                        ),
                        subject
                    )
                )
            }
        }
        return list
    }

    fun getTodayClassTests(todayDate: String): List<Pair<SubjectWorkItem, StudentSubject>> {
        val list = mutableListOf<Pair<SubjectWorkItem, StudentSubject>>()
        val subjects = getAllStudentSubjects().associateBy { it.id }
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM subject_work_items WHERE item_type = 'EXAM' AND (exam_type = 'CLASS_TEST_QUIZ' OR exam_type = '') AND (end_date = ? OR start_date = ?)",
            arrayOf(todayDate, todayDate)
        )
        cursor.use {
            val examTypeIdx = it.getColumnIndex("exam_type")
            while (it.moveToNext()) {
                val subjectId = it.getLong(it.getColumnIndexOrThrow("subject_id"))
                val subject = subjects[subjectId] ?: StudentSubject(id = subjectId, name = "Subject")
                list.add(
                    Pair(
                        SubjectWorkItem(
                            id = it.getLong(it.getColumnIndexOrThrow("id")),
                            subjectId = subjectId,
                            itemType = it.getString(it.getColumnIndexOrThrow("item_type")) ?: "EXAM",
                            title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                            description = it.getString(it.getColumnIndexOrThrow("description")) ?: "",
                            startDate = it.getString(it.getColumnIndexOrThrow("start_date")) ?: "",
                            endDate = it.getString(it.getColumnIndexOrThrow("end_date")) ?: "",
                            scoreOrMarks = it.getString(it.getColumnIndexOrThrow("score_or_marks")) ?: "",
                            status = it.getString(it.getColumnIndexOrThrow("status")) ?: "PENDING",
                            attachmentPath = it.getString(it.getColumnIndexOrThrow("attachment_path")) ?: "",
                            examType = if (examTypeIdx >= 0) it.getString(examTypeIdx) ?: "" else ""
                        ),
                        subject
                    )
                )
            }
        }
        return list
    }

    fun insertSubjectWorkItem(item: SubjectWorkItem): Long {
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
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM work_orders ORDER BY id DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    WorkOrder(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                        clientName = it.getString(it.getColumnIndexOrThrow("client_name")) ?: "",
                        quantity = it.getInt(it.getColumnIndexOrThrow("quantity")),
                        unitPrice = it.getDouble(it.getColumnIndexOrThrow("unit_price")),
                        totalPrice = it.getDouble(it.getColumnIndexOrThrow("total_price")),
                        startDate = it.getString(it.getColumnIndexOrThrow("start_date")) ?: "",
                        endDate = it.getString(it.getColumnIndexOrThrow("end_date")) ?: "",
                        status = it.getString(it.getColumnIndexOrThrow("status")) ?: "",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                        attachmentPath = it.getString(it.getColumnIndexOrThrow("attachment_path")) ?: ""
                    )
                )
            }
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
    fun getAllNotes(): List<NoteItem> {
        val list = mutableListOf<NoteItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM notes ORDER BY id DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    NoteItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                        content = it.getString(it.getColumnIndexOrThrow("content")) ?: "",
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "",
                        colorHex = it.getString(it.getColumnIndexOrThrow("color_hex")) ?: "",
                        updatedAt = it.getString(it.getColumnIndexOrThrow("updated_at")) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun insertNote(note: NoteItem): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", note.title)
            put("content", note.content)
            put("category", note.category)
            put("color_hex", note.colorHex)
            put("updated_at", note.updatedAt)
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
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM contacts_groups ORDER BY id DESC", null)
        cursor.use {
            val phoneIdx = it.getColumnIndex("phone_number")
            val roleIdx = it.getColumnIndex("role")
            val scopeIdx = it.getColumnIndex("scope")
            while (it.moveToNext()) {
                list.add(
                    ContactGroupItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        type = it.getString(it.getColumnIndexOrThrow("type")) ?: "",
                        platform = it.getString(it.getColumnIndexOrThrow("platform")) ?: "",
                        targetLink = it.getString(it.getColumnIndexOrThrow("target_link")) ?: "",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                        phoneNumber = if (phoneIdx >= 0) it.getString(phoneIdx) ?: "" else "",
                        role = if (roleIdx >= 0) it.getString(roleIdx) ?: "Classmate" else "Classmate",
                        scope = if (scopeIdx >= 0) it.getString(scopeIdx) ?: "SHARED" else "SHARED"
                    )
                )
            }
        }
        return list
    }

    fun getContactsForPortal(isStudent: Boolean, roleFilter: String? = null): List<ContactGroupItem> {
        val list = mutableListOf<ContactGroupItem>()
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
            val phoneIdx = it.getColumnIndex("phone_number")
            val roleIdx = it.getColumnIndex("role")
            val scopeIdx = it.getColumnIndex("scope")
            while (it.moveToNext()) {
                list.add(
                    ContactGroupItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        type = it.getString(it.getColumnIndexOrThrow("type")) ?: "",
                        platform = it.getString(it.getColumnIndexOrThrow("platform")) ?: "",
                        targetLink = it.getString(it.getColumnIndexOrThrow("target_link")) ?: "",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                        phoneNumber = if (phoneIdx >= 0) it.getString(phoneIdx) ?: "" else "",
                        role = if (roleIdx >= 0) it.getString(roleIdx) ?: "Classmate" else "Classmate",
                        scope = if (scopeIdx >= 0) it.getString(scopeIdx) ?: "SHARED" else "SHARED"
                    )
                )
            }
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
            "currency_rates", "tasks", "classes", "attendance", "student_subjects",
            "subject_materials", "subject_work_items", "work_orders", "notes", "contacts_groups"
        )

        for (table in tables) {
            val arr = JSONArray()
            val cursor = db.rawQuery("SELECT * FROM $table", null)
            cursor.use {
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
                    "currency_rates", "tasks", "classes", "attendance", "student_subjects",
                    "subject_materials", "subject_work_items", "work_orders", "notes", "contacts_groups"
                )
                for (table in tables) {
                    if (root.has(table)) {
                        db.delete(table, null, null)
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
                            db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                    }
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
                    created_at TEXT DEFAULT ''
                )"""
            )
        } catch (_: Exception) {}
    }

    fun insertDueDebt(item: DueDebtItem): Long {
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
            put("created_at", if (item.createdAt.isNotBlank()) item.createdAt else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
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

    fun getAllDuesDebts(): List<DueDebtItem> {
        val list = mutableListOf<DueDebtItem>()
        val db = readableDatabase
        ensureDuesDebtsTable(db)
        try {
            val cursor = db.rawQuery("SELECT * FROM dues_debts ORDER BY status ASC, id DESC", null)
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        DueDebtItem(
                            id = it.getLong(it.getColumnIndexOrThrow("id")),
                            personName = it.getString(it.getColumnIndexOrThrow("person_name")) ?: "",
                            phoneOrContact = it.getString(it.getColumnIndexOrThrow("phone_or_contact")) ?: "",
                            amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                            dueDate = it.getString(it.getColumnIndexOrThrow("due_date")) ?: "",
                            type = it.getString(it.getColumnIndexOrThrow("type")) ?: "",
                            status = it.getString(it.getColumnIndexOrThrow("status")) ?: "",
                            notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                            createdAt = it.getString(it.getColumnIndexOrThrow("created_at")) ?: ""
                        )
                    )
                }
            }
        } catch (_: Exception) {}
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
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        personName = it.getString(it.getColumnIndexOrThrow("person_name")) ?: "",
                        phoneOrContact = it.getString(it.getColumnIndexOrThrow("phone_or_contact")) ?: "",
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        dueDate = it.getString(it.getColumnIndexOrThrow("due_date")) ?: "",
                        type = it.getString(it.getColumnIndexOrThrow("type")) ?: "",
                        status = it.getString(it.getColumnIndexOrThrow("status")) ?: "",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at")) ?: ""
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

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

    fun getDuesDebtsSummary(): Pair<Double, Double> {
        val db = readableDatabase
        ensureDuesDebtsTable(db)
        var totalLentUnpaid = 0.0
        var totalBorrowedUnpaid = 0.0
        try {
            val cursorLent = db.rawQuery("SELECT SUM(amount) FROM dues_debts WHERE type = 'LENT' AND status = 'UNPAID'", null)
            cursorLent.use {
                if (it.moveToFirst() && !it.isNull(0)) totalLentUnpaid = it.getDouble(0)
            }
            val cursorBorrowed = db.rawQuery("SELECT SUM(amount) FROM dues_debts WHERE type = 'BORROWED' AND status = 'UNPAID'", null)
            cursorBorrowed.use {
                if (it.moveToFirst() && !it.isNull(0)) totalBorrowedUnpaid = it.getDouble(0)
            }
        } catch (_: Exception) {}
        return Pair(totalLentUnpaid, totalBorrowedUnpaid)
    }
}
