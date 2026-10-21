package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treatment_records")
data class TreatmentRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rabbitId: Int, // Refers to the rabbit
    val type: String, // "Vaccin", "Vermifuge", "Anti-coccidien", "Vitamines", "Antibiotique", "Autre soin"
    val designation: String, // Name of the product or care given
    val date: Long, // Date when given
    val dosage: String = "", // e.g. "0.5 mL"
    val notes: String = "",
    val nextDueDate: Long? = null, // Optional, if it needs repeating
    val withdrawalDays: Int = 0, // Délai d'attente sanitaire en jours
    val withdrawalEndDate: Long? = null // Date de fin de délai d'attente
) {
    /**
     * Checks if this treatment imposes an active sanitary withdrawal period at [currentTime].
     */
    fun isUnderWithdrawal(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (withdrawalEndDate != null && withdrawalEndDate > currentTime) return true
        if (withdrawalDays > 0) {
            val calculatedEnd = date + (withdrawalDays * 86_400_000L)
            return calculatedEnd > currentTime
        }
        return false
    }

    /**
     * Returns the effective timestamp when the sanitary withdrawal expires.
     */
    fun effectiveWithdrawalEndTime(): Long? {
        if (withdrawalEndDate != null) return withdrawalEndDate
        if (withdrawalDays > 0) return date + (withdrawalDays * 86_400_000L)
        return null
    }
}
