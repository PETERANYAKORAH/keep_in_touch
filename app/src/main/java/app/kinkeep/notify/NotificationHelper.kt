package app.kinkeep.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.kinkeep.MainActivity
import app.kinkeep.R
import app.kinkeep.domain.ReminderKind

object NotificationHelper {
    const val CHANNEL_ID = "reminders"
    const val EXTRA_CONTACT_ID = "contact_id"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val ACTION_SNOOZE = "app.kinkeep.action.SNOOZE"
    const val ACTION_SPOKE = "app.kinkeep.action.SPOKE"
    private const val TEST_ID = 999_999
    private const val SUMMARY_ID = 999_998
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun createChannel(ctx: Context) {
        val channel = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = ctx.getString(R.string.channel_description) }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(ctx: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun notificationId(contactId: Long, kind: ReminderKind): Int = (contactId * 3 + kind.ordinal).toInt()

    /** Opens KinKeep on a contact. The data URI keeps each PendingIntent distinct per contact. */
    fun openIntent(ctx: Context, contactId: Long): Intent =
        Intent(ctx, MainActivity::class.java).apply {
            data = Uri.parse("kinkeep://contact/$contactId")
            putExtra(EXTRA_CONTACT_ID, contactId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    fun show(ctx: Context, contactId: Long, kind: ReminderKind, title: String, body: String, number: String?) {
        if (!canNotify(ctx)) return
        val nid = notificationId(contactId, kind)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, nid * 4, openIntent(ctx, contactId), FLAGS))
        if (number != null) {
            val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            builder.addAction(0, ctx.getString(R.string.action_call), PendingIntent.getActivity(ctx, nid * 4 + 1, dial, FLAGS))
        }
        builder.addAction(0, ctx.getString(R.string.notif_snooze), broadcast(ctx, nid * 4 + 2, ACTION_SNOOZE, contactId, nid))
        builder.addAction(0, ctx.getString(R.string.notif_spoke), broadcast(ctx, nid * 4 + 3, ACTION_SPOKE, contactId, nid))
        NotificationManagerCompat.from(ctx).notify(nid, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showTest(ctx: Context) {
        if (!canNotify(ctx)) return
        val open = PendingIntent.getActivity(ctx, TEST_ID, Intent(ctx, MainActivity::class.java), FLAGS)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.test_title))
            .setContentText(ctx.getString(R.string.test_body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(ctx).notify(TEST_ID, n)
    }

    @SuppressLint("MissingPermission")
    fun showSummary(ctx: Context, title: String, body: String) {
        if (!canNotify(ctx)) return
        val open = PendingIntent.getActivity(ctx, SUMMARY_ID, Intent(ctx, MainActivity::class.java), FLAGS)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(ctx).notify(SUMMARY_ID, n)
    }

    private fun broadcast(ctx: Context, requestCode: Int, action: String, contactId: Long, nid: Int): PendingIntent {
        val intent = Intent(ctx, ReminderActionReceiver::class.java).setAction(action)
            .putExtra(EXTRA_CONTACT_ID, contactId)
            .putExtra(EXTRA_NOTIFICATION_ID, nid)
        return PendingIntent.getBroadcast(ctx, requestCode, intent, FLAGS)
    }
}
