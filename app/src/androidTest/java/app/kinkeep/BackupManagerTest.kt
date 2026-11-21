package app.kinkeep

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kinkeep.backup.BackupException
import app.kinkeep.backup.BackupManager
import app.kinkeep.data.ContactEntity
import app.kinkeep.data.InteractionEntity
import app.kinkeep.data.KinDatabase
import app.kinkeep.data.PhoneNumberEntity
import app.kinkeep.data.ReminderStateEntity
import app.kinkeep.data.SettingsStore
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType
import app.kinkeep.security.NoteCrypto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private val ctx = testContext()
    private val crypto = softwareCrypto()
    private lateinit var db: KinDatabase
    private lateinit var settings: SettingsStore
    private lateinit var manager: BackupManager

    @Before fun setUp() {
        db = inMemoryDb()
        settings = SettingsStore(ctx).also { it.reset() }
        manager = BackupManager(ctx, db, db.backupDao(), crypto, settings)
    }

    @After fun tearDown() { db.close() }

    private suspend fun seed(): Long {
        val id = db.contactDao().insert(
            ContactEntity(androidContactId = 42, displayName = "Mum", circle = Circle.INNER, notes = crypto.encrypt("Likes tea"), createdAt = 1L),
        )
        db.contactDao().insertNumbers(listOf(PhoneNumberEntity(contactId = id, normalizedNumber = "031234567", displayNumber = "0803 123 4567")))
        db.interactionDao().insertAll(
            listOf(InteractionEntity(contactId = id, timestamp = 5_000L, type = InteractionType.MANUAL, direction = Direction.OUTGOING, callLogId = 9, note = crypto.encrypt("weekly call"))),
        )
        db.reminderStateDao().upsert(ReminderStateEntity(contactId = id, snoozedUntil = 10L, lastSentAt = 20L, lastBirthdayYear = 2026))
        return id
    }

    private suspend fun clearEverything() = db.backupDao().run {
        deleteInteractions(); deleteNumbers(); deleteStates(); deleteContacts()
    }

    @Test fun backupAndRestoreRoundTrip() = runBlocking {
        seed()
        val file = manager.export("correct horse".toCharArray())
        clearEverything()

        val summary = manager.restore(file, "correct horse".toCharArray())

        assertEquals(1, summary.contacts)
        assertEquals(1, summary.interactions)
        val contact = db.backupDao().contacts().single()
        assertNull(contact.androidContactId)                       // re-linked on the next import
        assertTrue(contact.notes.startsWith(NoteCrypto.PREFIX))    // encrypted at rest again
        assertEquals("Likes tea", crypto.decrypt(contact.notes))
        val interaction = db.backupDao().interactions().single()
        assertNull(interaction.callLogId)
        assertEquals("weekly call", crypto.decrypt(interaction.note))
        assertEquals(1, db.backupDao().numbers().size)
        assertEquals(2026, db.backupDao().states().single().lastBirthdayYear)
    }

    @Test fun wrongPasswordRestoresNothing() = runBlocking {
        seed()
        val file = manager.export("correct horse".toCharArray())
        clearEverything()
        assertThrows(BackupException::class.java) { runBlocking { manager.restore(file, "wrong password".toCharArray()) } }
        assertEquals(0, db.backupDao().contacts().size)
    }

    @Test fun restoreReplacesWhatIsThere() = runBlocking {
        seed()
        val file = manager.export("correct horse".toCharArray())
        db.contactDao().insert(ContactEntity(androidContactId = 99, displayName = "Extra", circle = Circle.FRIENDS, createdAt = 2L))
        manager.restore(file, "correct horse".toCharArray())
        assertEquals(listOf("Mum"), db.backupDao().contacts().map { it.displayName })
    }

    @Test fun deleteAllDataWipesRecordsAndSettings() = runBlocking {
        seed()
        settings.update { it.copy(onboardingDone = true, appLockEnabled = true) }
        manager.wipeAll()
        assertEquals(0, db.backupDao().contacts().size)
        assertEquals(0, db.backupDao().interactions().size)
        assertEquals(false, settings.current.onboardingDone)
        assertEquals(false, settings.current.appLockEnabled)
    }
}
