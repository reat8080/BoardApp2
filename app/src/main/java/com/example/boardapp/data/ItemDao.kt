package com.example.boardapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE (:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId")
    fun getItemsByParent(parentId: Long?): Flow<List<BoardItem>>

    @Query("SELECT * FROM items")
    fun getAllItems(): Flow<List<BoardItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: BoardItem)

    @Delete
    suspend fun deleteItem(item: BoardItem)
}
