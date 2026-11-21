package app.kinkeep.di

import app.kinkeep.security.KeystoreKey
import app.kinkeep.security.NoteCrypto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides @Singleton
    fun noteCrypto(): NoteCrypto = NoteCrypto { KeystoreKey.get() }
}
