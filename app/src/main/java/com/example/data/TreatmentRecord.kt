package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treatment_records")
data class TreatmentRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rabbitId: Int, // Refers to the rabbit
    val type: String, // "Vaccin", "Vermifuge", "Anti-coccidien", "Vitamines", "Autre soin"
    val designation: String, // Name of the product or care given
    val date: Long, // Date when given
    val dosage: String = "", // e.g. "0.5 mL"
    val notes: String = "",
    val nextDueDate: Long? = null // Optional, if it needs repeating
)
