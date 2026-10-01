package com.rui.pvpgo

/** Pure, defensive projection of the already certified TeamLab output. No fresh matchup calculations. */
object TeamsGoldenResultPolicy {
    fun validIds(ids: List<String>): Boolean = ids.size == 3 && ids.distinct().size == 3 && ids.none { it.isBlank() }

    fun isVerified(primary: List<String>, alternatives: List<List<String>>, ids: List<String>): Boolean =
        validIds(ids) && (ids == primary || alternatives.any { it == ids })

    /** Only a single change can inherit the unchanged duo's actual roles without inventing a new role evaluation. */
    fun singleSwap(primary: List<String>, candidate: List<String>): Boolean =
        validIds(primary) && validIds(candidate) && primary.toSet().intersect(candidate.toSet()).size == 2

    fun rolesForVerifiedSingleSwap(
        primary: List<String>, alternatives: List<List<String>>, selected: List<String>, roles: Map<String, String>
    ): Map<String, String>? {
        if (!isVerified(primary, alternatives, selected) || roles.keys != primary.toSet() || roles.values.toSet().size != 3) return null
        if (selected == primary) return roles
        if (!singleSwap(primary, selected)) return null
        val removed = primary.single { it !in selected }
        val inserted = selected.single { it !in primary }
        return (roles - removed) + (inserted to roles.getValue(removed))
    }

    /** Ranking is by the engine-provided coverage score, not projected win chance. */
    fun highestCoverage(primaryIds: List<String>, primaryCoverage: Double,
                        alternatives: List<Pair<List<String>, Double>>): List<String> {
        if (!validIds(primaryIds)) return emptyList()
        val eligible = alternatives.filter { validIds(it.first) && it.second.isFinite() && it.second in 0.0..100.0 }
        return (eligible + (primaryIds to primaryCoverage)).maxByOrNull { it.second }?.first ?: primaryIds
    }
}
