package app.kinkeep.domain

object ReminderHealth {
    /** The reminder worker runs hourly. Three silent hours while reminders are on suggests the phone is stopping KinKeep. */
    const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L

    fun isStale(enabled: Boolean, pausedUntil: Long, lastRunAt: Long, now: Long): Boolean =
        enabled && pausedUntil <= now && lastRunAt > 0L && now - lastRunAt > STALE_AFTER_MS
}
