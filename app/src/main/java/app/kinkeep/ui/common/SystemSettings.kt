package app.kinkeep.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Shortcuts into Android's own settings. None of these need a permission. */
object SystemSettings {
    fun openBattery(ctx: Context) = start(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openApp(ctx: Context) =
        start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))

    fun openNotificationSettings(ctx: Context) =
        start(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))

    private fun start(ctx: Context, intent: Intent) {
        try { ctx.startActivity(intent) } catch (_: ActivityNotFoundException) { /* setting not available on this phone */ }
    }
}
