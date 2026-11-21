package app.kinkeep.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Result
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kinkeep.data.SyncRepository
import app.kinkeep.diagnostics.ErrorLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: SyncRepository,
    private val errorLog: ErrorLog,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        repository.sync()
        Result.success()
    } catch (e: Exception) {
        errorLog.record("sync", e)
        Result.retry()
    }

    companion object {
        private const val NAME = "kinkeep-sync"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
