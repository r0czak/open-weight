package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.openweight.app.core.database.entity.BodyWeightEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyWeightDao {
  @Query("SELECT * FROM body_weight ORDER BY date") fun observeAll(): Flow<List<BodyWeightEntity>>

  @Query("SELECT * FROM body_weight WHERE date = :date") suspend fun getByDate(date: LocalDate): BodyWeightEntity?

  @Query("SELECT * FROM body_weight WHERE id = :id") suspend fun get(id: String): BodyWeightEntity?

  @Insert suspend fun insert(entry: BodyWeightEntity)

  @Update suspend fun update(entry: BodyWeightEntity)

  @Query("DELETE FROM body_weight WHERE id = :id") suspend fun delete(id: String)
}
