package com.openweight.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** One weigh-in; at most one per day (a second one that day replaces it). */
@Entity(tableName = "body_weight", indices = [Index(value = ["date"], unique = true)])
data class BodyWeightEntity(
  @PrimaryKey val id: String,
  val date: LocalDate,
  val weightKg: Double,
  val createdAt: Instant,
  val updatedAt: Instant,
)
