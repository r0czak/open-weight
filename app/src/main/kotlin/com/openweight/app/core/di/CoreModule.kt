package com.openweight.app.core.di

import com.openweight.app.core.SystemTimeAndIds
import com.openweight.app.core.TimeAndIds
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {
  @Binds abstract fun bindTimeAndIds(impl: SystemTimeAndIds): TimeAndIds
}
