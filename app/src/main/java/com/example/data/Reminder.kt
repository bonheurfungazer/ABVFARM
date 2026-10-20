package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val dueDate: Long,
    val priority: String = "Moyenne", // "Haute", "Moyenne", "Basse"
    val isCompleted: Boolean = false
)
