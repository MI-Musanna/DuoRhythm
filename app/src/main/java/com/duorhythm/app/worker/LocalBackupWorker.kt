package com.duorhythm.app.worker

import android.content.Context
import android.os.Environment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.repository.BackupService
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class LocalBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val success = performAutoBackup(applicationContext)
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "DuoRhythm_LocalAutoBackup"
        const val PREFS_NAME = "lifesync_prefs"
        const val KEY_AUTO_BACKUP_ENABLED = "key_local_auto_backup_enabled"
        const val KEY_AUTO_BACKUP_INTERVAL = "key_local_auto_backup_interval" // "DAILY", "WEEKLY", "MONTHLY"

        fun performAutoBackup(context: Context): Boolean {
            return try {
                val db = DatabaseHelper.getInstance(context)
                val json = db.exportDatabaseToJson()

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                val fileName = "DuoRhythm_AutoBackup_$timeStamp.json"

                val backupDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DuoRhythm_Backups"
                )
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }

                val backupFile = File(backupDir, fileName)
                FileOutputStream(backupFile).use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                // Automatically prune older backups, keeping only the 3 most recent snapshots
                pruneOldBackups(backupDir, maxKeep = 3)

                val displayTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                BackupService.setLastBackupTimestamp(context, displayTime)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        private fun pruneOldBackups(dir: File, maxKeep: Int = 3) {
            try {
                val files = dir.listFiles { file ->
                    file.isFile && (file.name.startsWith("DuoRhythm_AutoBackup_") || file.name.startsWith("StudySync_AutoBackup_")) && file.name.endsWith(".json")
                } ?: return

                if (files.size > maxKeep) {
                    files.sortByDescending { it.lastModified() }
                    for (i in maxKeep until files.size) {
                        try {
                            files[i].delete()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun schedule(context: Context, interval: String) {
            val workManager = WorkManager.getInstance(context)
            val (repeatInterval, timeUnit) = when (interval.uppercase(Locale.US)) {
                "WEEKLY" -> Pair(7L, TimeUnit.DAYS)
                "MONTHLY" -> Pair(30L, TimeUnit.DAYS)
                else -> Pair(1L, TimeUnit.DAYS) // Daily default
            }

            val request = PeriodicWorkRequestBuilder<LocalBackupWorker>(repeatInterval, timeUnit)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
