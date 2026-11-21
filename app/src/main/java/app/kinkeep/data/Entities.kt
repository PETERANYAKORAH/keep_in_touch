package app.kinkeep.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType

@Entity(tableName = "contacts", indices = [Index("androidContactId", unique = true)])
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val androidContactId: Long?,           // null for contacts created inside KinKeep
    val displayName: String,
    val circle: Circle = Circle.FRIENDS,
    val cadenceOverrideDays: Int? = null,  // null = use the circle's default
    val birthdayMonthDay: String? = null,  // "MM-dd"
    val notes: String = "",                // encrypted at rest in Phase 5
    val muted: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "phone_numbers",
    foreignKeys = [ForeignKey(ContactEntity::class, ["id"], ["contactId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("normalizedNumber", unique = true), Index("contactId")],
)
data class PhoneNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val normalizedNumber: String,
    val displayNumber: String,             // as stored in the phone, used to dial or text
)

@Entity(
    tableName = "interactions",
    foreignKeys = [ForeignKey(ContactEntity::class, ["id"], ["contactId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index("contactId", "timestamp", "type", "direction", unique = true),
        Index("callLogId", unique = true),
        Index("timestamp"),
    ],
)
data class InteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val timestamp: Long,
    val type: InteractionType,
    val direction: Direction,
    val durationSec: Int = 0,
    val callLogId: Long? = null,           // dedupes incremental call-log syncs
    val note: String = "",
)

/** One row per contact: reminder bookkeeping kept separate from the contact itself. */
@Entity(tableName = "reminder_state")
data class ReminderStateEntity(
    @PrimaryKey val contactId: Long,
    val snoozedUntil: Long = 0L,
    val lastSentAt: Long = 0L,
    val lastBirthdayYear: Int = 0,
)
