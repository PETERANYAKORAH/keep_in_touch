package app.kinkeep.widget

import app.kinkeep.data.ContactDao
import app.kinkeep.data.HealthAssembler
import app.kinkeep.data.InteractionDao
import app.kinkeep.domain.ContactHealth
import app.kinkeep.domain.DashboardBuilder
import app.kinkeep.domain.ScoringEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetDataProvider @Inject constructor(
    private val contactDao: ContactDao,
    private val interactionDao: InteractionDao,
) {
    suspend fun topOverdue(limit: Int = 3): List<ContactHealth> {
        val now = System.currentTimeMillis()
        val assembled = HealthAssembler.assemble(
            contactDao.getTracked(), interactionDao.sinceList(now - 365 * ScoringEngine.DAY_MS), now,
        )
        return DashboardBuilder.build(assembled.contacts, emptyList()).reachOutToday.take(limit)
    }
}
