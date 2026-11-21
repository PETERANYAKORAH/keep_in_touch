package app.kinkeep.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Result
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kinkeep.R
import app.kinkeep.data.ContactDao
import app.kinkeep.data.ContactRepository
import app.kinkeep.data.HealthAssembler
import app.kinkeep.data.InteractionDao
import app.kinkeep.data.InteractionEntity
import app.kinkeep.data.AppPrefs
import app.kinkeep.data.ReminderStateDao
import app.kinkeep.data.ReminderStateEntity
import app.kinkeep.data.SettingsStore
import app.kinkeep.diagnostics.ErrorLog
import app.kinkeep.domain.BirthdayParser
import app.kinkeep.domain.Direction
import app.kinkeep.domain.Interaction
import app.kinkeep.domain.PlannerInput
import app.kinkeep.domain.QuietWindow
import app.kinkeep.domain.ReminderCopy
import app.kinkeep.domain.ReminderKind
import app.kinkeep.domain.ReminderPlanner
import app.kinkeep.domain.ReminderTiming
import app.kinkeep.domain.ScoringEngine
import app.kinkeep.domain.Streaks
import app.kinkeep.domain.WeeklySummary
import app.kinkeep.notify.NotificationHelper
import app.kinkeep.widget.refreshWidgets
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs about once an hour. Each run asks [ReminderPlanner] who is ready for a reminder right now,
 * posts the notifications, and records them so nobody is reminded twice in a row.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsStore,
    private val contactDao: ContactDao,
    private val interactionDao: InteractionDao,
    private val stateDao: ReminderStateDao,
    private val repo: ContactRepository,
    private val clock: Clock,
    private val errorLog: ErrorLog,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val now = clock.millis()
        settings.update { it.copy(lastRunAt = now) }
        return try {
            val prefs = settings.current
            if (prefs.enabled && prefs.pausedUntil <= now && NotificationHelper.canNotify(applicationContext)) {
                sendReminders(prefs, now)
            }
            refreshWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            errorLog.record("reminders", e)
            Result.retry()
        }
    }

    private suspend fun sendReminders(prefs: AppPrefs, now: Long) {
        val zone = clock.zone
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val interactions = interactionDao.sinceList(now - 365 * ScoringEngine.DAY_MS)
        val assembled = HealthAssembler.assemble(contactDao.getTracked(), interactions, now)
        val states = stateDao.all().associateBy { it.contactId }
        val todayStart = ReminderTiming.startOfToday(now, zone)

        val input = PlannerInput(
            contacts = assembled.contacts,
            lastSentAt = states.mapValues { it.value.lastSentAt },
            snoozedUntil = states.mapValues { it.value.snoozedUntil },
            birthdayDoneYear = states.mapValues { it.value.lastBirthdayYear },
            sentToday = states.values.count { it.lastSentAt >= todayStart },
            dailyCap = prefs.dailyCap,
            nowMs = now,
            nowHour = local.hour,
            todayMonthDay = BirthdayParser.format(local.monthValue, local.dayOfMonth),
            year = local.year,
            defaultHour = prefs.defaultHour,
            typicalHour = typicalHours(interactions, now, zone),
            quiet = QuietWindow(prefs.quietStartHour, prefs.quietEndHour),
        )

        for (planned in ReminderPlanner.plan(input)) {
            val contact = planned.contact
            val key = "templates_${planned.kind.name}"
            val history = ReminderCopy.parseHistory(settings.getString(key))
            val pick = ReminderCopy.pick(planned.kind, ReminderCopy.firstName(contact.name), history, now)
            val title = when (planned.kind) {
                ReminderKind.BIRTHDAY -> applicationContext.getString(R.string.notif_birthday_title, contact.name)
                ReminderKind.MISSED_CALL -> applicationContext.getString(R.string.notif_missed_title, contact.name)
                ReminderKind.OVERDUE -> contact.name
            }
            NotificationHelper.show(applicationContext, contact.id, planned.kind, title, pick.text, repo.primaryNumber(contact.id))

            val old = states[contact.id] ?: ReminderStateEntity(contactId = contact.id)
            stateDao.upsert(
                if (planned.kind == ReminderKind.BIRTHDAY) old.copy(lastBirthdayYear = local.year) else old.copy(lastSentAt = now),
            )
            settings.putString(key, ReminderCopy.formatHistory(history + (pick.index to now), now))
        }
        if (prefs.weeklySummaryEnabled) maybeSendSummary(prefs, assembled, local, now)
    }

    /** One gentle "your week" notification on Sunday evening, and only once per week. */
    private fun maybeSendSummary(prefs: AppPrefs, assembled: HealthAssembler.Assembled, local: ZonedDateTime, now: Long) {
        val today = local.toLocalDate().toEpochDay()
        val lastSent = settings.getString(SUMMARY_KEY)?.toLongOrNull() ?: 0L
        val quiet = QuietWindow(prefs.quietStartHour, prefs.quietEndHour)
        if (!WeeklySummary.shouldSend(local.dayOfWeek, local.hour, prefs.defaultHour, quiet, lastSent, today)) return

        val trackedIds = assembled.contacts.map { it.id }.toSet()
        val perWeek = Streaks.distinctPeoplePerWeek(assembled.history.filterKeys { it in trackedIds }, now)
        val people = perWeek.firstOrNull() ?: 0
        val streak = Streaks.weeklyStreak(perWeek, prefs.weeklyGoal)
        val res = applicationContext.resources
        val body = buildString {
            append(if (people == 0) res.getString(R.string.summary_quiet) else res.getQuantityString(R.plurals.summary_people, people, people))
            if (streak > 0) append(' ').append(res.getQuantityString(R.plurals.summary_streak, streak, streak))
        }
        NotificationHelper.showSummary(applicationContext, applicationContext.getString(R.string.summary_title), body)
        settings.putString(SUMMARY_KEY, today.toString())
    }

    /** Median hour of day at which you usually reach out to each person (needs at least 3 conversations in 6 months). */
    private fun typicalHours(interactions: List<InteractionEntity>, now: Long, zone: ZoneId): Map<Long, Int> {
        val since = now - 180 * ScoringEngine.DAY_MS
        return interactions
            .filter { it.timestamp >= since && it.direction == Direction.OUTGOING }
            .filter { ScoringEngine.weight(Interaction(it.timestamp, it.type, it.direction, it.durationSec)) >= ScoringEngine.MEANINGFUL_WEIGHT }
            .groupBy({ it.contactId }, { it.timestamp })
            .mapNotNull { (id, stamps) ->
                ReminderTiming.typicalHour(stamps) { Instant.ofEpochMilli(it).atZone(zone).hour }?.let { id to it }
            }.toMap()
    }

    companion object {
        private const val PERIODIC = "kinkeep-reminders"
        private const val IMMEDIATE = "kinkeep-reminders-now"
        private const val SUMMARY_KEY = "last_summary_day"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }
    }
}
