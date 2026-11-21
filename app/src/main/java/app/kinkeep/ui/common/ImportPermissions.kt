package app.kinkeep.ui.common

import android.Manifest
import android.os.Build
import app.kinkeep.BuildConfig

/** The permissions requested when importing. The Google Play build never asks for the call log. */
object ImportPermissions {
    fun list(): Array<String> = buildList {
        add(Manifest.permission.READ_CONTACTS)
        if (BuildConfig.CALL_LOG_ENABLED) add(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}
