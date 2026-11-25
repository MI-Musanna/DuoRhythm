package com.duorhythm.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.repository.BackupService
import com.duorhythm.app.data.repository.DataBackupHelper
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupSnapshotHelper {

    data class SnapshotItem(
        val file: File,
        val displayName: String,
        val formattedDate: String,
        val sizeFormatted: String,
        val isAuto: Boolean
    )

    fun getBackupDirectory(context: Context): File {
        // App-dedicated external directory (Accessible directly without root/Shizuku)
        val dir = File(context.getExternalFilesDir(null), "DuoRhythm_Snapshots")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPublicBackupDirectory(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DuoRhythm_Backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportToPublicDownloads(context: Context, jsonContent: String, fileName: String): File? {
        var mediaStoreSuccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DuoRhythm_Backups")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(jsonContent.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                    mediaStoreSuccess = true
                }
            } catch (_: Throwable) {
                mediaStoreSuccess = false
            }
        }

        return try {
            val publicDir = getPublicBackupDirectory()
            val file = File(publicDir, fileName)
            if (!mediaStoreSuccess || !file.exists()) {
                file.writeText(jsonContent)
            }
            file
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun listAvailableSnapshots(context: Context): List<SnapshotItem> {
        val dir = getBackupDirectory(context)
        val files = dir.listFiles { f -> f.extension == "json" || f.name.endsWith(".duobak") } ?: return emptyList()

        val list = mutableListOf<SnapshotItem>()
        val sdf = SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.ENGLISH)

        files.sortedByDescending { it.lastModified() }.forEach { file ->
            val isAuto = file.name.startsWith("Auto_", ignoreCase = true)
            val cleanName = if (isAuto) "Automatic Snapshot" else "Manual Snapshot"
            val dateStr = sdf.format(Date(file.lastModified()))
            val sizeKb = (file.length() / 1024).coerceAtLeast(1)

            list.add(
                SnapshotItem(
                    file = file,
                    displayName = cleanName,
                    formattedDate = dateStr,
                    sizeFormatted = "${sizeKb} KB",
                    isAuto = isAuto
                )
            )
        }
        return list
    }

    fun createInstantSnapshot(context: Context, isAuto: Boolean): Boolean {
        return try {
            val db = DatabaseHelper.getInstance(context)
            val dir = getBackupDirectory(context)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val prefix = if (isAuto) "Auto" else "Manual"
            val file = File(dir, "${prefix}_Snapshot_${timestamp}.json")

            val jsonBackup = db.exportFullDatabaseAsJson()
            file.writeText(jsonBackup)

            // Retain only latest 5 auto snapshots to prevent storage bloat
            if (isAuto) {
                val autoFiles = dir.listFiles { f -> f.name.startsWith("Auto_") }?.sortedBy { it.lastModified() }
                if (autoFiles != null && autoFiles.size > 5) {
                    for (i in 0 until (autoFiles.size - 5)) {
                        autoFiles[i].delete()
                    }
                }
            }

            // Public external export on CREATE BACKUP (Uninstall-Safe)
            val publicFileName = "DuoRhythm_Backup_${timestamp}.json"
            exportToPublicDownloads(context, jsonBackup, publicFileName)

            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val label = if (isAuto) "$now (Auto)" else now
            BackupService.setLastBackupTimestamp(context, label)

            true
        } catch (_: Exception) {
            false
        }
    }

    fun restoreSnapshotFromFile(context: Context, file: File): Boolean {
        return try {
            val content = file.readText()
            val db = DatabaseHelper.getInstance(context)
            val success = db.importFullDatabaseFromJson(content)
            if (success) {
                try {
                    val root = JSONObject(content)
                    DataBackupHelper.restoreProfileAvatarsFromJson(context, root)
                    DataBackupHelper.restoreAttachmentsFromJson(context, root)
                    DataBackupHelper.restoreUiPreferencesFromJson(context, root)
                } catch (_: Exception) {}
                try {
                    val activeId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(context)
                    val activeProfile = db.profileDao.getProfileById(activeId) ?: db.getAllProfiles().firstOrNull()
                    if (activeProfile != null) {
                        com.duorhythm.app.data.local.pref.ProfileManager.syncUserProfile(context, activeProfile)
                    }
                } catch (_: Exception) {}
                DatabaseHelper.reactivateAllAlarms(context)
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                BackupService.setLastBackupTimestamp(context, "$now (Restored)")
            }
            success
        } catch (_: Exception) {
            false
        }
    }

    fun deleteSnapshot(file: File): Boolean = deleteSnapshot(null, file)

    fun deleteSnapshot(context: Context?, file: File): Boolean {
        var deletedInternal = false
        try {
            deletedInternal = file.delete()
        } catch (_: Exception) {}

        // Match public backup file name in Downloads/DuoRhythm_Backups/
        try {
            val fileName = file.name
            val timestamp = when {
                fileName.contains("_Snapshot_") -> fileName.substringAfter("_Snapshot_").substringBefore(".")
                fileName.contains("DuoRhythm_Backup_") -> fileName.substringAfter("DuoRhythm_Backup_").substringBefore(".")
                else -> ""
            }

            if (timestamp.isNotBlank()) {
                val publicTargetName = "DuoRhythm_Backup_${timestamp}.json"
                val publicDir = getPublicBackupDirectory()
                val publicFile = File(publicDir, publicTargetName)
                if (publicFile.exists()) {
                    publicFile.delete()
                }

                if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                        val selectionArgs = arrayOf(publicTargetName)
                        context.contentResolver.query(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            selectionArgs,
                            null
                        )?.use { cursor ->
                            val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                            if (idCol >= 0) {
                                while (cursor.moveToNext()) {
                                    val id = cursor.getLong(idCol)
                                    val uri = android.content.ContentUris.withAppendedId(
                                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                        id
                                    )
                                    try {
                                        context.contentResolver.delete(uri, null, null)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        return deletedInternal
    }
}
