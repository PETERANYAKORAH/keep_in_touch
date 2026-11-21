package app.kinkeep.di

import android.content.Context
import androidx.room.Room
import app.kinkeep.BuildConfig
import app.kinkeep.data.ALL_MIGRATIONS
import app.kinkeep.data.BackupDao
import app.kinkeep.data.ContactDao
import app.kinkeep.data.InteractionDao
import app.kinkeep.data.KinDatabase
import app.kinkeep.data.ReminderStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): KinDatabase =
        Room.databaseBuilder(ctx, KinDatabase::class.java, "kinkeep.db")
            .addMigrations(*ALL_MIGRATIONS)
            .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigrationOnDowngrade() } // debug builds only
            .build()

    @Provides fun contactDao(db: KinDatabase): ContactDao = db.contactDao()
    @Provides fun interactionDao(db: KinDatabase): InteractionDao = db.interactionDao()
    @Provides fun reminderStateDao(db: KinDatabase): ReminderStateDao = db.reminderStateDao()
    @Provides fun backupDao(db: KinDatabase): BackupDao = db.backupDao()
}
