package com.example.boardapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class BoardItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val imageUri: String,
    val audioUri: String = "",
    val parentId: Long? = null
)
