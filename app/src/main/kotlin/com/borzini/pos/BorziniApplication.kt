package com.borzini.pos

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.borzini.pos.di.AppContainer
import com.borzini.pos.sync.SyncWorker
import com.borzini.pos.sync.SyncWorkerFactory
import java.util.concurrent.TimeUnit

class BorziniApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        schedulePeriodicSync()
    }

    /**
     * Background sync every ~30 minutes when there is any network - the metered/Wi-Fi-only choice
     * itself is enforced inside SyncEngine.syncNow(), not here, so this constraint only needs to
     * say "some connection exists" and let the engine decide whether to actually push.
     */
    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory(container.syncEngine))
            .build()
}
