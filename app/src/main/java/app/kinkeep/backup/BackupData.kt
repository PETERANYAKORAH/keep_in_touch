package app.kinkeep.backup

import app.kinkeep.data.ContactEntity
import app.kinkeep.data.InteractionEntity
import app.kinkeep.data.PhoneNumberEntity
import app.kinkeep.data.ReminderStateEntity

/** Everything KinKeep stores. Notes inside are plain text here because the whole file is encrypted. */
data class BackupData(
    val contacts: List<ContactEntity>,
    val numbers: List<PhoneNumberEntity>,
    val interactions: List<InteractionEntity>,
    val states: List<ReminderStateEntity>,
)

class BackupException(message: String) : Exception(message)
