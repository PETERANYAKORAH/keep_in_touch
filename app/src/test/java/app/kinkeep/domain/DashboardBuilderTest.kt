package app.kinkeep.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardBuilderTest {
    private val zeros = List(DashboardBuilder.WEEKS) { 0 }
    private val day = ScoringEngine.DAY_MS

    private fun h(
        id: Long, circle: Circle, days: Int?, cadence: Int,
        score: Int = 50, missed: Int = 0, muted: Boolean = false,
    ) = ContactHealth(id, "C$id", circle, cadence, HealthResult(score, ScoringEngine.categoryFor(score), days, missed), muted)

    @Test fun emptyDashboard() {
        val d = DashboardBuilder.build(emptyList(), zeros)
        assertEquals(0, d.overallScore)
        assertEquals(0, d.trackedCount)
        assertTrue(d.reachOutToday.isEmpty())
    }

    @Test fun overallIsAverageAndCategoriesAreCounted() {
        val d = DashboardBuilder.build(listOf(h(1, Circle.INNER, 1, 7, 90), h(2, Circle.FRIENDS, 50, 30, 10)), zeros)
        assertEquals(50, d.overallScore)
        assertEquals(1, d.categoryCounts[HealthCategory.THRIVING])
        assertEquals(1, d.categoryCounts[HealthCategory.DORMANT])
    }

    @Test fun reachOutIsOrderedByCircleThenMostOverdue() {
        val list = listOf(
            h(1, Circle.FRIENDS, 90, 30),
            h(2, Circle.INNER, 8, 7),
            h(3, Circle.INNER, 21, 7),
            h(4, Circle.CLOSE, 1, 14), // not due yet
        )
        assertEquals(listOf(3L, 2L, 1L), DashboardBuilder.build(list, zeros).reachOutToday.map { it.id })
    }

    @Test fun neverContactedComesFirstWithinItsCircle() {
        val list = listOf(h(1, Circle.INNER, 30, 7), h(2, Circle.INNER, null, 7))
        assertEquals(listOf(2L, 1L), DashboardBuilder.build(list, zeros).reachOutToday.map { it.id })
    }

    @Test fun mutedContactsAreNeverSuggested() {
        val list = listOf(h(1, Circle.INNER, 30, 7, muted = true, missed = 2))
        val d = DashboardBuilder.build(list, zeros)
        assertTrue(d.reachOutToday.isEmpty())
        assertTrue(d.missedCallsToReturn.isEmpty())
        assertEquals(1, d.trackedCount)
    }

    @Test fun reachOutIsCappedAtFive() {
        val list = (1L..9L).map { h(it, Circle.FRIENDS, 60, 30) }
        assertEquals(5, DashboardBuilder.build(list, zeros).reachOutToday.size)
    }

    @Test fun missedCallsAreListed() {
        val list = listOf(h(1, Circle.CLOSE, 2, 14, missed = 1), h(2, Circle.CLOSE, 2, 14))
        assertEquals(listOf(1L), DashboardBuilder.build(list, zeros).missedCallsToReturn.map { it.id })
    }

    @Test fun weeklyBucketsIgnoreMissedAndOldCalls() {
        val now = 1_800_000_000_000L
        val items = listOf(
            Interaction(now - 1 * day, InteractionType.CALL, Direction.OUTGOING, 300),
            Interaction(now - 8 * day, InteractionType.CALL, Direction.INCOMING, 300),
            Interaction(now - 60 * day, InteractionType.CALL, Direction.OUTGOING, 300),
            Interaction(now - 2 * day, InteractionType.CALL, Direction.MISSED, 0),
        )
        val w = DashboardBuilder.weeklyCounts(items, now)
        assertEquals(DashboardBuilder.WEEKS, w.size)
        assertEquals(1, w.last())
        assertEquals(1, w[w.size - 2])
        assertEquals(2, w.sum())
    }
}
