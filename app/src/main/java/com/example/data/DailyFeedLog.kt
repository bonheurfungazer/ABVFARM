package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_feed_logs")
data class DailyFeedLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val quantityGrams: Double, // The exact feed weight given in grams or kilograms
    val feedDate: Long, // Date when the distribution was registered
    val notes: String = ""
)
