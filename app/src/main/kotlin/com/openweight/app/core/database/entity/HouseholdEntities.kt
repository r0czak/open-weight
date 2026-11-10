package com.openweight.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Someone you cook for. You are not a row: your portion is the meal's servings. A member eats
 * [portion] times your portion (¾ = 0.75); [eatsByDefault] adds them to new and planned meals.
 */
@Entity(tableName = "household_member")
data class HouseholdMemberEntity(
  @PrimaryKey val id: String,
  val name: String,
  val portion: Double,
  val eatsByDefault: Boolean = true,
  val createdAt: Instant,
  val updatedAt: Instant,
)

/** A household member who also eats a calendar meal. */
@Entity(
  tableName = "meal_member",
  primaryKeys = ["mealId", "memberId"],
  foreignKeys =
    [
      ForeignKey(entity = MealEntryEntity::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE),
      ForeignKey(entity = HouseholdMemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.CASCADE),
    ],
  indices = [Index("memberId")],
)
data class MealMemberEntity(val mealId: String, val memberId: String, val createdAt: Instant)
