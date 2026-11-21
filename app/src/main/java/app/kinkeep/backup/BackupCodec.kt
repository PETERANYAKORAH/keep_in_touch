package app.kinkeep.backup

import app.kinkeep.data.ContactEntity
import app.kinkeep.data.InteractionEntity
import app.kinkeep.data.PhoneNumberEntity
import app.kinkeep.data.ReminderStateEntity
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object BackupCodec {
    const val VERSION = 1

    fun encode(data: BackupData, createdAt: Long): ByteArray {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("createdAt", createdAt)
        root.put("contacts", JSONArray().also { a -> data.contacts.forEach { a.put(contactJson(it)) } })
        root.put("numbers", JSONArray().also { a -> data.numbers.forEach { a.put(numberJson(it)) } })
        root.put("interactions", JSONArray().also { a -> data.interactions.forEach { a.put(interactionJson(it)) } })
        root.put("states", JSONArray().also { a -> data.states.forEach { a.put(stateJson(it)) } })
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): BackupData {
        try {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            if (root.optInt("version", -1) != VERSION) throw BackupException("This backup was made by an unknown version of KinKeep.")
            return BackupData(
                contacts = root.getJSONArray("contacts").objects().map(::contactFrom),
                numbers = root.getJSONArray("numbers").objects().map(::numberFrom),
                interactions = root.getJSONArray("interactions").objects().map(::interactionFrom),
                states = root.getJSONArray("states").objects().map(::stateFrom),
            )
        } catch (e: JSONException) {
            throw BackupException("The backup file is damaged.")
        } catch (e: IllegalArgumentException) {
            throw BackupException("The backup file is damaged.")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private fun contactJson(c: ContactEntity) = JSONObject().apply {
        put("id", c.id)
        c.androidContactId?.let { put("androidContactId", it) }
        put("displayName", c.displayName)
        put("circle", c.circle.name)
        c.cadenceOverrideDays?.let { put("cadenceOverrideDays", it) }
        c.birthdayMonthDay?.let { put("birthdayMonthDay", it) }
        put("notes", c.notes)
        put("muted", c.muted)
        put("createdAt", c.createdAt)
    }

    private fun contactFrom(o: JSONObject) = ContactEntity(
        id = o.getLong("id"),
        androidContactId = o.longOrNull("androidContactId"),
        displayName = o.getString("displayName"),
        circle = Circle.valueOf(o.getString("circle")),
        cadenceOverrideDays = o.intOrNull("cadenceOverrideDays"),
        birthdayMonthDay = o.stringOrNull("birthdayMonthDay"),
        notes = o.optString("notes", ""),
        muted = o.optBoolean("muted", false),
        createdAt = o.getLong("createdAt"),
    )

    private fun numberJson(n: PhoneNumberEntity) = JSONObject().apply {
        put("id", n.id); put("contactId", n.contactId)
        put("normalizedNumber", n.normalizedNumber); put("displayNumber", n.displayNumber)
    }

    private fun numberFrom(o: JSONObject) = PhoneNumberEntity(
        id = o.getLong("id"), contactId = o.getLong("contactId"),
        normalizedNumber = o.getString("normalizedNumber"), displayNumber = o.getString("displayNumber"),
    )

    private fun interactionJson(i: InteractionEntity) = JSONObject().apply {
        put("id", i.id); put("contactId", i.contactId); put("timestamp", i.timestamp)
        put("type", i.type.name); put("direction", i.direction.name); put("durationSec", i.durationSec)
        i.callLogId?.let { put("callLogId", it) }
        put("note", i.note)
    }

    private fun interactionFrom(o: JSONObject) = InteractionEntity(
        id = o.getLong("id"), contactId = o.getLong("contactId"), timestamp = o.getLong("timestamp"),
        type = InteractionType.valueOf(o.getString("type")), direction = Direction.valueOf(o.getString("direction")),
        durationSec = o.optInt("durationSec", 0), callLogId = o.longOrNull("callLogId"), note = o.optString("note", ""),
    )

    private fun stateJson(s: ReminderStateEntity) = JSONObject().apply {
        put("contactId", s.contactId); put("snoozedUntil", s.snoozedUntil)
        put("lastSentAt", s.lastSentAt); put("lastBirthdayYear", s.lastBirthdayYear)
    }

    private fun stateFrom(o: JSONObject) = ReminderStateEntity(
        contactId = o.getLong("contactId"), snoozedUntil = o.optLong("snoozedUntil", 0L),
        lastSentAt = o.optLong("lastSentAt", 0L), lastBirthdayYear = o.optInt("lastBirthdayYear", 0),
    )
}
