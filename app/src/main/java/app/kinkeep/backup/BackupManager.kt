package app.kinkeep.backup

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import app.kinkeep.data.BackupDao
import app.kinkeep.data.KinDatabase
import app.kinkeep.data.SettingsStore
import app.kinkeep.security.KeystoreKey
import app.kinkeep.security.NoteCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class RestoreSummary(val contacts: Int, val interactions: Int)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: KinDatabase,
    private val dao: BackupDao,
    private val crypto: NoteCrypto,
    private val settings: SettingsStore,
) {
    /** Reads everything, decrypts notes, and seals it with the password. PBKDF2 is slow on purpose. */
    suspend fun export(password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val data = BackupData(
            contacts = dao.contacts().map { it.copy(notes = crypto.decrypt(it.notes)) },
            numbers = dao.numbers(),
            interactions = dao.interactions().map { it.copy(note = crypto.decrypt(it.note)) },
            states = dao.states(),
        )
        BackupCrypto.encrypt(BackupCodec.encode(data, System.currentTimeMillis()), password)
    }

    /**
     * Replaces everything in KinKeep with the backup. Phone-specific ids are dropped: contacts are
     * re-linked by phone number on the next import, and calls are matched by time so nothing is counted twice.
     */
    suspend fun restore(file: ByteArray, password: CharArray): RestoreSummary = withContext(Dispatchers.Default) {
        val data = BackupCodec.decode(BackupCrypto.decrypt(file, password))
        db.withTransaction {
            dao.deleteInteractions(); dao.deleteNumbers(); dao.deleteStates(); dao.deleteContacts()
            dao.insertContacts(data.contacts.map { it.copy(androidContactId = null, notes = crypto.encrypt(it.notes)) })
            dao.insertNumbers(data.numbers)
            dao.insertInteractions(data.interactions.map { it.copy(callLogId = null, note = crypto.encrypt(it.note)) })
            dao.insertStates(data.states)
        }
        ctx.getSharedPreferences("kinkeep_sync", Context.MODE_PRIVATE).edit().clear().apply()
        RestoreSummary(data.contacts.size, data.interactions.size)
    }

    /** Deletes every contact, call record, note, setting and the encryption key. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.deleteInteractions(); dao.deleteNumbers(); dao.deleteStates(); dao.deleteContacts()
        }
        ctx.getSharedPreferences("kinkeep_sync", Context.MODE_PRIVATE).edit().clear().apply()
        settings.reset()
        KeystoreKey.delete()
        NotificationManagerCompat.from(ctx).cancelAll()
    }
}
