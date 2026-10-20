package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_records")
data class PhotoRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rabbitId: Int,
    val imageUri: String,
    val recordDate: Long,
    val note: String = ""
)
