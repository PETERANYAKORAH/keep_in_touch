package app.kinkeep.di

import android.content.Context
import app.kinkeep.diagnostics.ErrorLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Injected so workers can be tested at a fixed time of day. */
    @Provides @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides @Singleton
    fun errorLog(@ApplicationContext ctx: Context): ErrorLog = ErrorLog(File(ctx.filesDir, "error.log"))
}
