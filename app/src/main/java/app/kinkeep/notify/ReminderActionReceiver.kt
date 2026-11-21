package app.kinkeep.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.kinkeep.data.ContactRepository
import app.kinkeep.domain.Direction
import app.kinkeep.domain.ReminderTiming
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** Handles the Snooze and "Already spoke" buttons on a reminder, fully offline. */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: ContactRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // required so Hilt can inject
        val contactId = intent.getLongExtra(NotificationHelper.EXTRA_CONTACT_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        if (contactId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    NotificationHelper.ACTION_SNOOZE ->
                        repo.snooze(contactId, ReminderTiming.startOfTomorrow(System.currentTimeMillis(), ZoneId.systemDefault()))
                    NotificationHelper.ACTION_SPOKE -> repo.logManual(contactId, Direction.OUTGOING, 0, "")
                }
                NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }
}
