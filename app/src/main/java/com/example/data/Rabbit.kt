package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rabbits")
data class Rabbit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sex: String, // "Male", "Female", "Indéterminé"
    val dateOfBirth: Long,
    val breed: String = "Inconnu",
    val cageNumber: String = "",
    val weight: Double = 0.0,
    val fatherId: Int? = null,
    val motherId: Int? = null,
    val notes: String = "",
    val isActive: Boolean = true,
    val status: String = "Actif", // "Actif", "Vendu", "Mort", "Abattu", "Externe", "Donné"
    val imageUri: String? = null
)
