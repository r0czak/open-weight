package com.openweight.app.transfer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Open Weight file format (see the Import/Export Format doc). One format for everything from a
 * single workout to a full backup; a partial export is the same file with fewer records.
 *
 * This version reads the workout part. Diet arrays and fields from newer app versions are ignored
 * (`ignoreUnknownKeys`), so files keep importing as the format grows.
 */
@Serializable
data class OpenWeightFile(
  val format: String,
  val formatVersion: Int,
  val appVersion: String? = null,
  val exportedAt: String? = null,
  val scope: TransferScope? = null,
  val data: TransferData = TransferData(),
) {
  companion object {
    const val FORMAT = "open-weight"
    const val CURRENT_VERSION = 1
  }
}

@Serializable data class TransferScope(val kind: String, val description: String? = null)

@Serializable
data class TransferData(
  val exercises: List<ExerciseDto> = emptyList(),
  val workouts: List<WorkoutDto> = emptyList(),
  val trainingBlocks: List<TrainingBlockDto> = emptyList(),
)

@Serializable
data class ExerciseDto(
  val id: String,
  val name: String,
  val muscleGroup: String = "",
  val equipment: String = "",
  val notes: String = "",
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

@Serializable
data class WorkoutDto(
  val id: String,
  val name: String,
  val type: String = "",
  val notes: String = "",
  val exercises: List<WorkoutExerciseDto> = emptyList(),
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

@Serializable
data class WorkoutExerciseDto(
  val exerciseId: String,
  val notes: String = "",
  val sets: List<SetDto> = emptyList(),
)

@Serializable
data class SetDto(
  val repsMin: Int,
  val repsMax: Int? = null,
  val weightKg: Double? = null,
  val restSeconds: Int = 90,
)

@Serializable
data class TrainingBlockDto(
  val id: String,
  val name: String,
  val startDate: String,
  val endDate: String,
  val notes: String = "",
  val workoutIds: List<String> = emptyList(),
  val schedule: List<ScheduleDayDto> = emptyList(),
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

/** [dayOfWeek] is MONDAY…SUNDAY; a null [workoutId] (or a missing day) means rest. */
@Serializable data class ScheduleDayDto(val dayOfWeek: String, val workoutId: String? = null)

internal val TransferJson = Json {
  ignoreUnknownKeys = true
  prettyPrint = true
  prettyPrintIndent = "  "
  explicitNulls = false
}
