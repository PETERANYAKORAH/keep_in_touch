package app.kinkeep.data

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import app.kinkeep.domain.BirthdayParser
import app.kinkeep.domain.Direction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedContact(val androidId: Long, val name: String, val starred: Boolean, val numbers: Set<String>)
data class ImportedCall(val logId: Long, val number: String?, val timestamp: Long, val durationSec: Int, val type: Int)

/** Reads the system contacts and call log. Callers must hold the matching runtime permission. */
@Singleton
class DeviceImporter @Inject constructor(@ApplicationContext private val ctx: Context) : DeviceSource {

    override fun readContacts(): List<ImportedContact> {
        val names = HashMap<Long, String>()
        val starred = HashMap<Long, Boolean>()
        val numbers = LinkedHashMap<Long, MutableSet<String>>()
        val projection = arrayOf(
            Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, ContactsContract.Contacts.STARRED,
        )
        ctx.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val nameCol = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
            val numCol = c.getColumnIndexOrThrow(Phone.NUMBER)
            val starCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                names.putIfAbsent(id, c.getString(nameCol).orEmpty())
                starred[id] = c.getInt(starCol) == 1
                val n = c.getString(numCol)
                if (!n.isNullOrBlank()) numbers.getOrPut(id) { mutableSetOf() }.add(n)
            }
        }
        return numbers.map { (id, set) ->
            ImportedContact(id, names[id].orEmpty().ifBlank { set.first() }, starred[id] == true, set)
        }
    }

    /** Android contact id to "MM-dd" for contacts that have a birthday saved. */
    override fun readBirthdays(): Map<Long, String> {
        val out = HashMap<Long, String>()
        val projection = arrayOf(ContactsContract.Data.CONTACT_ID, Event.START_DATE)
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?"
        val args = arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString())
        ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val dateCol = c.getColumnIndexOrThrow(Event.START_DATE)
            while (c.moveToNext()) {
                BirthdayParser.monthDay(c.getString(dateCol))?.let { out[c.getLong(idCol)] = it }
            }
        }
        return out
    }

    /** Call-log rows with _ID greater than [afterLogId], oldest first. */
    override fun readCalls(afterLogId: Long): List<ImportedCall> {
        val out = ArrayList<ImportedCall>()
        val projection = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE,
        )
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection,
            "${CallLog.Calls._ID} > ?", arrayOf(afterLogId.toString()),
            "${CallLog.Calls._ID} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numCol = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val dateCol = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durCol = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val typeCol = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            while (c.moveToNext()) {
                out += ImportedCall(c.getLong(idCol), c.getString(numCol), c.getLong(dateCol), c.getInt(durCol), c.getInt(typeCol))
            }
        }
        return out
    }

    companion object {
        /** Rejected, blocked, voicemail and answered-elsewhere calls are not counted as contact. */
        fun directionFor(type: Int): Direction? = when (type) {
            CallLog.Calls.INCOMING_TYPE -> Direction.INCOMING
            CallLog.Calls.OUTGOING_TYPE -> Direction.OUTGOING
            CallLog.Calls.MISSED_TYPE -> Direction.MISSED
            else -> null
        }
    }
}
