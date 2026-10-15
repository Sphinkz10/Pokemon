package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.RankSettings

/**
 * Evidence about future IV spreads in catalog-linked evolutions only.
 * No claim that an evolution is executable in Pokémon GO, battle legal,
 * meta-relevant or affordable. A rank in one evolution is never compared
 * numerically with a rank from another evolution.
 */
internal data class CandidateEvolutionIvRow(
    val targetName: String,
    val targetSpeciesId: String,
    val candidateRank: Int?,
    val comparableOwnedCount: Int,
    val bestOwnedRank: Int?
)

internal data class CandidateEvolutionIvOverview(
    val rows: List<CandidateEvolutionIvRow>,
    val totalCatalogTargets: Int
) {
    val omittedCount: Int get() = (totalCatalogTargets - rows.size).coerceAtLeast(0)
}

internal object CandidateEvolutionAdvisor {
    const val MAX_TARGETS_PER_PREVIEW = 8

    /**
     * Explicit outgoing catalog links only, excluding unreleased or Mega forms
     * from this quick preview. The catalog does not validate candies, items,
     * time-limited rules, forms, CP cap or investment.
     */
    fun targets(source: PokemonSpecies, catalog: List<PokemonSpecies>): List<PokemonSpecies> =
        EvolutionRepository.descendants(source, catalog)
            .asSequence()
            .filter { it.speciesId.isNotBlank() && it.speciesId != source.speciesId }
            .filter { it.released && !it.isMega }
            .distinctBy { it.speciesId }
            .sortedWith(compareBy<PokemonSpecies> { it.dex }.thenBy { it.speciesId })
            .toList()

    fun evaluate(
        source: PokemonSpecies,
        candidate: OwnedPokemon,
        collection: List<OwnedPokemon>,
        catalog: List<PokemonSpecies>,
        league: League,
        settings: RankSettings = RankSettings()
    ): CandidateEvolutionIvOverview {
        require(candidate.speciesId == source.speciesId) {
            "Candidate and source must be the same catalog species/form"
        }
        val targets = targets(source, catalog)
        if (targets.isEmpty()) return CandidateEvolutionIvOverview(emptyList(), 0)

        val byId = catalog.associateBy { it.speciesId }
        val paths = mutableMapOf<String, Set<String>>()

        fun reachableIds(ownedSpeciesId: String): Set<String> =
            paths.getOrPut(ownedSpeciesId) {
                val ownedSpecies = byId[ownedSpeciesId] ?: return@getOrPut emptySet()
                (listOf(ownedSpecies.speciesId) +
                    EvolutionRepository.descendants(ownedSpecies, catalog).map { it.speciesId })
                    .toSet()
            }

        val rows = targets.take(MAX_TARGETS_PER_PREVIEW).map { target ->
            // Compare *the same target form* for both candidate and saved specimens.
            val similar = collection.filter {
                it.id != candidate.id && target.speciesId in reachableIds(it.speciesId)
            }
            val candidateRank = RankRepository.find(target, league, candidate.iv, settings)?.rank
            val bestRank = similar.mapNotNull { saved ->
                RankRepository.find(target, league, saved.iv, settings)?.rank
            }.minOrNull()

            CandidateEvolutionIvRow(
                targetName = target.name,
                targetSpeciesId = target.speciesId,
                candidateRank = candidateRank,
                comparableOwnedCount = similar.size,
                bestOwnedRank = bestRank
            )
        }
        return CandidateEvolutionIvOverview(rows, targets.size)
    }
}
