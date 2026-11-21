package app.kinkeep.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class StreakTest {
    private val now = 1_800_000_000_000L
    private val day = ScoringEngine.DAY_MS

    private fun call(daysAgo: Int, dir: Direction = Direction.OUTGOING, minutes: Int = 5) =
        Interaction(now - daysAgo * day, InteractionType.CALL, dir, minutes * 60)

    @Test fun countsDistinctPeopleOncePerWeek() {
        val history = mapOf(
            1L to listOf(call(1), call(2), call(10)),
            2L to listOf(call(3)),
            3L to listOf(call(1, Direction.MISSED, 0), call(20, minutes = 0)), // missed and very short calls do not count
        )
        val weeks = Streaks.distinctPeoplePerWeek(history, now, weeks = 4)
        assertEquals(listOf(2, 1, 0, 0), weeks)
    }

    @Test fun streakCountsConsecutiveWeeksMeetingTheGoal() {
        assertEquals(3, Streaks.weeklyStreak(listOf(3, 4, 5, 0, 9), goal = 3))
    }

    @Test fun thisWeekDoesNotBreakAStreakUntilItEnds() {
        assertEquals(2, Streaks.weeklyStreak(listOf(1, 3, 3, 0), goal = 3))
        assertEquals(0, Streaks.weeklyStreak(listOf(1, 2, 3), goal = 3))
        assertEquals(0, Streaks.weeklyStreak(emptyList(), goal = 3))
    }
}

class WeeklySummaryTest {
    private val quiet = QuietWindow(22, 8)

    @Test fun sentOnSundayEveningOncePerWeek() {
        assertTrue(WeeklySummary.shouldSend(DayOfWeek.SUNDAY, 18, 18, quiet, 0L, 20_000L))
        assertFalse(WeeklySummary.shouldSend(DayOfWeek.SUNDAY, 18, 18, quiet, 19_999L, 20_000L))
        assertTrue(WeeklySummary.shouldSend(DayOfWeek.SUNDAY, 20, 18, quiet, 19_993L, 20_000L))
    }

    @Test fun notOnOtherDaysEarlyOrDuringQuietHours() {
        assertFalse(WeeklySummary.shouldSend(DayOfWeek.MONDAY, 18, 18, quiet, 0L, 20_000L))
        assertFalse(WeeklySummary.shouldSend(DayOfWeek.SUNDAY, 10, 18, quiet, 0L, 20_000L))
        assertFalse(WeeklySummary.shouldSend(DayOfWeek.SUNDAY, 23, 18, quiet, 0L, 20_000L))
    }
}
