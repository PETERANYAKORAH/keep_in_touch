package app.kinkeep

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kinkeep.data.ContactEntity
import app.kinkeep.data.ContactRepository
import app.kinkeep.data.KinDatabase
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType
import app.kinkeep.security.NoteCrypto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactRepositoryTest {
    private lateinit var db: KinDatabase
    private lateinit var repo: ContactRepository
    private val crypto = softwareCrypto()

    @Before fun setUp() {
        db = inMemoryDb()
        repo = ContactRepository(testContext(), db.contactDao(), db.interactionDao(), db.reminderStateDao(), crypto)
    }

    @After fun tearDown() { db.close() }

    private suspend fun newContact() =
        db.contactDao().insert(ContactEntity(androidContactId = 1, displayName = "Ada", circle = Circle.FRIENDS, createdAt = 1L))

    @Test fun notesAreEncryptedAtRestAndReadableThroughTheRepository() = runBlocking {
        val id = newContact()
        repo.saveNotes(id, "Ask about her exam")
        assertTrue(db.contactDao().get(id)!!.notes.startsWith(NoteCrypto.PREFIX))
        assertEquals("Ask about her exam", repo.observeContact(id).first()!!.notes)
    }

    @Test fun manualLogNotesAreEncryptedToo() = runBlocking {
        val id = newContact()
        repo.logManual(id, Direction.OUTGOING, minutes = 5, note = "  weekly chat  ")
        val raw = db.backupDao().interactions().single()
        assertTrue(raw.note.startsWith(NoteCrypto.PREFIX))
        assertEquals(InteractionType.MANUAL, raw.type)
        assertEquals(300, raw.durationSec)
        assertEquals("weekly chat", repo.observeInteractions(id).first().single().note)
    }

    @Test fun snoozeCanBeSetAndCleared() = runBlocking {
        val id = newContact()
        repo.snooze(id, 1_234L)
        assertEquals(1_234L, db.reminderStateDao().get(id)!!.snoozedUntil)
        repo.snooze(id, 0L)
        assertEquals(0L, db.reminderStateDao().get(id)!!.snoozedUntil)
    }

    @Test fun circleCadenceAndMuteAreSaved() = runBlocking {
        val id = newContact()
        repo.setCircle(id, Circle.INNER)
        repo.setCadenceOverride(id, 5)
        repo.setMuted(id, true)
        val saved = db.contactDao().get(id)!!
        assertEquals(Circle.INNER, saved.circle)
        assertEquals(5, saved.cadenceOverrideDays)
        assertTrue(saved.muted)
    }
}
