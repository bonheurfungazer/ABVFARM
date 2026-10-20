package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mating_records")
data class MatingRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val maleId: Int,
    val femaleId: Int,
    val matingDate: Long,
    val expectedBirthDate: Long,
    val status: String = "Accouplé", // "Accouplé", "Gestation confirmée", "Mise bas", "Échoué"
    val notes: String = ""
)
