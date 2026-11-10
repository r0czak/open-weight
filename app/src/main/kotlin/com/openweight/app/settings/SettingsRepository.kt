package com.openweight.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
  val weightUnit: WeightUnit = WeightUnit.KG,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  /** A notification when rest ends during live training. */
  val restAlerts: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

  val settings: Flow<Settings> =
    dataStore.data.map { prefs ->
      Settings(
        weightUnit = prefs[WEIGHT_UNIT].toEnumOr(WeightUnit.KG),
        themeMode = prefs[THEME_MODE].toEnumOr(ThemeMode.SYSTEM),
        restAlerts = prefs[REST_ALERTS] ?: true,
      )
    }

  suspend fun setWeightUnit(unit: WeightUnit) {
    dataStore.edit { it[WEIGHT_UNIT] = unit.name }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.edit { it[THEME_MODE] = mode.name }
  }

  suspend fun setRestAlerts(on: Boolean) {
    dataStore.edit { it[REST_ALERTS] = on }
  }

  private companion object {
    val REST_ALERTS = booleanPreferencesKey("rest_alerts")
    val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
    val THEME_MODE = stringPreferencesKey("theme_mode")
  }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
  this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
