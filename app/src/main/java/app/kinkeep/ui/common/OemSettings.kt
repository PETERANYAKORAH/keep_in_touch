package app.kinkeep.ui.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.kinkeep.domain.Oem

/**
 * Best-effort shortcuts to each maker's autostart or battery screen. These screens are not part of
 * Android's public API and vary by model, so every one is tried safely and we fall back to KinKeep's app settings.
 */
object OemSettings {
    private fun screen(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    fun intentsFor(oem: Oem): List<Intent> = when (oem) {
        Oem.XIAOMI -> listOf(screen("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        Oem.OPPO -> listOf(
            screen("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            screen("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            screen("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        )
        Oem.VIVO -> listOf(
            screen("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            screen("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        Oem.HUAWEI -> listOf(
            screen("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            screen("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        Oem.TRANSSION -> listOf(screen("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"))
        Oem.SAMSUNG -> listOf(screen("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
        Oem.OTHER -> emptyList()
    }

    fun open(ctx: Context, oem: Oem) {
        for (intent in intentsFor(oem)) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // Not present on this model: try the next one.
            }
        }
        SystemSettings.openApp(ctx)
    }
}
