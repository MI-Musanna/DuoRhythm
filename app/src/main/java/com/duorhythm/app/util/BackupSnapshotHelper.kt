package com.duorhythm.app.util

import android.content.Context
import android.os.Environment
import com.duorhythm.app.data.local.db.DatabaseHelper
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
            true
        } catch (_: Exception) {
            false
        }
    }

    fun restoreSnapshotFromFile(context: Context, file: File): Boolean {
        return try {
            val content = file.readText()
            val db = DatabaseHelper.getInstance(context)
            db.importFullDatabaseFromJson(content)
        } catch (_: Exception) {
            false
        }
    }

    fun deleteSnapshot(file: File): Boolean {
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }
}
