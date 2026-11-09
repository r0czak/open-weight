package com.openweight.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.openweight.app.core.database.OpenWeightDatabase
import dagger.Module
import dagger.Provides
import com.openweight.app.transfer.AppInfo
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): OpenWeightDatabase =
    Room.databaseBuilder(context, OpenWeightDatabase::class.java, OpenWeightDatabase.NAME).build()

  @Provides
  @Singleton
  fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

  @Provides
  fun provideAppInfo(@ApplicationContext context: Context): AppInfo = AppInfo {
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
  }
}
