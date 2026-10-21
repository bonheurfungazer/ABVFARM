package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GenealogyHelper {

    data class CompatibilityResult(
        val male: Rabbit,
        val kinshipCoefficient: Double,
        val offspringInbreeding: Double, // Consanguinité des futurs lapereaux en %
        val riskLevel: RiskLevel,
        val commonAncestorsNames: List<String>
    )

    enum class RiskLevel(val label: String) {
        SAFE("Optimal (0%)"),
        MODERATE("Acceptable (< 6.25%)"),
        HIGH("Déconseillé (6.25% - 12.5%)"),
        CRITICAL("Critique (> 12.5% - Risque élevé)")
    }

    /**
     * Evaluates inbreeding and kinship between a target female and all candidate males
     * using the Henderson/Wright Numerator Relationship Matrix (A-Matrix).
     */
    suspend fun evaluateMatingCompatibility(
        female: Rabbit,
        candidateMales: List<Rabbit>,
        allRabbitsInFarm: List<Rabbit>
    ): List<CompatibilityResult> = withContext(Dispatchers.Default) {
        val rabbitMap = allRabbitsInFarm.associateBy { it.id }
        val sortedRabbits = topologicalSortPedigree(allRabbitsInFarm, rabbitMap)
        val indexMap = sortedRabbits.mapIndexed { index, rabbit -> rabbit.id to index }.toMap()
        val n = sortedRabbits.size

        val aMatrix = Array(n) { DoubleArray(n) }

        for (i in 0 until n) {
            val rabbitI = sortedRabbits[i]
            val sireIdx = rabbitI.fatherId?.let { indexMap[it] }
            val damIdx = rabbitI.motherId?.let { indexMap[it] }

            aMatrix[i][i] = if (sireIdx != null && damIdx != null) {
                1.0 + 0.5 * aMatrix[sireIdx][damIdx]
            } else {
                1.0
            }

            for (j in 0 until i) {
                val aSire = if (sireIdx != null) aMatrix[j][sireIdx] else 0.0
                val aDam = if (damIdx != null) aMatrix[j][damIdx] else 0.0
                val value = 0.5 * (aSire + aDam)
                aMatrix[i][j] = value
                aMatrix[j][i] = value
            }
        }

        val femaleIdx = indexMap[female.id]

        candidateMales.map { male ->
            val maleIdx = indexMap[male.id]
            val relationship = if (femaleIdx != null && maleIdx != null) {
                aMatrix[femaleIdx][maleIdx]
            } else {
                0.0
            }

            val offspringF = (relationship / 2.0) * 100.0
            val commonAncestors = findCommonAncestors(male, female, rabbitMap)

            val risk = when {
                offspringF == 0.0 -> RiskLevel.SAFE
                offspringF < 6.25 -> RiskLevel.MODERATE
                offspringF <= 12.5 -> RiskLevel.HIGH
                else -> RiskLevel.CRITICAL
            }

            CompatibilityResult(
                male = male,
                kinshipCoefficient = relationship * 100.0,
                offspringInbreeding = offspringF,
                riskLevel = risk,
                commonAncestorsNames = commonAncestors
            )
        }.sortedBy { it.offspringInbreeding }
    }

    private fun topologicalSortPedigree(
        allRabbits: List<Rabbit>,
        rabbitMap: Map<Int, Rabbit>
    ): List<Rabbit> {
        val visited = mutableSetOf<Int>()
        val result = mutableListOf<Rabbit>()

        fun dfs(rabbit: Rabbit, visiting: MutableSet<Int>) {
            if (rabbit.id in visited || rabbit.id in visiting) return
            visiting.add(rabbit.id)
            rabbit.fatherId?.let { rabbitMap[it] }?.let { dfs(it, visiting) }
            rabbit.motherId?.let { rabbitMap[it] }?.let { dfs(it, visiting) }
            visiting.remove(rabbit.id)
            visited.add(rabbit.id)
            result.add(rabbit)
        }

        allRabbits.forEach { dfs(it, mutableSetOf()) }
        return result
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

    /**
     * Calculates Wright's Coefficient of Inbreeding (F) for potential offspring of [maleId] and [femaleId].
     * Returns a percentage value between 0.0% and 100.0%.
     */
    fun calculateConsanguinity(maleId: Int?, femaleId: Int?, rabbits: List<Rabbit>): Double {
        if (maleId == null || femaleId == null || maleId <= 0 || femaleId <= 0) return 0.0
        if (maleId == femaleId) return 0.0

        val rabbitsMap = rabbits.associateBy { it.id }
        val maleRabbit = rabbitsMap[maleId] ?: return 0.0
        val femaleRabbit = rabbitsMap[femaleId] ?: return 0.0

        val maleHasSire = maleRabbit.fatherId != null && maleRabbit.fatherId != maleRabbit.id && rabbitsMap.containsKey(maleRabbit.fatherId)
        val maleHasDam = maleRabbit.motherId != null && maleRabbit.motherId != maleRabbit.id && rabbitsMap.containsKey(maleRabbit.motherId)
        val femaleHasSire = femaleRabbit.fatherId != null && femaleRabbit.fatherId != femaleRabbit.id && rabbitsMap.containsKey(femaleRabbit.fatherId)
        val femaleHasDam = femaleRabbit.motherId != null && femaleRabbit.motherId != femaleRabbit.id && rabbitsMap.containsKey(femaleRabbit.motherId)

        if (!maleHasSire && !maleHasDam && !femaleHasSire && !femaleHasDam) {
            return 0.0
        }

        if (maleRabbit.fatherId == femaleRabbit.id || maleRabbit.motherId == femaleRabbit.id ||
            femaleRabbit.fatherId == maleRabbit.id || femaleRabbit.motherId == maleRabbit.id) {
            return 25.0
        }

        if (maleHasSire && femaleHasSire && maleRabbit.fatherId == femaleRabbit.fatherId &&
            maleHasDam && femaleHasDam && maleRabbit.motherId == femaleRabbit.motherId) {
            return 25.0
        }

        val visitedMale = mutableSetOf<Int>()
        val visitedFemale = mutableSetOf<Int>()

        fun collectAncestors(currentId: Int?, visited: MutableSet<Int>) {
            if (currentId == null || currentId in visited) return
            visited.add(currentId)
            val r = rabbitsMap[currentId] ?: return
            val sId = if (r.fatherId != null && r.fatherId != r.id && rabbitsMap.containsKey(r.fatherId)) r.fatherId else null
            val dId = if (r.motherId != null && r.motherId != r.id && rabbitsMap.containsKey(r.motherId)) r.motherId else null
            collectAncestors(sId, visited)
            collectAncestors(dId, visited)
        }

        collectAncestors(maleId, visitedMale)
        collectAncestors(femaleId, visitedFemale)

        val sharedAncestors = visitedMale.intersect(visitedFemale)
        if (sharedAncestors.isEmpty()) {
            return 0.0
        }

        val sortedRabbits = topologicalSortPedigree(rabbits, rabbitsMap)
        val indexMap = sortedRabbits.mapIndexed { index, rabbit -> rabbit.id to index }.toMap()
        val n = sortedRabbits.size
        val aMatrix = Array(n) { DoubleArray(n) }

        for (i in 0 until n) {
            val rabbitI = sortedRabbits[i]
            val sireIdx = rabbitI.fatherId?.let { indexMap[it] }
            val damIdx = rabbitI.motherId?.let { indexMap[it] }

            aMatrix[i][i] = if (sireIdx != null && damIdx != null) {
                1.0 + 0.5 * aMatrix[sireIdx][damIdx]
            } else {
                1.0
            }

            for (j in 0 until i) {
                val aSire = if (sireIdx != null) aMatrix[j][sireIdx] else 0.0
                val aDam = if (damIdx != null) aMatrix[j][damIdx] else 0.0
                val value = 0.5 * (aSire + aDam)
                aMatrix[i][j] = value
                aMatrix[j][i] = value
            }
        }

        val fIdx = indexMap[femaleId]
        val mIdx = indexMap[maleId]
        if (fIdx != null && mIdx != null) {
            return (aMatrix[fIdx][mIdx] / 2.0) * 100.0
        }

        return 0.0
    }
}
