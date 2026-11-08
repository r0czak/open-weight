package com.openweight.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
  @get:Rule val tmp = TemporaryFolder()

  private fun kotlinx.coroutines.test.TestScope.repository(file: File) =
    SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })

  @Test
  fun defaults_areKilogramsAndSystemTheme() = runTest {
    val repository = repository(File(tmp.root, "settings.preferences_pb"))

    assertEquals(Settings(WeightUnit.KG, ThemeMode.SYSTEM), repository.settings.first())
  }

  @Test
  fun changes_arePersisted() = runTest {
    val repository = repository(File(tmp.root, "settings.preferences_pb"))

    repository.setWeightUnit(WeightUnit.LB)
    repository.setThemeMode(ThemeMode.DARK)

    assertEquals(Settings(WeightUnit.LB, ThemeMode.DARK), repository.settings.first())
  }
}
