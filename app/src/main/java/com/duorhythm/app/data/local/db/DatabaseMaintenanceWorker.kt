package com.duorhythm.app.data.local.db

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DatabaseMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val db = DatabaseHelper.getInstance(applicationContext).writableDatabase
            // Run SQLite WAL checkpoint and optimization
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close()
            db.rawQuery("PRAGMA optimize", null).close()
            Log.d("DUO_MAINTENANCE", "SQLite WAL Checkpoint & PRAGMA optimize executed cleanly.")
            Result.success()
        } catch (e: Exception) {
            Log.e("DUO_MAINTENANCE", "Maintenance error", e)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "duorhythm_db_maintenance"

        fun scheduleWeekly(context: Context) {
            val request = PeriodicWorkRequestBuilder<DatabaseMaintenanceWorker>(7, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
