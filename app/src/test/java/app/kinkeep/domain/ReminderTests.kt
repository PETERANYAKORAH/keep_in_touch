package app.kinkeep.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class ReminderPlannerTest {
    private val now = 1_800_000_000_000L
    private val day = ScoringEngine.DAY_MS

    private fun c(
        id: Long, circle: Circle = Circle.FRIENDS, days: Int? = 60, cadence: Int = 30,
        missed: Int = 0, muted: Boolean = false, birthday: String? = null,
    ) = ContactHealth(id, "C$id", circle, cadence, HealthResult(10, HealthCategory.DORMANT, days, missed), muted, birthday)

    private fun input(
        contacts: List<ContactHealth>, nowHour: Int = 18, sentToday: Int = 0, cap: Int = 3,
        lastSent: Map<Long, Long> = emptyMap(), snoozed: Map<Long, Long> = emptyMap(),
        bdDone: Map<Long, Int> = emptyMap(), typical: Map<Long, Int> = emptyMap(),
        quiet: QuietWindow = QuietWindow(22, 8), defaultHour: Int = 9,
    ) = PlannerInput(contacts, lastSent, snoozed, bdDone, sentToday, cap, now, nowHour, "05-17", 2027, defaultHour, typical, quiet)

    @Test fun quietWindowWrapsPastMidnight() {
        val q = QuietWindow(22, 8)
        listOf(22, 23, 0, 7).forEach { assertTrue(q.contains(it)) }
        listOf(8, 12, 21).forEach { assertFalse(q.contains(it)) }
        assertFalse(QuietWindow(8, 8).contains(8))
        assertTrue(QuietWindow(13, 17).contains(16))
        assertFalse(QuietWindow(13, 17).contains(17))
    }

    @Test fun nothingIsSentDuringQuietHours() {
        assertTrue(ReminderPlanner.plan(input(listOf(c(1)), nowHour = 23)).isEmpty())
        assertTrue(ReminderPlanner.plan(input(listOf(c(1)), nowHour = 3)).isEmpty())
        assertEquals(1, ReminderPlanner.plan(input(listOf(c(1)), nowHour = 12)).size)
    }

    @Test fun waitsForTheDefaultHour() {
        assertTrue(ReminderPlanner.plan(input(listOf(c(1)), nowHour = 9, defaultHour = 18)).isEmpty())
        assertEquals(1, ReminderPlanner.plan(input(listOf(c(1)), nowHour = 18, defaultHour = 18)).size)
    }

    @Test fun learnedHourOverridesDefaultPerPerson() {
        val plan = ReminderPlanner.plan(input(listOf(c(1), c(2)), nowHour = 10, typical = mapOf(1L to 20)))
        assertEquals(listOf(2L), plan.map { it.contact.id })
    }

    @Test fun learnedHourInsideQuietTimeMovesToTheEndOfQuietTime() {
        val inp = input(listOf(c(1)), nowHour = 8, typical = mapOf(1L to 23))
        assertEquals(8, ReminderPlanner.targetHour(1, inp))
        assertEquals(1, ReminderPlanner.plan(inp).size)
    }

    @Test fun dailyCapCountsWhatWasAlreadySent() {
        val many = (1L..6L).map { c(it) }
        assertEquals(2, ReminderPlanner.plan(input(many, sentToday = 1, cap = 3)).size)
        assertTrue(ReminderPlanner.plan(input(many, sentToday = 3, cap = 3)).isEmpty())
    }

    @Test fun snoozedAndMutedAreSkipped() {
        val plan = ReminderPlanner.plan(
            input(listOf(c(1), c(2), c(3, muted = true)), snoozed = mapOf(1L to now + 1, 2L to now - 1)),
        )
        assertEquals(listOf(2L), plan.map { it.contact.id })
    }

    @Test fun recentlyRemindedPeopleAreRotatedOut() {
        val plan = ReminderPlanner.plan(input(listOf(c(1), c(2)), lastSent = mapOf(1L to now - day, 2L to now - 3 * day)))
        assertEquals(listOf(2L), plan.map { it.contact.id })
    }

    @Test fun notDuePeopleAreNotReminded() {
        assertTrue(ReminderPlanner.plan(input(listOf(c(1, days = 1, cadence = 30)))).isEmpty())
    }

    @Test fun missedCallsComeFirstThenCloserCircles() {
        val plan = ReminderPlanner.plan(
            input(listOf(c(1, Circle.INNER, days = 20, cadence = 7), c(2, Circle.CLOSE, days = 1, cadence = 14, missed = 1))),
        )
        assertEquals(listOf(ReminderKind.MISSED_CALL, ReminderKind.OVERDUE), plan.map { it.kind })
        assertEquals(listOf(2L, 1L), plan.map { it.contact.id })
    }

    @Test fun birthdaysIgnoreTheCapAndHappenOncePerYear() {
        val b = c(1, days = 1, cadence = 30, birthday = "05-17")
        val plan = ReminderPlanner.plan(input(listOf(b), cap = 0))
        assertEquals(listOf(ReminderKind.BIRTHDAY), plan.map { it.kind })
        assertTrue(ReminderPlanner.plan(input(listOf(b), cap = 0, bdDone = mapOf(1L to 2027))).isEmpty())
    }
}

