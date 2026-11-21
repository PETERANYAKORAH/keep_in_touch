package app.kinkeep.data

import android.Manifest
import android.content.Context
import androidx.room.withTransaction
import app.kinkeep.BuildConfig
import app.kinkeep.domain.Circle
import app.kinkeep.domain.CircleSuggester
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType
import app.kinkeep.domain.PhoneNormalizer
import app.kinkeep.domain.ScoringEngine
import app.kinkeep.widget.refreshWidgets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SyncSummary(val newContacts: Int, val processedCalls: Int, val missingPermissions: Boolean)

/**
 * Imports contacts, birthdays and the call log. Built for large phones: everything it needs is loaded
 * once into memory maps, so 5,000 contacts and 50,000 calls need a handful of queries, not tens of thousands.
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: KinDatabase,
    private val contactDao: ContactDao,
    private val interactionDao: InteractionDao,
    private val source: DeviceSource,
    private val permissions: PermissionChecker,
) {
    private val mutex = Mutex()
    private val prefs get() = ctx.getSharedPreferences("kinkeep_sync", Context.MODE_PRIVATE)

    suspend fun sync(): SyncSummary = mutex.withLock { withContext(Dispatchers.IO) { doSync() } }

    private suspend fun doSync(): SyncSummary {
        val now = System.currentTimeMillis()
        val canContacts = permissions.has(Manifest.permission.READ_CONTACTS)
        val canCalls = BuildConfig.CALL_LOG_ENABLED && permissions.has(Manifest.permission.READ_CALL_LOG)
        var newContacts = 0
        var processedCalls = 0
        val newlyAdded = mutableListOf<Pair<Long, Boolean>>() // contact id to starred

        if (canContacts) {
            val imported = source.readContacts()
            val birthdays = source.readBirthdays()
            db.withTransaction {
                val byId = contactDao.all().associateByTo(HashMap()) { it.id }
                val byAndroidId = HashMap<Long, ContactEntity>()
                byId.values.forEach { c -> c.androidContactId?.let { byAndroidId[it] = c } }
                val owners = contactDao.allNumbers().associateTo(HashMap()) { it.normalizedNumber to it.contactId }
                val newNumbers = ArrayList<PhoneNumberEntity>()

                for (ic in imported) {
                    val found = byAndroidId[ic.androidId] ?: relinkTarget(ic, owners, byId)?.also { contactDao.update(it) }
                    val contact: ContactEntity = if (found == null) {
                        val created = ContactEntity(
                            androidContactId = ic.androidId, displayName = ic.name, circle = Circle.IGNORED, createdAt = now,
                        )
                        val rowId = contactDao.insert(created)
                        if (rowId <= 0) continue
                        newContacts++
                        newlyAdded += rowId to ic.starred
                        created.copy(id = rowId)
                    } else if (found.displayName != ic.name) {
                        found.copy(displayName = ic.name).also { contactDao.update(it) }
                    } else {
                        found
                    }
                    byId[contact.id] = contact
                    byAndroidId[ic.androidId] = contact
                    for (raw in ic.numbers) {
                        val key = PhoneNormalizer.matchKey(raw) ?: continue
                        if (owners.putIfAbsent(key, contact.id) == null) {
                            newNumbers += PhoneNumberEntity(contactId = contact.id, normalizedNumber = key, displayNumber = raw)
                        }
                    }
                }
                contactDao.insertNumbers(newNumbers)

                for ((androidId, monthDay) in birthdays) {
                    val c = byAndroidId[androidId] ?: continue
                    if (c.birthdayMonthDay != monthDay) {
                        val updated = c.copy(birthdayMonthDay = monthDay)
                        contactDao.update(updated)
                        byAndroidId[androidId] = updated
                    }
                }
            }
        }

        if (canCalls) {
            val numberToContact = contactDao.allNumbers().associate { it.normalizedNumber to it.contactId }
            // New contacts may own older calls, so rescan from the start. Duplicates are ignored by the unique indexes.
            val after = if (newContacts > 0) 0L else prefs.getLong(KEY_CURSOR, 0L)
            val calls = source.readCalls(after)
            val entities = calls.mapNotNull { c ->
                val key = PhoneNormalizer.matchKey(c.number) ?: return@mapNotNull null
                val contactId = numberToContact[key] ?: return@mapNotNull null
                val direction = DeviceImporter.directionFor(c.type) ?: return@mapNotNull null
                InteractionEntity(
                    contactId = contactId, timestamp = c.timestamp, type = InteractionType.CALL,
                    direction = direction, durationSec = c.durationSec, callLogId = c.logId,
                )
            }
            processedCalls = entities.size
            val since = now - ScoringEngine.WINDOW_DAYS * ScoringEngine.DAY_MS
            val recentCounts = entities
                .filter { it.timestamp >= since && it.direction != Direction.MISSED }
                .groupingBy { it.contactId }.eachCount()
            db.withTransaction {
                interactionDao.insertAll(entities)
                for ((id, starred) in newlyAdded) {
                    val suggestion = CircleSuggester.suggest(starred, recentCounts[id] ?: 0)
                    if (suggestion != Circle.IGNORED) contactDao.get(id)?.let { contactDao.update(it.copy(circle = suggestion)) }
                }
            }
            calls.maxOfOrNull { it.logId }?.let { max -> prefs.edit().putLong(KEY_CURSOR, maxOf(max, after)).apply() }
        } else {
            // No call log access: still suggest starred contacts as Close.
            db.withTransaction {
                for ((id, starred) in newlyAdded) {
                    if (starred) contactDao.get(id)?.let { contactDao.update(it.copy(circle = Circle.CLOSE)) }
                }
            }
        }
        refreshWidgets(ctx)
        return SyncSummary(newContacts, processedCalls, missingPermissions = !canContacts || (BuildConfig.CALL_LOG_ENABLED && !canCalls))
    }

    /**
     * After a restore, contacts have no Android id. Match them to the phone's contacts by number so
     * nothing is duplicated. Only adopts a contact that is not already linked to another one.
     */
    private fun relinkTarget(ic: ImportedContact, owners: Map<String, Long>, byId: Map<Long, ContactEntity>): ContactEntity? {
        val ownerId = ic.numbers.firstNotNullOfOrNull { raw -> PhoneNormalizer.matchKey(raw)?.let { owners[it] } } ?: return null
        val owner = byId[ownerId] ?: return null
        return if (owner.androidContactId == null) owner.copy(androidContactId = ic.androidId) else null
    }

    private companion object { const val KEY_CURSOR = "last_call_log_id" }
}
