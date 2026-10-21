package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fattening_batches",
    foreignKeys = [
        ForeignKey(
            entity = MatingRecord::class,
            parentColumns = ["id"],
            childColumns = ["matingRecordId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("matingRecordId")]
)
data class FatteningBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchCode: String,              // Ex: "LOT-2026-10-A"
    val matingRecordId: Int? = null,    // Portée d'origine
    val weaningDate: Long,              // Date de sevrage
    val initialCount: Int,              // Nombre au sevrage
    val currentCount: Int,              // Nombre actuel (diminue si mortalité ou vente)
    val mortalityCount: Int = 0,
    val cageLocation: String,           // Cage ou parc d'engraissement
    val averageWeightGrams: Int = 0,    // Poids moyen du lot en grammes
    val targetSaleDate: Long,           // Date prévue de vente (ex: J+45 après sevrage)
    val status: String = "ACTIVE"       // ACTIVE, SOLD_OUT, QUARANTINE
)
