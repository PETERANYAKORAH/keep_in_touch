package app.kinkeep.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the dialer or messaging app. ACTION_DIAL and ACTION_SENDTO need no extra permission. */
object ContactActions {
    fun dial(ctx: Context, number: String) = start(ctx, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    fun message(ctx: Context, number: String) = start(ctx, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

    private fun start(ctx: Context, intent: Intent) {
        try { ctx.startActivity(intent) } catch (_: ActivityNotFoundException) { /* no app to handle it */ }
    }
}
