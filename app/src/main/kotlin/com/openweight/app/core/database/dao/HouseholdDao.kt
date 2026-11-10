package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openweight.app.core.database.entity.HouseholdMemberEntity
import com.openweight.app.core.database.entity.MealMemberEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdDao {
  @Query("SELECT * FROM household_member ORDER BY createdAt") fun observeMembers(): Flow<List<HouseholdMemberEntity>>

  @Query("SELECT * FROM household_member ORDER BY createdAt") suspend fun getMembers(): List<HouseholdMemberEntity>

  @Query("SELECT * FROM household_member WHERE id = :id") suspend fun getMember(id: String): HouseholdMemberEntity?

  @Insert suspend fun insertMember(member: HouseholdMemberEntity)

  @Update suspend fun updateMember(member: HouseholdMemberEntity)

  @Query("DELETE FROM household_member WHERE id = :id") suspend fun deleteMember(id: String)

  @Query("SELECT mm.* FROM meal_member mm JOIN meal_entry m ON m.id = mm.mealId WHERE m.date BETWEEN :from AND :to")
  fun observeMealMembers(from: LocalDate, to: LocalDate): Flow<List<MealMemberEntity>>

  @Query("SELECT mm.* FROM meal_member mm JOIN meal_entry m ON m.id = mm.mealId WHERE m.date BETWEEN :from AND :to")
  suspend fun getMealMembers(from: LocalDate, to: LocalDate): List<MealMemberEntity>

  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMealMembers(rows: List<MealMemberEntity>)

  @Query("DELETE FROM meal_member WHERE mealId = :mealId") suspend fun deleteMealMembers(mealId: String)
}
