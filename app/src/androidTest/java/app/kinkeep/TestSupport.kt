package app.kinkeep

import android.content.Context
import androidx.annotation.StringRes
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.kinkeep.data.DeviceSource
import app.kinkeep.data.ImportedCall
import app.kinkeep.data.ImportedContact
import app.kinkeep.data.KinDatabase
import app.kinkeep.security.NoteCrypto
import javax.crypto.KeyGenerator

fun testContext(): Context = ApplicationProvider.getApplicationContext()

fun inMemoryDb(): KinDatabase =
    Room.inMemoryDatabaseBuilder(testContext(), KinDatabase::class.java).allowMainThreadQueries().build()

/** A software AES key, so tests do not depend on the device keystore. */
fun softwareCrypto(): NoteCrypto {
    val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    return NoteCrypto { key }
}

fun str(@StringRes id: Int, vararg args: Any): String = testContext().getString(id, *args)

class FakeSource(
    var contacts: List<ImportedContact> = emptyList(),
    var birthdays: Map<Long, String> = emptyMap(),
    var calls: List<ImportedCall> = emptyList(),
) : DeviceSource {
    val callRequests = mutableListOf<Long>()
    override fun readContacts() = contacts
    override fun readBirthdays() = birthdays
    override fun readCalls(afterLogId: Long): List<ImportedCall> {
        callRequests += afterLogId
        return calls.filter { it.logId > afterLogId }
    }
}
