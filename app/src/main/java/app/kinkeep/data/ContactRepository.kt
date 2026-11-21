package app.kinkeep.data

import android.content.Context
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType
import app.kinkeep.security.NoteCrypto
import app.kinkeep.widget.refreshWidgets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the UI and notification actions need to edit a contact.
 * Notes (on contacts and on logged conversations) are encrypted at rest and only decrypted here.
 */
@Singleton
class ContactRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val contactDao: ContactDao,
    private val interactionDao: InteractionDao,
    private val stateDao: ReminderStateDao,
    private val crypto: NoteCrypto,
) {
    fun observeContact(id: Long): Flow<ContactEntity?> =
        contactDao.observeById(id).map { c -> c?.copy(notes = crypto.decrypt(c.notes)) }

    fun observeInteractions(id: Long): Flow<List<InteractionEntity>> =
        interactionDao.observeFor(id).map { list -> list.map { it.copy(note = crypto.decrypt(it.note)) } }

    fun observeUntracked(): Flow<List<ContactEntity>> = contactDao.observeUntracked()
    fun observeReminderState(id: Long): Flow<ReminderStateEntity?> = stateDao.observe(id)

    suspend fun setCircle(id: Long, circle: Circle) { edit(id) { it.copy(circle = circle) }; refreshWidgets(ctx) }
    suspend fun setCadenceOverride(id: Long, days: Int?) = edit(id) { it.copy(cadenceOverrideDays = days) }
    suspend fun setMuted(id: Long, muted: Boolean) = edit(id) { it.copy(muted = muted) }
    suspend fun saveNotes(id: Long, notes: String) = edit(id) { it.copy(notes = crypto.encrypt(notes)) }

    /** Records a conversation that is not in the call log (WhatsApp, in person, and so on). */
    suspend fun logManual(
        id: Long, direction: Direction, minutes: Int, note: String,
        at: Long = System.currentTimeMillis(),
    ) {
        interactionDao.insertAll(
            listOf(
                InteractionEntity(
                    contactId = id, timestamp = at, type = InteractionType.MANUAL,
                    direction = direction, durationSec = minutes.coerceAtLeast(0) * 60,
                    note = crypto.encrypt(note.trim()),
                ),
            ),
        )
        refreshWidgets(ctx)
    }

    /** Stops reminders for this person until [untilMs]. Pass 0 to clear the snooze. */
    suspend fun snooze(id: Long, untilMs: Long) {
        val old = stateDao.get(id) ?: ReminderStateEntity(contactId = id)
        stateDao.upsert(old.copy(snoozedUntil = untilMs))
        refreshWidgets(ctx)
    }

    suspend fun primaryNumber(id: Long): String? = contactDao.numbersFor(id).firstOrNull()?.displayNumber

    private suspend fun edit(id: Long, change: (ContactEntity) -> ContactEntity) {
        contactDao.get(id)?.let { contactDao.update(change(it)) }
    }
}
