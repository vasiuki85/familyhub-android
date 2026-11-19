package com.familyhub.di

import com.familyhub.data.backup.Argon2KtKeyDeriver
import com.familyhub.data.backup.Argon2idKeyDeriver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    @Singleton
    abstract fun bindArgon2idKeyDeriver(impl: Argon2KtKeyDeriver): Argon2idKeyDeriver
}
