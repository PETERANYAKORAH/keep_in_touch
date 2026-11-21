package app.kinkeep.backup

import app.kinkeep.data.ContactEntity
import app.kinkeep.data.InteractionEntity
import app.kinkeep.data.PhoneNumberEntity
import app.kinkeep.data.ReminderStateEntity
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    private val fast = 1_000 // low PBKDF2 cost keeps the tests quick

    private val sample = BackupData(
        contacts = listOf(
            ContactEntity(1, 42L, "Mum", Circle.INNER, 5, "05-17", "Likes \"tea\" and 🙂", true, 1000L),
            ContactEntity(2, null, "Ada", Circle.FRIENDS, null, null, "", false, 2000L),
        ),
        numbers = listOf(PhoneNumberEntity(1, 1, "031234567", "0803 123 4567")),
        interactions = listOf(
            InteractionEntity(1, 1, 5000L, InteractionType.CALL, Direction.OUTGOING, 300, 77L, "weekly call"),
            InteractionEntity(2, 2, 6000L, InteractionType.MANUAL, Direction.INCOMING, 0, null, ""),
        ),
        states = listOf(ReminderStateEntity(1, 10L, 20L, 2026)),
    )

    @Test fun codecRoundTripKeepsEverythingIncludingNulls() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample, 123L))
        assertEquals(sample, decoded)
    }

    @Test fun codecRejectsGarbageAndUnknownVersions() {
        try { BackupCodec.decode("not json".toByteArray()); fail() } catch (_: BackupException) {}
        try { BackupCodec.decode("""{"version":99}""".toByteArray()); fail() } catch (_: BackupException) {}
    }

    @Test fun encryptedBackupRoundTrips() {
        val plain = BackupCodec.encode(sample, 1L)
        val file = BackupCrypto.encrypt(plain, "correct horse".toCharArray(), fast)
        assertArrayEquals(plain, BackupCrypto.decrypt(file, "correct horse".toCharArray()))
    }

    @Test fun fileDoesNotLeakContents() {
        val file = BackupCrypto.encrypt(BackupCodec.encode(sample, 1L), "correct horse".toCharArray(), fast)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("Mum"))
    }

    @Test fun wrongPasswordIsRejected() {
        val file = BackupCrypto.encrypt("data".toByteArray(), "right-password".toCharArray(), fast)
        try { BackupCrypto.decrypt(file, "wrong-password".toCharArray()); fail() } catch (e: BackupException) {
            assertTrue(e.message!!.contains("password", ignoreCase = true))
        }
    }

    @Test fun tamperedOrForeignFilesAreRejected() {
        val file = BackupCrypto.encrypt("data".toByteArray(), "right-password".toCharArray(), fast)
        val tampered = file.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        try { BackupCrypto.decrypt(tampered, "right-password".toCharArray()); fail() } catch (_: BackupException) {}
        try { BackupCrypto.decrypt(ByteArray(80), "x".toCharArray()); fail() } catch (_: BackupException) {}
        try { BackupCrypto.decrypt(ByteArray(3), "x".toCharArray()); fail() } catch (_: BackupException) {}
    }

    @Test fun everyBackupIsDifferent() {
        val a = BackupCrypto.encrypt("same".toByteArray(), "pw-pw-pw-pw".toCharArray(), fast)
        val b = BackupCrypto.encrypt("same".toByteArray(), "pw-pw-pw-pw".toCharArray(), fast)
        assertFalse(a.contentEquals(b))
        assertNotEquals(a.toList(), b.toList())
    }
}
