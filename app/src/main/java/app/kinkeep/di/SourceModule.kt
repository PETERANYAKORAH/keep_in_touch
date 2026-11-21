package app.kinkeep.di

import app.kinkeep.data.AndroidPermissionChecker
import app.kinkeep.data.DeviceImporter
import app.kinkeep.data.DeviceSource
import app.kinkeep.data.PermissionChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SourceModule {
    @Binds abstract fun deviceSource(impl: DeviceImporter): DeviceSource
    @Binds abstract fun permissionChecker(impl: AndroidPermissionChecker): PermissionChecker
}
