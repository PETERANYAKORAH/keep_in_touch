package app.kinkeep

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.kinkeep.diagnostics.ErrorLog
import app.kinkeep.notify.NotificationHelper
import app.kinkeep.work.ReminderWorker
import app.kinkeep.work.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KinKeepApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var errorLog: ErrorLog

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        installCrashLogging()
        NotificationHelper.createChannel(this)
        SyncWorker.schedule(this)
        ReminderWorker.schedule(this)
    }

    /** Records crashes locally (type and stack only), then lets Android handle the crash as usual. */
    private fun installCrashLogging() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            errorLog.record("crash:${thread.name}", error, describeApp())
            previous?.uncaughtException(thread, error)
        }
    }

    fun describeApp(): String {
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }
        return "v$version Android ${Build.VERSION.RELEASE} ${Build.MODEL}"
    }
}
