package com.openweight.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.progress.strength.StrengthStandard
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
  val weightUnit: WeightUnit = WeightUnit.KG,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  /** A notification when rest ends during live training. */
  val restAlerts: Boolean = true,
  /** Daily targets; null when not set. */
  val kcalTarget: Int? = null,
  val proteinTarget: Int? = null,
  /** The standards lifts are measured against for the power level. */
  val strengthStandard: StrengthStandard = StrengthStandard.MEN,
)

@Singleton
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

  val settings: Flow<Settings> =
    dataStore.data.map { prefs ->
      Settings(
        weightUnit = prefs[WEIGHT_UNIT].toEnumOr(WeightUnit.KG),
        themeMode = prefs[THEME_MODE].toEnumOr(ThemeMode.SYSTEM),
        restAlerts = prefs[REST_ALERTS] ?: true,
        kcalTarget = prefs[KCAL_TARGET],
        proteinTarget = prefs[PROTEIN_TARGET],
        strengthStandard = prefs[STRENGTH_STANDARD].toEnumOr(StrengthStandard.MEN),
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

  suspend fun setStrengthStandard(standard: StrengthStandard) {
    dataStore.edit { it[STRENGTH_STANDARD] = standard.name }
  }

  /** Sets or (null) clears the daily targets; values outside the limits are refused. */
  suspend fun setTargets(kcal: Int?, protein: Int?) {
    require(kcal == null || kcal in KCAL_RANGE) { "Calories out of range" }
    require(protein == null || protein in PROTEIN_RANGE) { "Protein out of range" }
    dataStore.edit {
      if (kcal == null) it.remove(KCAL_TARGET) else it[KCAL_TARGET] = kcal
      if (protein == null) it.remove(PROTEIN_TARGET) else it[PROTEIN_TARGET] = protein
    }
  }

  companion object {
    val KCAL_RANGE = 500..10_000
    val PROTEIN_RANGE = 10..500
    private val KCAL_TARGET = intPreferencesKey("kcal_target")
    private val PROTEIN_TARGET = intPreferencesKey("protein_target")
    private val STRENGTH_STANDARD = stringPreferencesKey("strength_standard")
    private val REST_ALERTS = booleanPreferencesKey("rest_alerts")
    private val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
  }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
  this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
