package com.duorhythm.app.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.repository.BackupService
import com.duorhythm.app.data.repository.DataBackupHelper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val success = performAutoBackup(applicationContext)
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "DUORHYTHM_AUTO_BACKUP"
        const val PREFS_NAME = "lifesync_prefs"
        const val KEY_AUTO_BACKUP_ENABLED = "pref_auto_backup_enabled"
        const val KEY_AUTO_BACKUP_INTERVAL_HOURS = "pref_auto_backup_interval_hours"
        const val KEY_BACKUP_INCLUDE_ATTACHMENTS = "pref_backup_include_attachments"

        fun performAutoBackup(context: Context): Boolean {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val includeAttachments = prefs.getBoolean(KEY_BACKUP_INCLUDE_ATTACHMENTS, false)

                val db = DatabaseHelper.getInstance(context)
                val profiles = db.getAllProfiles()
                val allActiveSlots = if (profiles.isNotEmpty()) profiles.map { it.id.toInt() } else listOf(1, 2, 3)

                val jsonPayload = DataBackupHelper.generateSelectedProfilesBackupJson(
                    allActiveSlots,
                    context,
                    includeAttachments
                )

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "DuoRhythm_AutoBackup_$timeStamp.json"

                val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                val backupDir = File(baseDir, "AutoBackups").apply { mkdirs() }

                val backupFile = File(backupDir, fileName)
                FileOutputStream(backupFile).use { out ->
                    out.write(jsonPayload.toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                // Strictly retain the latest 3 JSON files
                pruneOldBackups(backupDir, maxKeep = 3)

                val displayTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                BackupService.setLastBackupTimestamp(context, "$displayTime (Auto)")
                true
            } catch (e: Throwable) {
                e.printStackTrace()
                false
            }
        }

        private fun pruneOldBackups(dir: File, maxKeep: Int = 3) {
            try {
                val files = dir.listFiles { f ->
                    f.isFile && f.name.startsWith("DuoRhythm_AutoBackup_") && f.name.endsWith(".json")
                } ?: return
                if (files.size > maxKeep) {
                    files.sortBy { it.lastModified() }
                    val deleteCount = files.size - maxKeep
                    for (i in 0 until deleteCount) {
                        try {
                            files[i].delete()
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        fun schedulePeriodicBackup(context: Context, intervalHours: Int) {
            val safeHours = if (intervalHours > 0) intervalHours else 24
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(safeHours.toLong(), TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        @Deprecated("Use schedulePeriodicBackup with intervalHours instead")
        fun schedule(context: Context, interval: String = "DAILY") {
            val hours = when (interval.uppercase(Locale.US)) {
                "WEEKLY" -> 168
                "MONTHLY" -> 720
                else -> 24
            }
            schedulePeriodicBackup(context, hours)
        }
    }
}
