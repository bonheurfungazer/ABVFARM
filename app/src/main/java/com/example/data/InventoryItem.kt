package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String, // "Nourriture", "Médicament", "Matériel", "Autre"
    val quantity: Double,
    val unit: String = "kg",
    val pricePerUnit: Double = 0.0,
    val totalPrice: Double = 0.0,
    val purchaseDate: Long,
    val durationMonths: Int = 1,
    val notes: String = ""
)