class ReminderCopyTest {
    private val now = 1_800_000_000_000L
    private val day = ScoringEngine.DAY_MS

    @Test fun thereAreEnoughTemplatesAndEachHasAName() {
        assertTrue(ReminderCopy.overdue.size >= 30)
        ReminderKind.entries.forEach { k -> ReminderCopy.templates(k).forEach { assertTrue(it, it.contains("{name}")) } }
    }

    @Test fun nameIsSubstituted() {
        val p = ReminderCopy.pick(ReminderKind.OVERDUE, "Ada", emptyMap(), now, Random(1))
        assertTrue(p.text.contains("Ada"))
        assertFalse(p.text.contains("{name}"))
    }

    @Test fun avoidsTemplatesUsedInTheLastTwoWeeks() {
        val size = ReminderCopy.overdue.size
        val history = (0 until size).filter { it != 5 }.associateWith { now - day }
        repeat(20) { assertEquals(5, ReminderCopy.pick(ReminderKind.OVERDUE, "Ada", history, now, Random(it)).index) }
    }

    @Test fun whenAllAreRecentPicksTheLeastRecentlyUsed() {
        val size = ReminderCopy.overdue.size
        val history = (0 until size).associateWith { now - 1000L - it }
        assertEquals(size - 1, ReminderCopy.pick(ReminderKind.OVERDUE, "Ada", history, now).index)
    }

    @Test fun historyRoundTripsAndOldEntriesAreDropped() {
        val csv = ReminderCopy.formatHistory(mapOf(1 to now, 2 to now - 20 * day), now)
        assertEquals(mapOf(1 to now), ReminderCopy.parseHistory(csv))
        assertTrue(ReminderCopy.parseHistory(null).isEmpty())
        assertTrue(ReminderCopy.parseHistory("junk,1:x").isEmpty())
    }

    @Test fun firstNameHandlesSingleWordsAndBlanks() {
        assertEquals("Ada", ReminderCopy.firstName("Ada Obi"))
        assertEquals("Mum", ReminderCopy.firstName("Mum"))
    }
}

class TimingAndBirthdayTest {
    @Test fun typicalHourIsTheMedianAndNeedsThreeSamples() {
        assertNull(ReminderTiming.typicalHour(listOf(9L, 10L)) { it.toInt() })
        assertEquals(10, ReminderTiming.typicalHour(listOf(9L, 10L, 11L)) { it.toInt() })
        assertEquals(10, ReminderTiming.typicalHour(listOf(20L, 9L, 10L, 9L)) { it.toInt() })
    }

    @Test fun startOfTomorrowIsLocalMidnight() {
        val utc = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 11, 19, 15, 30, 0, 0, utc).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 11, 20, 0, 0, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals(expected, ReminderTiming.startOfTomorrow(now, utc))
        assertEquals(ZonedDateTime.of(2026, 11, 19, 0, 0, 0, 0, utc).toInstant().toEpochMilli(), ReminderTiming.startOfToday(now, utc))
    }

    @Test fun birthdayFormatsAreParsed() {
        assertEquals("05-17", BirthdayParser.monthDay("1990-05-17"))
        assertEquals("05-17", BirthdayParser.monthDay("--05-17"))
        assertNull(BirthdayParser.monthDay("garbage"))
        assertNull(BirthdayParser.monthDay("1990-13-01"))
        assertNull(BirthdayParser.monthDay(null))
    }
}
