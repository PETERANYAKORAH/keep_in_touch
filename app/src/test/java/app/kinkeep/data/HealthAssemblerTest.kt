package app.kinkeep.data

import app.kinkeep.domain.Circle
import app.kinkeep.domain.DashboardBuilder
import app.kinkeep.domain.Direction
import app.kinkeep.domain.HealthCategory
import app.kinkeep.domain.InteractionType
import app.kinkeep.domain.PlannerInput
import app.kinkeep.domain.QuietWindow
import app.kinkeep.domain.ReminderPlanner
import app.kinkeep.domain.ScoringEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class HealthAssemblerTest {
    private val now = 1_800_000_000_000L
    private val day = ScoringEngine.DAY_MS

    private fun contact(
        id: Long, circle: Circle = Circle.FRIENDS, muted: Boolean = false,
        birthday: String? = null, override: Int? = null,
    ) = ContactEntity(
        id = id, androidContactId = id, displayName = "C$id", circle = circle,
        cadenceOverrideDays = override, birthdayMonthDay = birthday, muted = muted, createdAt = 0L,
    )

    private fun call(id: Long, contactId: Long, daysAgo: Int, dir: Direction = Direction.OUTGOING) =
        InteractionEntity(id, contactId, now - daysAgo * day, InteractionType.CALL, dir, 600)

    @Test fun sortsByScoreAndCarriesMutedAndBirthday() {
        val weekly = (0..12).map { call(it.toLong(), 1, it * 7) }
        val result = HealthAssembler.assemble(
            listOf(contact(1, Circle.INNER), contact(2, muted = true, birthday = "05-17")), weekly, now,
        )
        assertEquals(listOf(2L, 1L), result.contacts.map { it.id }) // no history scores lowest, so it comes first
        assertTrue(result.contacts[0].muted)
        assertEquals("05-17", result.contacts[0].birthdayMonthDay)
        assertEquals(HealthCategory.THRIVING, result.contacts[1].result.category)
    }

    @Test fun cadenceOverrideBeatsTheCircleDefault() {
        val result = HealthAssembler.assemble(listOf(contact(1, override = 3), contact(2)), emptyList(), now)
        assertEquals(3, result.contacts.first { it.id == 1L }.cadenceDays)
        assertEquals(Circle.FRIENDS.defaultCadenceDays, result.contacts.first { it.id == 2L }.cadenceDays)
    }

    @Test fun historyIsGroupedPerContact() {
        val result = HealthAssembler.assemble(
            listOf(contact(1), contact(2)), listOf(call(1, 1, 1), call(2, 1, 2), call(3, 2, 3)), now,
        )
        assertEquals(2, result.history[1L]?.size)
        assertEquals(1, result.history[2L]?.size)
    }

    /** A large phone: 5,000 tracked people and 50,000 calls must be scored, summarised and planned in seconds. */
    @Test fun handlesLargePhones() {
        val people = (1L..5_000L).map { contact(it, Circle.entries[(it % 4).toInt()]) }
        val calls = (1L..50_000L).map { n -> call(n, (n % 5_000) + 1, (n % 300).toInt(), if (n % 7 == 0L) Direction.MISSED else Direction.OUTGOING) }

        var contactsCount = 0
        val millis = measureTimeMillis {
            val assembled = HealthAssembler.assemble(people, calls, now)
            contactsCount = assembled.contacts.size
            val dashboard = DashboardBuilder.build(assembled.contacts, DashboardBuilder.weeklyCounts(assembled.history.values.flatten(), now))
            assertEquals(5_000, dashboard.trackedCount)
            val plan = ReminderPlanner.plan(
                PlannerInput(
                    contacts = assembled.contacts, lastSentAt = emptyMap(), snoozedUntil = emptyMap(), birthdayDoneYear = emptyMap(),
                    sentToday = 0, dailyCap = 3, nowMs = now, nowHour = 18, todayMonthDay = "05-17", year = 2027,
                    defaultHour = 9, typicalHour = emptyMap(), quiet = QuietWindow(22, 8),
                ),
            )
            assertTrue(plan.size <= 3)
        }
        assertEquals(5_000, contactsCount)
        assertTrue("took ${millis}ms", millis < 10_000) // generous: normally well under a second
    }
}
