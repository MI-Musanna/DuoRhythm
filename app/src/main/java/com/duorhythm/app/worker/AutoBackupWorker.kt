package com.duorhythm.app.worker

import android.content.Context
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

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val success = performAutoBackup(applicationContext)
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "DuoRhythm_AutoBackupWorker"
        const val PREFS_NAME = "lifesync_prefs"
        const val KEY_AUTO_BACKUP_ENABLED = "key_local_auto_backup_enabled"
        const val KEY_AUTO_BACKUP_INTERVAL = "key_local_auto_backup_interval"

        fun performAutoBackup(context: Context): Boolean {
            return try {
                val db = DatabaseHelper.getInstance(context)
                val json = db.exportDatabaseToJson()

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                val fileName = "DuoRhythm_AutoBackup_$timeStamp.json"

                // Safely write internal snapshots using app-scoped storage: File(context.filesDir, "backups")
                val backupDir = File(context.filesDir, "backups")
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }

                val backupFile = File(backupDir, fileName)
                FileOutputStream(backupFile).use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                // Rotate snapshots safely without throwing I/O exceptions on restricted devices
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
                val files = dir.listFiles { f -> f.isFile && f.name.startsWith("DuoRhythm_AutoBackup_") && f.name.endsWith(".json") }
                    ?: return
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

        fun schedule(context: Context, interval: String = "DAILY") {
            val (repeatInterval, timeUnit) = when (interval.uppercase(Locale.US)) {
                "WEEKLY" -> Pair(7L, TimeUnit.DAYS)
                "MONTHLY" -> Pair(30L, TimeUnit.DAYS)
                else -> Pair(1L, TimeUnit.DAYS)
            }

            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(repeatInterval, timeUnit)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
