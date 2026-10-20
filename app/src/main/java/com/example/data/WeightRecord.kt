package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rabbitId: Int,
    val weight: Double,
    val recordDate: Long,
    val notes: String = ""
)
