package app.kinkeep.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {
    private val day = ScoringEngine.DAY_MS
    private val now = 1_800_000_000_000L

    private fun call(daysAgo: Int, minutes: Int = 10, dir: Direction = Direction.OUTGOING) =
        Interaction(now - daysAgo * day, InteractionType.CALL, dir, minutes * 60)

    private fun missed(daysAgo: Int) =
        Interaction(now - daysAgo * day, InteractionType.CALL, Direction.MISSED, 0)

    @Test fun noHistoryIsDormant() {
        val r = ScoringEngine.score(7, emptyList(), now)
        assertEquals(0, r.score)
        assertEquals(HealthCategory.DORMANT, r.category)
        assertNull(r.daysSinceLastContact)
    }

    @Test fun steadyWeeklyCallsAreThriving() {
        val calls = (0..12).map { call(it * 7) }
        val r = ScoringEngine.score(7, calls, now)
        assertEquals(90, r.score)
        assertEquals(HealthCategory.THRIVING, r.category)
    }

    @Test fun veryOverdueContactScoresZero() {
        val r = ScoringEngine.score(30, listOf(call(100)), now)
        assertEquals(0, r.score)
        assertEquals(HealthCategory.DORMANT, r.category)
    }

    @Test fun unreturnedMissedCallCostsTenPoints() {
        val base = (1..12).map { call(it * 7) }
        val without = ScoringEngine.score(7, base, now)
        val with = ScoringEngine.score(7, base + missed(1), now)
        assertEquals(1, with.unreturnedMissedCalls)
        assertEquals(10, without.score - with.score)
    }

    @Test fun returningTheCallRemovesThePenalty() {
        val base = (1..12).map { call(it * 7) }
        val r = ScoringEngine.score(7, base + missed(3) + call(1), now)
        assertEquals(0, r.unreturnedMissedCalls)
    }

    @Test fun shortCallsDoNotCountAsMeaningfulContact() {
        val r = ScoringEngine.score(7, listOf(Interaction(now - day, InteractionType.CALL, Direction.OUTGOING, 20)), now)
        assertNull(r.daysSinceLastContact)
        assertTrue(r.score < 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ignoredContactsCannotBeScored() { ScoringEngine.score(0, emptyList(), now) }
}
