package com.duorhythm.app.data.repository

import com.duorhythm.app.data.local.db.DatabaseHelper
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupService {

    private const val PREFS_BACKUP = "lifesync_backup_prefs"
    private const val KEY_LAST_BACKUP = "key_last_backup_time"

    fun getLastBackupTimestamp(context: Context): String {
        return context.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)
            .getString(KEY_LAST_BACKUP, "Never") ?: "Never"
    }

    fun setLastBackupTimestamp(context: Context, timestamp: String) {
        context.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_BACKUP, timestamp)
            .apply()
    }

    /**
     * Exports database as JSON directly into an SAF destination Uri (Google Drive, OneDrive, Local storage).
     */
    fun exportDatabaseToUri(context: Context, uri: Uri): Boolean {
        return try {
            val db = DatabaseHelper.getInstance(context)
            val json = db.exportDatabaseToJson()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
            }
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            setLastBackupTimestamp(context, now)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Creates a timestamped local backup cache file.
     */
    fun createBackupFile(context: Context): File? {
        return try {
            val db = DatabaseHelper.getInstance(context)
            val json = db.exportDatabaseToJson()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "LifeSync_Backup_$timeStamp.json"
            val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val file = File(backupDir, fileName)

            FileOutputStream(file).use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Shares backup via Android share chooser (Drive, Gmail, Files, etc.).
     */
    fun shareBackup(context: Context, backupFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, backupFile)

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "LifeSync Backup - ${backupFile.name}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Backup of LifeSync database (Transactions, Tasks, Schedules, Subjects, Orders, Notes, Buckets, Contacts)."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Reads and verifies the integrity of an imported backup JSON file from SAF Uri (Google Drive or Local Storage).
     */
    fun restoreBackupWithVerification(context: Context, uri: Uri): Pair<Boolean, String> {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            } ?: return Pair(false, "Could not open selected backup file.")

            // Verify integrity
            val root = try {
                JSONObject(jsonString)
            } catch (_: Exception) {
                return Pair(false, "Invalid file format: Not a valid JSON file.")
            }

            val requiredTableAny = listOf(
                "tasks", "classes", "cards", "transactions", "wallet_buckets",
                "student_subjects", "work_orders", "notes", "contacts_groups"
            )
            val hasValidTables = requiredTableAny.any { root.has(it) }
            if (!hasValidTables) {
                return Pair(false, "Corrupted or incompatible backup: No recognized LifeSync tables found.")
            }

            val db = DatabaseHelper.getInstance(context)
            val success = db.importDatabaseFromJson(jsonString)
            if (success) {
                DataBackupHelper.restoreProfileAvatarsFromJson(context, root)
                val exportedTime = root.optString("exported_at", "Unknown date")
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                setLastBackupTimestamp(context, "$now (Restored)")
                Pair(true, "Database successfully restored! (Snapshot: $exportedTime)")
            } else {
                Pair(false, "Failed to restore database tables from backup.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Error restoring backup: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun restoreBackup(context: Context, uri: Uri): Boolean {
        val (success, _) = restoreBackupWithVerification(context, uri)
        return success
    }
}
