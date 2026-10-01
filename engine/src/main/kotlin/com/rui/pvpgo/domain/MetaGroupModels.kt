package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.League

/** One concrete, reproducible opponent build inside a versioned meta group. */
data class MetaBuildEntry(
    val key: String,
    val opponent: OpponentBuild,
    /** Relative prevalence/importance inside this meta snapshot. It is not a win probability. */
    val weight: Double = 1.0,
    val labels: Set<String> = emptySet()
) {
    init {
        require(key.isNotBlank())
        require(weight > 0.0 && weight.isFinite())
        require(opponent.fastMoveId != null) { "Meta build must declare a Fast Move" }
        require(opponent.chargedMoveIds.isNotEmpty()) { "Meta build must declare at least one Charged Move" }
        require(opponent.iv != null) { "Meta build must declare IVs" }
        require(opponent.level != null) { "Meta build must declare level" }
    }
}

/**
 * F50 contract. A meta is immutable/versioned input, never an invisible live assumption.
 * F65–F68 will later own ruleset/data-pack download and rollback around these snapshots.
 */
data class VersionedMetaGroup(
    val id: String,
    val league: League,
    val seasonId: String,
    val cupId: String? = null,
    val sourceName: String,
    val sourceVersion: String,
    val generatedAtEpochMs: Long,
    val entries: List<MetaBuildEntry>,
    /** F67 provenance links; optional only for legacy/test groups created before Meta Pack support. */
    val rulesetId: String? = null,
    val rulesetVersion: String? = null,
    val dataPackHashSha256: String? = null
) {
    init {
        require(id.isNotBlank())
        require(seasonId.isNotBlank())
        require(sourceName.isNotBlank())
        require(sourceVersion.isNotBlank())
        require(generatedAtEpochMs >= 0)
        require(entries.isNotEmpty())
        require(entries.map { it.key }.distinct().size == entries.size) { "Meta entry keys must be unique" }
        require((rulesetId == null) == (rulesetVersion == null)) { "rulesetId/rulesetVersion must be both present or both absent" }
        require(dataPackHashSha256 == null || dataPackHashSha256.matches(Regex("[0-9a-f]{64}")))
    }

    val totalWeight: Double get() = entries.sumOf { it.weight }
}

data class KeyMatchup(
    val metaEntry: MetaBuildEntry,
    val evaluation: MatchupEvaluation
)

data class UnresolvedMetaMatchup(
    val metaEntryKey: String,
    val opponentSpeciesId: String,
    val reason: String
)

data class KeyWinsLossesReport(
    val ownedPokemonId: String,
    val metaGroupId: String,
    val scenario: MatchupScenario,
    val certificationRoute: String?,
    val evaluatedCount: Int,
    val totalCount: Int,
    val evaluatedMetaWeight: Double,
    val totalMetaWeight: Double,
    val keyWins: List<KeyMatchup>,
    val keyLosses: List<KeyMatchup>,
    val closeMatchups: List<KeyMatchup>,
    val unresolved: List<UnresolvedMetaMatchup>
) {
    init {
        require(evaluatedCount in 0..totalCount)
        require(evaluatedMetaWeight >= 0.0)
        require(totalMetaWeight > 0.0)
    }

    /** Coverage of the meta snapshot that was actually simulated; never a battle win probability. */
    val evaluatedMetaCoveragePercent: Double
        get() = (evaluatedMetaWeight / totalMetaWeight * 100.0).coerceIn(0.0, 100.0)
}
