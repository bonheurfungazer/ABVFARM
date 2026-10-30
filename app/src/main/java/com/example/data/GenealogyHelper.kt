package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Calculates inbreeding coefficients (F) and Wright's Additive Relationship Matrix (A)
 * using older-animal resolution and recursive memoization.
 */
object GenealogyHelper {

    data class CompatibilityResult(
        val male: Rabbit,
        val kinshipCoefficient: Double,
        val offspringInbreeding: Double, // Consanguinité des futurs lapereaux en %
        val riskLevel: RiskLevel,
        val tip: String = riskLevel.tip,
        val commonAncestorsNames: List<String> = emptyList()
    )

    enum class RiskLevel(val label: String, val tip: String) {
        SAFE("Aucune parenté connue", "Accouplement sûr. Vigueur hybride optimale."),
        MODERATE("Risque faible", "Parenté éloignée. Acceptable avec surveillance (< 6.25%)."),
        HIGH("Risque élevé", "Équivaut à des demi-frères/sœurs (6.25% - 12.5%). À éviter."),
        CRITICAL("Risque critique", "Parenté très proche (frère/sœur, parent/enfant > 12.5%). Déconseillé.")
    }

    class KinshipEngine(private val rabbits: List<Rabbit>) {
        private val rabbitMap: Map<Int, Rabbit> = rabbits.associateBy { it.id }
        private val memoA = mutableMapOf<String, Double>()
        private val memoF = mutableMapOf<Int, Double>()

        private fun isOlder(x: Int, y: Int): Boolean {
            val rx = rabbitMap[x]
            val ry = rabbitMap[y]
            val bx = rx?.dateOfBirth ?: Long.MAX_VALUE
            val by = ry?.dateOfBirth ?: Long.MAX_VALUE
            return if (bx == by) x < y else bx < by
        }

        fun F(x: Int?, depth: Int = 0): Double {
            if (x == null || depth > 12) return 0.0
            memoF[x]?.let { return it }
            val r = rabbitMap[x]
            val v = if (r != null && r.fatherId != null && r.motherId != null) {
                0.5 * A(r.fatherId, r.motherId, depth + 1)
            } else {
                0.0
            }
            memoF[x] = v
            return v
        }

        fun A(x: Int?, y: Int?, depth: Int = 0): Double {
            if (x == null || y == null || depth > 12) return 0.0
            if (x == y) return 1.0 + F(x, depth)

            val key = if (x < y) "$x|$y" else "$y|$x"
            memoA[key]?.let { return it }

            var a = x
            var b = y
            if (!isOlder(a, b)) {
                a = y
                b = x
            }
            val rb = rabbitMap[b]
            val v = if (rb != null) {
                0.5 * (A(a, rb.fatherId, depth + 1) + A(a, rb.motherId, depth + 1))
            } else {
                0.0
            }
            memoA[key] = v
            return v
        }

        fun calculateOffspringF(maleId: Int?, femaleId: Int?): Double {
            if (maleId == null || femaleId == null) return 0.0
            val aVal = A(maleId, femaleId)
            return (0.5 * aVal * 100.0).coerceIn(0.0, 100.0)
        }
    }

    fun riskOf(f: Double): RiskLevel {
        return when {
            f <= 0.0001 -> RiskLevel.SAFE
            f < 6.25 -> RiskLevel.MODERATE
            f <= 12.5 -> RiskLevel.HIGH
            else -> RiskLevel.CRITICAL
        }
    }

    /**
     * Calculates Wright's Coefficient of Inbreeding (F) for potential offspring of [maleId] and [femaleId].
     * Returns a percentage value between 0.0% and 100.0%.
     */
    fun calculateConsanguinity(maleId: Int?, femaleId: Int?, rabbits: List<Rabbit>): Double {
        if (maleId == null || femaleId == null || maleId <= 0 || femaleId <= 0) return 0.0
        if (maleId == femaleId) return 100.0
        return KinshipEngine(rabbits).calculateOffspringF(maleId, femaleId)
    }

    /**
     * Calculates an individual rabbit's inbreeding coefficient F in %.
     */
    fun calculateRabbitInbreeding(rabbitId: Int?, rabbits: List<Rabbit>): Double {
        if (rabbitId == null) return 0.0
        return (KinshipEngine(rabbits).F(rabbitId) * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Evaluates inbreeding and kinship between a target female and all candidate males.
     */
    suspend fun evaluateMatingCompatibility(
        female: Rabbit,
        candidateMales: List<Rabbit>,
        allRabbitsInFarm: List<Rabbit>
    ): List<CompatibilityResult> = withContext(Dispatchers.Default) {
        val engine = KinshipEngine(allRabbitsInFarm)
        val rabbitMap = allRabbitsInFarm.associateBy { it.id }

        candidateMales.map { male ->
            val relationship = engine.A(male.id, female.id)
            val offspringF = (0.5 * relationship * 100.0).coerceIn(0.0, 100.0)
            val risk = riskOf(offspringF)
            val commonAncestors = findCommonAncestors(male, female, rabbitMap)

            CompatibilityResult(
                male = male,
                kinshipCoefficient = relationship * 100.0,
                offspringInbreeding = offspringF,
                riskLevel = risk,
                tip = risk.tip,
                commonAncestorsNames = commonAncestors
            )
        }.sortedBy { it.offspringInbreeding }
    }

    private fun findCommonAncestors(
        male: Rabbit,
        female: Rabbit,
        rabbitMap: Map<Int, Rabbit>,
        maxDepth: Int = 5
    ): List<String> {
        fun collectAncestors(start: Rabbit): Set<Int> {
            val ancestors = mutableSetOf<Int>()
            fun walk(curr: Rabbit?, depth: Int) {
                if (curr == null || depth > maxDepth) return
                curr.fatherId?.let { id ->
                    if (ancestors.add(id)) walk(rabbitMap[id], depth + 1)
                }
                curr.motherId?.let { id ->
                    if (ancestors.add(id)) walk(rabbitMap[id], depth + 1)
                }
            }
            walk(start, 1)
            return ancestors
        }
        return collectAncestors(male).intersect(collectAncestors(female))
            .mapNotNull { rabbitMap[it]?.name }
    }

    fun getAncestorsWithDepths(rabbitId: Int?, rabbits: List<Rabbit>, depth: Int = 0, maxDepth: Int = 4): Map<Int, Int> {
        if (rabbitId == null || depth >= maxDepth) return emptyMap()
        val r = rabbits.find { it.id == rabbitId } ?: return emptyMap()

        val map = mutableMapOf<Int, Int>()
        map[r.id] = depth

        val validFatherId = if (r.fatherId != null && r.fatherId != r.id) r.fatherId else null
        val validMotherId = if (r.motherId != null && r.motherId != r.id) r.motherId else null

        val fatherAncestors = getAncestorsWithDepths(validFatherId, rabbits, depth + 1, maxDepth)
        val motherAncestors = getAncestorsWithDepths(validMotherId, rabbits, depth + 1, maxDepth)

        fatherAncestors.forEach { (id, d) ->
            map[id] = minOf(map[id] ?: Int.MAX_VALUE, d)
        }
        motherAncestors.forEach { (id, d) ->
            map[id] = minOf(map[id] ?: Int.MAX_VALUE, d)
        }

        return map
    }
}
