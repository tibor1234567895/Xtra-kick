package com.github.andreyasadchy.xtra.kick.storage

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KickTokenModule {

    @Binds
    @Singleton
    abstract fun bindKickTokenProvider(store: KickTokenStore): KickTokenProvider
}
