package app.kinkeep

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kinkeep.data.ContactEntity
import app.kinkeep.data.InteractionEntity
import app.kinkeep.data.KinDatabase
import app.kinkeep.data.PhoneNumberEntity
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KinDatabaseTest {
    private lateinit var db: KinDatabase

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private fun contact(name: String, circle: Circle = Circle.FRIENDS, androidId: Long? = null) =
        ContactEntity(androidContactId = androidId, displayName = name, circle = circle, createdAt = 1L)

    @Test fun trackedListExcludesIgnoredContacts() = runBlocking {
        db.contactDao().insert(contact("Mum", Circle.INNER))
        db.contactDao().insert(contact("Stranger", Circle.IGNORED))
        assertEquals(listOf("Mum"), db.contactDao().observeTracked().first().map { it.displayName })
        assertEquals(listOf("Stranger"), db.contactDao().observeUntracked().first().map { it.displayName })
    }

    @Test fun sameConversationIsNeverStoredTwice() = runBlocking {
        val id = db.contactDao().insert(contact("Ada"))
        fun call(logId: Long?) = InteractionEntity(contactId = id, timestamp = 5_000L, type = InteractionType.CALL, direction = Direction.OUTGOING, callLogId = logId)
        db.interactionDao().insertAll(listOf(call(1), call(2), call(null)))
        assertEquals(1, db.backupDao().interactions().size)
    }

    @Test fun deletingAContactRemovesItsNumbersAndHistory() = runBlocking {
        val id = db.contactDao().insert(contact("Ada"))
        db.contactDao().insertNumbers(listOf(PhoneNumberEntity(contactId = id, normalizedNumber = "031234567", displayNumber = "0803 123 4567")))
        db.interactionDao().insertAll(listOf(InteractionEntity(contactId = id, timestamp = 1L, type = InteractionType.MANUAL, direction = Direction.OUTGOING)))
        db.backupDao().deleteContacts()
        assertEquals(0, db.backupDao().numbers().size)
        assertEquals(0, db.backupDao().interactions().size)
    }

    @Test fun androidContactIdIsUnique() = runBlocking {
        db.contactDao().insert(contact("A", androidId = 7))
        val second = db.contactDao().insert(contact("B", androidId = 7))
        assertEquals(-1L, second)
    }
}
