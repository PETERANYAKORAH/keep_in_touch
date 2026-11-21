package app.kinkeep.data

import app.kinkeep.domain.ContactHealth
import app.kinkeep.domain.Interaction
import app.kinkeep.domain.ScoringEngine

/** One place that turns stored rows into scored contacts, so the UI, reminders and widget always agree. */
object HealthAssembler {
    class Assembled(val contacts: List<ContactHealth>, val history: Map<Long, List<Interaction>>)

    fun assemble(tracked: List<ContactEntity>, interactions: List<InteractionEntity>, now: Long): Assembled {
        val history = interactions.groupBy({ it.contactId }, { Interaction(it.timestamp, it.type, it.direction, it.durationSec) })
        val contacts = tracked.map { c ->
            val cadence = (c.cadenceOverrideDays ?: c.circle.defaultCadenceDays).coerceAtLeast(1)
            ContactHealth(
                id = c.id, name = c.displayName, circle = c.circle, cadenceDays = cadence,
                result = ScoringEngine.score(cadence, history[c.id].orEmpty(), now),
                muted = c.muted, birthdayMonthDay = c.birthdayMonthDay,
            )
        }.sortedBy { it.result.score }
        return Assembled(contacts, history)
    }
}
