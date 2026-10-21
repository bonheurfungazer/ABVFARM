package com.example

import com.example.data.GenealogyHelper
import com.example.data.Rabbit
import com.example.data.TreatmentRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GenealogyAndWithdrawalTest {

    @Test
    fun testTreatmentRecordWithdrawal() {
        val now = System.currentTimeMillis()
        val activeTreatment = TreatmentRecord(
            id = 1,
            rabbitId = 10,
            type = "Antibiotique",
            designation = "Oxytétracycline",
            date = now - (2 * 86_400_000L),
            dosage = "0.5 mL",
            withdrawalDays = 7,
            withdrawalEndDate = now + (5 * 86_400_000L)
        )

        assertTrue(activeTreatment.isUnderWithdrawal(now))
        assertNotNull(activeTreatment.effectiveWithdrawalEndTime())

        val expiredTreatment = TreatmentRecord(
            id = 2,
            rabbitId = 10,
            type = "Vermifuge",
            designation = "Ivermectine",
            date = now - (15 * 86_400_000L),
            dosage = "0.2 mL",
            withdrawalDays = 7,
            withdrawalEndDate = now - (8 * 86_400_000L)
        )

        assertFalse(expiredTreatment.isUnderWithdrawal(now))
    }

    @Test
    fun testGenealogyCompatibilityEvaluation() = runBlocking {
        // Founders
        val sire = Rabbit(id = 1, name = "Grand-Père", sex = "Male", dateOfBirth = 1000L)
        val dam = Rabbit(id = 2, name = "Grand-Mère", sex = "Female", dateOfBirth = 1000L)

        // Full Siblings
        val brother = Rabbit(id = 3, name = "Frère", sex = "Male", dateOfBirth = 2000L, fatherId = 1, motherId = 2)
        val sister = Rabbit(id = 4, name = "Sœur", sex = "Female", dateOfBirth = 2000L, fatherId = 1, motherId = 2)

        // Unrelated male
        val unrelatedMale = Rabbit(id = 5, name = "Mâle Externe", sex = "Male", dateOfBirth = 2000L)

        val farm = listOf(sire, dam, brother, sister, unrelatedMale)

        val results = GenealogyHelper.evaluateMatingCompatibility(
            female = sister,
            candidateMales = listOf(brother, unrelatedMale),
            allRabbitsInFarm = farm
        )

        assertEquals(2, results.size)

        // Unrelated should be first (safest, 0% consanguinity)
        val safeMatch = results[0]
        assertEquals(unrelatedMale.id, safeMatch.male.id)
        assertEquals(0.0, safeMatch.offspringInbreeding, 0.001)
        assertEquals(GenealogyHelper.RiskLevel.SAFE, safeMatch.riskLevel)
        assertTrue(safeMatch.commonAncestorsNames.isEmpty())

        // Full siblings should be CRITICAL with 25% Wright's inbreeding
        val siblingMatch = results[1]
        assertEquals(brother.id, siblingMatch.male.id)
        assertEquals(25.0, siblingMatch.offspringInbreeding, 0.001)
        assertEquals(GenealogyHelper.RiskLevel.CRITICAL, siblingMatch.riskLevel)
        assertTrue(siblingMatch.commonAncestorsNames.contains("Grand-Père"))
        assertTrue(siblingMatch.commonAncestorsNames.contains("Grand-Mère"))
    }
}
