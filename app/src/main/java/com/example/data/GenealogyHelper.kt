package com.example.data

object GenealogyHelper {

    fun getAncestorsWithDepths(rabbitId: Int?, rabbits: List<Rabbit>, depth: Int = 0, maxDepth: Int = 4): Map<Int, Int> {
        if (rabbitId == null || depth >= maxDepth) return emptyMap()
        val r = rabbits.find { it.id == rabbitId } ?: return emptyMap()
        
        val map = mutableMapOf<Int, Int>()
        map[r.id] = depth
        
        // Prevent cycle (e.g. if fatherId == rabbitId)
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
     *
     * Inbreeding coefficient of progeny = (Additive Relationship Coefficient A(Sire, Dam) / 2) * 100
     * - Unrelated individuals: 0.0%
     * - First cousins: 6.25%
     * - Half siblings / Grandparent-Grandchild: 12.5%
     * - Full siblings / Parent-Offspring: 25.0%
     */
    fun calculateConsanguinity(maleId: Int?, femaleId: Int?, rabbits: List<Rabbit>): Double {
        if (maleId == null || femaleId == null || maleId <= 0 || femaleId <= 0) return 0.0
        if (maleId == femaleId) return 0.0 // Mating with self is invalid, return 0.0%

        val rabbitsMap = rabbits.associateBy { it.id }
        val maleRabbit = rabbitsMap[maleId] ?: return 0.0
        val femaleRabbit = rabbitsMap[femaleId] ?: return 0.0

        // Quick check: if neither rabbit has any known parents in database, they are strictly unrelated founders
        val maleHasSire = maleRabbit.fatherId != null && maleRabbit.fatherId != maleRabbit.id && rabbitsMap.containsKey(maleRabbit.fatherId)
        val maleHasDam = maleRabbit.motherId != null && maleRabbit.motherId != maleRabbit.id && rabbitsMap.containsKey(maleRabbit.motherId)
        val femaleHasSire = femaleRabbit.fatherId != null && femaleRabbit.fatherId != femaleRabbit.id && rabbitsMap.containsKey(femaleRabbit.fatherId)
        val femaleHasDam = femaleRabbit.motherId != null && femaleRabbit.motherId != femaleRabbit.id && rabbitsMap.containsKey(femaleRabbit.motherId)

        // If neither has any ancestors in database, relationship is strictly 0.0%
        if (!maleHasSire && !maleHasDam && !femaleHasSire && !femaleHasDam) {
            return 0.0
        }

        // Direct parent-child relationship quick validation
        if (maleRabbit.fatherId == femaleRabbit.id || maleRabbit.motherId == femaleRabbit.id ||
            femaleRabbit.fatherId == maleRabbit.id || femaleRabbit.motherId == maleRabbit.id) {
            // Parent x Child mating has 25% consanguinity
            return 25.0
        }

        // Direct full sibling quick validation
        if (maleHasSire && femaleHasSire && maleRabbit.fatherId == femaleRabbit.fatherId &&
            maleHasDam && femaleHasDam && maleRabbit.motherId == femaleRabbit.motherId) {
            // Full brothers & sisters share both parents = 25% consanguinity
            return 25.0
        }

        // Collect all ancestors reachable from either male or female
        val relevantIds = mutableSetOf<Int>()
        fun collectAncestors(currentId: Int?, visited: MutableSet<Int>) {
            if (currentId == null || currentId in visited) return
            visited.add(currentId)
            val r = rabbitsMap[currentId] ?: return
            relevantIds.add(r.id)
            val sId = if (r.fatherId != null && r.fatherId != r.id && rabbitsMap.containsKey(r.fatherId)) r.fatherId else null
            val dId = if (r.motherId != null && r.motherId != r.id && rabbitsMap.containsKey(r.motherId)) r.motherId else null
            collectAncestors(sId, visited)
            collectAncestors(dId, visited)
        }

        val visitedMale = mutableSetOf<Int>()
        val visitedFemale = mutableSetOf<Int>()
        collectAncestors(maleId, visitedMale)
        collectAncestors(femaleId, visitedFemale)

        // If no common ancestor exists in the pedigree tree, return 0.0% immediately
        val sharedAncestors = visitedMale.intersect(visitedFemale)
        if (sharedAncestors.isEmpty()) {
            return 0.0
        }

        val relevantRabbits = relevantIds.mapNotNull { rabbitsMap[it] }
        if (relevantRabbits.isEmpty()) return 0.0

        // Compute generation depth/level for topological ordering
        // Founders have level 0; children have level = 1 + max(sireLevel, damLevel)
        val levelMap = mutableMapOf<Int, Int>()
        fun getLevel(id: Int, chain: MutableSet<Int>): Int {
            if (id in levelMap) return levelMap[id]!!
            if (id in chain) return 0 // Cycle safeguard
            chain.add(id)
            val r = rabbitsMap[id]
            val sId = if (r?.fatherId != null && r.fatherId != r.id && rabbitsMap.containsKey(r.fatherId)) r.fatherId else null
            val dId = if (r?.motherId != null && r.motherId != r.id && rabbitsMap.containsKey(r.motherId)) r.motherId else null

            val sLevel = if (sId != null) getLevel(sId, chain) else -1
            val dLevel = if (dId != null) getLevel(dId, chain) else -1
            val lvl = maxOf(sLevel, dLevel) + 1
            chain.remove(id)
            levelMap[id] = lvl
            return lvl
        }

        relevantRabbits.forEach { getLevel(it.id, mutableSetOf()) }

        // Sort by generation level ASC so all ancestors always precede their descendants
        val sorted = relevantRabbits.sortedBy { levelMap[it.id] ?: 0 }
        val n = sorted.size
        val idToIndex = sorted.mapIndexed { index, rabbit -> rabbit.id to index }.toMap()

        // Tabular method (Henderson's numerator relationship matrix A)
        val a = Array(n) { DoubleArray(n) { 0.0 } }

        for (i in 0 until n) {
            val r = sorted[i]
            val sId = if (r.fatherId != null && r.fatherId != r.id && rabbitsMap.containsKey(r.fatherId)) r.fatherId else null
            val dId = if (r.motherId != null && r.motherId != r.id && rabbitsMap.containsKey(r.motherId)) r.motherId else null

            val sireIdx = sId?.let { idToIndex[it] }
            val damIdx = dId?.let { idToIndex[it] }

            for (j in 0..i) {
                if (i == j) {
                    val parentalRel = if (sireIdx != null && damIdx != null) a[sireIdx][damIdx] else 0.0
                    a[i][i] = 1.0 + (parentalRel / 2.0)
                } else {
                    val sVal = if (sireIdx != null) a[sireIdx][j] else 0.0
                    val dVal = if (damIdx != null) a[damIdx][j] else 0.0
                    val rel = (sVal + dVal) / 2.0
                    a[i][j] = rel
                    a[j][i] = rel // Symmetric
                }
            }
        }

        val sireIdx = idToIndex[maleId]
        val damIdx = idToIndex[femaleId]

        if (sireIdx != null && damIdx != null) {
            val relationship = a[sireIdx][damIdx]
            val inbreedingPercent = (relationship / 2.0) * 100.0
            return String.format(java.util.Locale.US, "%.2f", inbreedingPercent).toDouble()
        }

        return 0.0
    }
}
