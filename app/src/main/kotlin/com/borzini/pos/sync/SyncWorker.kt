package com.borzini.pos.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/** Runs one sync batch in the background. Retried by WorkManager (with its own backoff) on failure. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = syncEngine.syncNow()
        return when (result.status) {
            is SyncStatus.Error -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "borzini_periodic_sync"
        const val ONE_TIME_WORK_NAME = "borzini_manual_sync"
    }
}

class SyncWorkerFactory(private val syncEngine: SyncEngine) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == SyncWorker::class.java.name) {
        SyncWorker(appContext, workerParameters, syncEngine)
    } else {
        null
    }
}
