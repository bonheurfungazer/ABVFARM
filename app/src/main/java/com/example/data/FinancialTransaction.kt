package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "financial_transactions")
data class FinancialTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val type: String,                   // "INCOME" ou "EXPENSE"
    val category: String,               // "VENTE_CHAIR", "VENTE_REPRODUCTEUR", "ALIMENT", "SANTE", "MATERIEL"
    val amountFcfa: Double,
    val description: String,
    val relatedBatchId: Long? = null,
    val relatedRabbitId: Int? = null
)
