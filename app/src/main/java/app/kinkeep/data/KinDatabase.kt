package app.kinkeep.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ContactEntity::class, PhoneNumberEntity::class, InteractionEntity::class, ReminderStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KinDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun interactionDao(): InteractionDao
    abstract fun reminderStateDao(): ReminderStateDao
    abstract fun backupDao(): BackupDao
}
