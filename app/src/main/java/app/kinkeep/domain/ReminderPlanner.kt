package app.kinkeep.domain

enum class ReminderKind { MISSED_CALL, OVERDUE, BIRTHDAY } // order = priority among capped reminders

data class PlannedReminder(val contact: ContactHealth, val kind: ReminderKind)

/** Quiet hours as whole hours. Wraps past midnight (22 to 8). Equal start and end means quiet hours are off. */
data class QuietWindow(val startHour: Int, val endHour: Int) {
    fun contains(hour: Int): Boolean = when {
        startHour == endHour -> false
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }
}

data class PlannerInput(
    val contacts: List<ContactHealth>,
    val lastSentAt: Map<Long, Long>,
    val snoozedUntil: Map<Long, Long>,
    val birthdayDoneYear: Map<Long, Int>,
    val sentToday: Int,
    val dailyCap: Int,
    val nowMs: Long,
    val nowHour: Int,
    val todayMonthDay: String,
    val year: Int,
    val defaultHour: Int,
    val typicalHour: Map<Long, Int>,
    val quiet: QuietWindow,
)

/**
 * Decides who to remind right now. Pure and deterministic. The reminder worker runs hourly and
 * sends a reminder once the current hour has reached that person's target hour (their usual calling
 * hour, else the default), so a late or skipped run still catches up later the same day.
 */
object ReminderPlanner {
    const val RESEND_AFTER_DAYS = 2

    fun targetHour(contactId: Long, input: PlannerInput): Int {
        val hour = input.typicalHour[contactId] ?: input.defaultHour
        return if (input.quiet.contains(hour)) input.quiet.endHour else hour
    }

    fun plan(input: PlannerInput): List<PlannedReminder> {
        if (input.quiet.contains(input.nowHour)) return emptyList()

        val ready = input.contacts
            .filter { !it.muted && (input.snoozedUntil[it.id] ?: 0L) <= input.nowMs }
            .filter { input.nowHour >= targetHour(it.id, input) }

        // Birthdays: once a year per person, not limited by the daily cap.
        val birthdays = ready
            .filter { it.birthdayMonthDay == input.todayMonthDay && input.birthdayDoneYear[it.id] != input.year }
            .map { PlannedReminder(it, ReminderKind.BIRTHDAY) }
        val birthdayIds = birthdays.map { it.contact.id }.toSet()

        val resendMs = RESEND_AFTER_DAYS * ScoringEngine.DAY_MS
        val others = ready
            .filter { it.id !in birthdayIds }
            .filter { input.nowMs - (input.lastSentAt[it.id] ?: 0L) >= resendMs }
            .mapNotNull { c ->
                when {
                    c.result.unreturnedMissedCalls > 0 -> PlannedReminder(c, ReminderKind.MISSED_CALL)
                    DashboardBuilder.isDue(c) -> PlannedReminder(c, ReminderKind.OVERDUE)
                    else -> null
                }
            }
            .sortedWith(
                compareBy<PlannedReminder>({ it.kind.ordinal }, { it.contact.circle.ordinal })
                    .thenByDescending { DashboardBuilder.overdueRatio(it.contact) },
            )

        val room = (input.dailyCap - input.sentToday).coerceAtLeast(0)
        return birthdays + others.take(room)
    }
}
