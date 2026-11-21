package app.kinkeep

import android.content.Context
import android.provider.CallLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kinkeep.data.ContactEntity
import app.kinkeep.data.ImportedCall
import app.kinkeep.data.ImportedContact
import app.kinkeep.data.KinDatabase
import app.kinkeep.data.PermissionChecker
import app.kinkeep.data.PhoneNumberEntity
import app.kinkeep.data.SyncRepository
import app.kinkeep.domain.Circle
import app.kinkeep.domain.PhoneNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncRepositoryTest {
    private val ctx = testContext()
    private val day = 86_400_000L
    private val now = System.currentTimeMillis()
    private lateinit var db: KinDatabase
    private lateinit var source: FakeSource

    @Before fun setUp() {
        ctx.getSharedPreferences("kinkeep_sync", Context.MODE_PRIVATE).edit().clear().commit()
        db = inMemoryDb()
        source = FakeSource()
    }

    @After fun tearDown() { db.close() }

    private fun repo(granted: Boolean = true) =
        SyncRepository(ctx, db, db.contactDao(), db.interactionDao(), source, PermissionChecker { granted })

    private fun person(androidId: Long, name: String, number: String, starred: Boolean = false) =
        ImportedContact(androidId, name, starred, setOf(number))

    private fun call(id: Long, number: String, daysAgo: Int, type: Int = CallLog.Calls.OUTGOING_TYPE, seconds: Int = 300) =
        ImportedCall(id, number, now - daysAgo * day, seconds, type)

    private suspend fun contacts() = db.backupDao().contacts().associateBy { it.displayName }

    @Test fun importsPeopleAndSuggestsCircles() = runBlocking {
        source.contacts = listOf(
            person(1, "Mum", "0803 123 4567", starred = true),
            person(2, "Ada", "+234 802 000 0001"),
            person(3, "Bob", "0805 555 5555"),
        )
        source.calls = listOf(call(1, "0802 000 0001", 1), call(2, "0802 000 0001", 5), call(3, "0802 000 0001", 9))

        val summary = repo().sync()

        assertEquals(3, summary.newContacts)
        assertEquals(3, summary.processedCalls)
        val byName = contacts()
        assertEquals(Circle.CLOSE, byName.getValue("Mum").circle)    // starred
        assertEquals(Circle.FRIENDS, byName.getValue("Ada").circle)  // 3 conversations in 90 days
        assertEquals(Circle.IGNORED, byName.getValue("Bob").circle)  // nothing yet, so not tracked
    }

    @Test fun rejectedBlockedAndVoicemailEntriesAreNotCounted() = runBlocking {
        source.contacts = listOf(person(1, "Ada", "0802 000 0001"))
        source.calls = listOf(
            call(1, "0802 000 0001", 1, CallLog.Calls.REJECTED_TYPE),
            call(2, "0802 000 0001", 2, CallLog.Calls.BLOCKED_TYPE),
            call(3, "0802 000 0001", 3, CallLog.Calls.VOICEMAIL_TYPE),
            call(4, "0802 000 0001", 4, CallLog.Calls.INCOMING_TYPE),
        )
        assertEquals(1, repo().sync().processedCalls)
        assertEquals(1, db.backupDao().interactions().size)
    }

    @Test fun withoutPermissionNothingIsImportedAndTheCallerIsTold() = runBlocking {
        source.contacts = listOf(person(1, "Ada", "0802 000 0001"))
        source.calls = listOf(call(1, "0802 000 0001", 1))
        val summary = repo(granted = false).sync()
        assertTrue(summary.missingPermissions)
        assertEquals(0, db.backupDao().contacts().size)
        assertEquals(0, db.backupDao().interactions().size)
    }

    @Test fun laterSyncsOnlyReadNewCallsButNewPeopleTriggerAFullRescan() = runBlocking {
        source.contacts = listOf(person(1, "Ada", "0802 000 0001"))
        source.calls = listOf(call(2, "0802 000 0001", 3), call(3, "0802 000 0001", 2), call(4, "0802 000 0001", 1))
        val repo = repo()
        repo.sync()
        assertEquals(listOf(0L), source.callRequests)

        source.calls += call(5, "0802 000 0001", 0)
        repo.sync()
        assertEquals(4L, source.callRequests.last()) // continues after the last call it saw

        // A new person arrives whose older call has a lower id than the cursor.
        source.contacts += person(9, "Dan", "0806 666 6666")
        source.calls += call(1, "0806 666 6666", 20)
        repo.sync()
        assertEquals(0L, source.callRequests.last())
        val dan = contacts().getValue("Dan")
        assertEquals(1, db.backupDao().interactions().count { it.contactId == dan.id })
    }

    @Test fun rescanningNeverDuplicatesConversations() = runBlocking {
        source.contacts = listOf(person(1, "Ada", "0802 000 0001"))
        source.calls = listOf(call(1, "0802 000 0001", 1), call(2, "0802 000 0001", 2))
        val repo = repo()
        repo.sync()
        ctx.getSharedPreferences("kinkeep_sync", Context.MODE_PRIVATE).edit().clear().commit()
        repo.sync()
        assertEquals(2, db.backupDao().interactions().size)
    }

    @Test fun restoredContactsAreRelinkedByPhoneNumberNotDuplicated() = runBlocking {
        val id = db.contactDao().insert(ContactEntity(androidContactId = null, displayName = "Mum", circle = Circle.INNER, createdAt = 1L))
        val number = "0803 123 4567"
        db.contactDao().insertNumbers(listOf(PhoneNumberEntity(contactId = id, normalizedNumber = PhoneNormalizer.matchKey(number)!!, displayNumber = number)))
        source.contacts = listOf(person(77, "Mum", "+234 803 123 4567"))

        val summary = repo().sync()

        assertEquals(0, summary.newContacts)
        val all = db.backupDao().contacts()
        assertEquals(1, all.size)
        assertEquals(77L, all.single().androidContactId)
        assertEquals(Circle.INNER, all.single().circle) // the person's choice is kept
    }

    @Test fun birthdaysAreSaved() = runBlocking {
        source.contacts = listOf(person(5, "Ada", "0802 000 0001"))
        source.birthdays = mapOf(5L to "05-17")
        repo().sync()
        assertEquals("05-17", contacts().getValue("Ada").birthdayMonthDay)
    }
}
