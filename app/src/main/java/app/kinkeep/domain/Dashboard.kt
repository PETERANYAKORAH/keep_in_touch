package app.kinkeep.domain

import kotlin.math.roundToInt

data class ContactHealth(
    val id: Long,
    val name: String,
    val circle: Circle,
    val cadenceDays: Int,
    val result: HealthResult,
    val muted: Boolean = false,
    val birthdayMonthDay: String? = null, // "MM-dd"
)

data class DashboardData(
    val overallScore: Int,
    val trackedCount: Int,
    val categoryCounts: Map<HealthCategory, Int>,
    val reachOutToday: List<ContactHealth>,
    val missedCallsToReturn: List<ContactHealth>,
    val weeklyActivity: List<Int>, // conversations per week, oldest first
    val peopleThisWeek: Int = 0,
    val streakWeeks: Int = 0,
    val weeklyGoal: Int = Streaks.DEFAULT_GOAL,
)

/** Pure functions behind the dashboard, so they can be unit-tested without Android. */
object DashboardBuilder {
    const val WEEKS = 8
    private const val MAX_ITEMS = 5
    private const val WEEK_MS = 7 * ScoringEngine.DAY_MS

    fun isDue(c: ContactHealth): Boolean =
        c.result.daysSinceLastContact?.let { it >= c.cadenceDays } ?: true

    fun overdueRatio(c: ContactHealth): Double =
        c.result.daysSinceLastContact?.let { it.toDouble() / c.cadenceDays } ?: Double.MAX_VALUE

    fun build(
        contacts: List<ContactHealth>,
        weeklyActivity: List<Int>,
        peopleThisWeek: Int = 0,
        streakWeeks: Int = 0,
        weeklyGoal: Int = Streaks.DEFAULT_GOAL,
    ): DashboardData {
        val overall = if (contacts.isEmpty()) 0 else contacts.map { it.result.score }.average().roundToInt()
        val counts = HealthCategory.entries.associateWith { cat -> contacts.count { it.result.category == cat } }
        val reminders = contacts.filter { !it.muted }
        val reach = reminders.filter(::isDue)
            .sortedWith(compareBy<ContactHealth> { it.circle.ordinal }.thenByDescending { overdueRatio(it) })
            .take(MAX_ITEMS)
        val missed = reminders.filter { it.result.unreturnedMissedCalls > 0 }
            .sortedWith(compareBy<ContactHealth> { it.circle.ordinal }.thenByDescending { it.result.unreturnedMissedCalls })
            .take(MAX_ITEMS)
        return DashboardData(overall, contacts.size, counts, reach, missed, weeklyActivity, peopleThisWeek, streakWeeks, weeklyGoal)
    }

    /** Non-missed conversations per week for the last [weeks] weeks, oldest first. */
    fun weeklyCounts(interactions: List<Interaction>, now: Long, weeks: Int = WEEKS): List<Int> {
        val out = IntArray(weeks)
        for (i in interactions) {
            if (i.direction == Direction.MISSED) continue
            val age = now - i.timestamp
            if (age < 0) continue
            val w = (age / WEEK_MS).toInt()
            if (w < weeks) out[weeks - 1 - w]++
        }
        return out.toList()
    }
}
