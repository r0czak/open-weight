package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.openweight.app.core.database.entity.ShoppingItemEntity
import com.openweight.app.core.database.entity.ShoppingListEntity
import kotlinx.coroutines.flow.Flow

/** The shopping list. The app keeps one current list (the most recently generated). */
@Dao
interface ShoppingDao {
  @Query("SELECT * FROM shopping_list ORDER BY generatedAt DESC LIMIT 1")
  fun observeCurrentList(): Flow<ShoppingListEntity?>

  @Query("SELECT * FROM shopping_list ORDER BY generatedAt DESC LIMIT 1")
  suspend fun currentList(): ShoppingListEntity?

  @Query("SELECT * FROM shopping_item WHERE listId = :listId ORDER BY name COLLATE NOCASE")
  fun observeItems(listId: String): Flow<List<ShoppingItemEntity>>

  @Query("SELECT * FROM shopping_item WHERE listId = :listId")
  suspend fun getItems(listId: String): List<ShoppingItemEntity>

  @Query("SELECT * FROM shopping_item WHERE id = :id") suspend fun getItem(id: String): ShoppingItemEntity?

  @Insert suspend fun insertList(list: ShoppingListEntity)

  @Update suspend fun updateList(list: ShoppingListEntity)

  @Insert suspend fun insertItems(items: List<ShoppingItemEntity>)

  @Update suspend fun updateItems(items: List<ShoppingItemEntity>)

  @Query("DELETE FROM shopping_item WHERE id IN (:ids)") suspend fun deleteItems(ids: List<String>)

  @Query("UPDATE shopping_item SET checked = 0 WHERE listId = :listId") suspend fun uncheckAll(listId: String)
}
