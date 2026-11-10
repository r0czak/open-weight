package com.openweight.app.progress.strength

import com.openweight.app.core.Today
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.progress.BodyWeightRepository
import com.openweight.app.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/** The power level as screens show it: it needs a weigh-in, since every standard is relative to body weight. */
sealed interface PowerLevel {
  data object NeedsBodyWeight : PowerLevel

  data class Ready(val report: StrengthReport, val standard: StrengthStandard) : PowerLevel
}

@Singleton
class StrengthRepository
@Inject
constructor(private val db: OpenWeightDatabase, private val bodyWeights: BodyWeightRepository, private val settings: SettingsRepository, private val today: Today) {

  /** Done sets of the last [StrengthRules.WINDOW_DAYS] days, against the latest weigh-in up to today. */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun powerLevel(): Flow<PowerLevel> =
    today.date.flatMapLatest { day ->
      combine(db.sessionDao().observeDoneSetsSince(day.minusDays(StrengthRules.WINDOW_DAYS)), bodyWeights.entries(), settings.settings) { rows, weighIns, s ->
        val bodyWeight = weighIns.lastOrNull { !it.date.isAfter(day) }?.weightKg ?: return@combine PowerLevel.NeedsBodyWeight
        val sets = rows.filter { !it.plannedDate.isAfter(day) }.map { StrengthSet(it.set.exerciseId, it.set.exerciseName, it.set.weightKg, it.set.reps, it.plannedDate) }
        PowerLevel.Ready(StrengthRules.report(sets, bodyWeight, s.strengthStandard), s.strengthStandard)
      }
    }
}
