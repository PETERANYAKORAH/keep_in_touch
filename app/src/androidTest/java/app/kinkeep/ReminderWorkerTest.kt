package app.kinkeep

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.kinkeep.data.ContactEntity
import app.kinkeep.data.ContactRepository
import app.kinkeep.data.InteractionEntity
import app.kinkeep.data.KinDatabase
import app.kinkeep.data.PhoneNumberEntity
import app.kinkeep.data.SettingsStore
import app.kinkeep.diagnostics.ErrorLog
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType
import app.kinkeep.domain.ReminderKind
import app.kinkeep.notify.NotificationHelper
import app.kinkeep.work.ReminderWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Runs the real worker against an in-memory database at a fixed time of day. Needs notifications to be allowed. */
@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {
    private val ctx = testContext()
    private val noon = Clock.fixed(Instant.parse("2027-05-18T12:00:00Z"), ZoneOffset.UTC)
    private val night = Clock.fixed(Instant.parse("2027-05-18T23:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: KinDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: ContactRepository

    @Before fun setUp() {
        db = inMemoryDb()
        settings = SettingsStore(ctx).also { it.reset() }
        settings.update { it.copy(defaultHour = 9) } // the test clock reads 12:00
        repo = ContactRepository(ctx, db.contactDao(), db.interactionDao(), db.reminderStateDao(), softwareCrypto())
        NotificationHelper.createChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ctx.getSystemService(NotificationManager::class.java).cancelAll()
        assumeTrue("Notifications must be allowed for this test", NotificationHelper.canNotify(ctx))
    }

    @After fun tearDown() { db.close() }

    private fun worker(clock: Clock): ReminderWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                ReminderWorker(
                    appContext, workerParameters, settings, db.contactDao(), db.interactionDao(), db.reminderStateDao(),
                    repo, clock, ErrorLog(File(ctx.cacheDir, "worker-test.log")),
                )
        }
        return TestListenableWorkerBuilder<ReminderWorker>(ctx).setWorkerFactory(factory).build()
    }

    /** A friend you last spoke to 60 days ago, with a 30-day rhythm: clearly overdue. */
    private suspend fun seedOverdueFriend(clock: Clock): Long {
        val id = db.contactDao().insert(ContactEntity(androidContactId = 1, displayName = "Ada Obi", circle = Circle.FRIENDS, createdAt = 1L))
        db.contactDao().insertNumbers(listOf(PhoneNumberEntity(contactId = id, normalizedNumber = "020000001", displayNumber = "0802 000 0001")))
        db.interactionDao().insertAll(
            listOf(InteractionEntity(contactId = id, timestamp = clock.millis() - 60L * 86_400_000L, type = InteractionType.CALL, direction = Direction.OUTGOING, durationSec = 600)),
        )
        return id
    }

    private fun waitForNotification(id: Int): Boolean {
        val manager = ctx.getSystemService(NotificationManager::class.java)
        repeat(20) {
            if (manager.activeNotifications.any { it.id == id }) return true
            Thread.sleep(100)
        }
        return false
    }

    @Test fun overdueFriendGetsOneReminderAndItIsRecorded() = runBlocking {
        val id = seedOverdueFriend(noon)
        worker(noon).doWork()
        assertEquals(noon.millis(), db.reminderStateDao().get(id)!!.lastSentAt)
        assertTrue(waitForNotification(NotificationHelper.notificationId(id, ReminderKind.OVERDUE)))
    }

    @Test fun theSamePersonIsNotRemindedAgainStraightAway() = runBlocking {
        val id = seedOverdueFriend(noon)
        worker(noon).doWork()
        val first = db.reminderStateDao().get(id)!!.lastSentAt
        worker(Clock.fixed(Instant.parse("2027-05-18T14:00:00Z"), ZoneOffset.UTC)).doWork()
        assertEquals(first, db.reminderStateDao().get(id)!!.lastSentAt)
    }

    @Test fun nothingIsSentDuringQuietHours() = runBlocking {
        val id = seedOverdueFriend(night)
        worker(night).doWork()
        assertNull(db.reminderStateDao().get(id))
    }

    @Test fun nothingIsSentWhilePausedOrSwitchedOff() = runBlocking {
        val id = seedOverdueFriend(noon)
        settings.update { it.copy(pausedUntil = noon.millis() + 3_600_000L) }
        worker(noon).doWork()
        assertNull(db.reminderStateDao().get(id))
        settings.update { it.copy(pausedUntil = 0L, enabled = false) }
        worker(noon).doWork()
        assertNull(db.reminderStateDao().get(id))
    }

    @Test fun everyRunRecordsWhenItLastChecked() = runBlocking {
        worker(noon).doWork()
        assertEquals(noon.millis(), settings.current.lastRunAt)
        assertNotNull(settings.current.lastRunAt)
    }
}
