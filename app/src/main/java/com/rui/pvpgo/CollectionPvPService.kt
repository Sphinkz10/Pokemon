package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvPRankEntry
import com.rui.pvpgo.engine.RankSettings

data class OwnedRankEvaluation(
    val owned: OwnedPokemon,
    val targetSpecies: PokemonSpecies,
    val league: League,
    val rank: PvPRankEntry
)

data class CollectionUpgradeInsight(
    val candidate: OwnedRankEvaluation,
    val currentBest: OwnedRankEvaluation?,
    val isUpgrade: Boolean,
    val rankImprovement: Int?,
    val statProductImprovementPercent: Double?
)

/**
 * Collection-aware PvP layer.
 *
 * It compares a newly scanned Pokémon against what the user ALREADY owns, including
 * unevolved family members whose IVs can reach the same target evolution. It never
 * treats IV Rank as a meta-strength score.
 */
object CollectionPvPService {
    fun evaluateOwned(
        owned: OwnedPokemon,
        catalog: List<PokemonSpecies>,
        settings: RankSettings = RankSettings()
    ): KeepAdvice? {
        val species = catalog.firstOrNull { it.speciesId == owned.speciesId } ?: return null
        return PvPAdvisor.evaluate(species, catalog, owned.iv, settings)
    }

    fun upgradeInsights(
        candidate: OwnedPokemon,
        collection: List<OwnedPokemon>,
        catalog: List<PokemonSpecies>,
        settings: RankSettings = RankSettings()
    ): List<CollectionUpgradeInsight> {
        val candidateSpecies = catalog.firstOrNull { it.speciesId == candidate.speciesId } ?: return emptyList()
        val advice = PvPAdvisor.evaluate(candidateSpecies, catalog, candidate.iv, settings)

        return advice.uses.map { use ->
            val candidateEvaluation = OwnedRankEvaluation(candidate, use.species, use.league, use.rank)
            val currentBest = bestOwnedForTarget(
                collection = collection.filterNot { it.id == candidate.id },
                catalog = catalog,
                targetSpecies = use.species,
                league = use.league,
                settings = settings
            )
            val isUpgrade = currentBest == null || use.rank.rank < currentBest.rank.rank ||
                (use.rank.rank == currentBest.rank.rank && use.rank.stats.statProduct > currentBest.rank.stats.statProduct + 1e-9)
            val rankImprovement = currentBest?.let { it.rank.rank - use.rank.rank }
            val statProductImprovement = currentBest?.let {
                if (it.rank.stats.statProduct == 0.0) 0.0
                else ((use.rank.stats.statProduct / it.rank.stats.statProduct) - 1.0) * 100.0
            }
            CollectionUpgradeInsight(
                candidate = candidateEvaluation,
                currentBest = currentBest,
                isUpgrade = isUpgrade,
                rankImprovement = rankImprovement,
                statProductImprovementPercent = statProductImprovement
            )
        }.sortedWith(
            compareByDescending<CollectionUpgradeInsight> { it.isUpgrade }
                .thenBy { it.candidate.rank.rank }
                .thenByDescending { it.statProductImprovementPercent ?: Double.POSITIVE_INFINITY }
        )
    }

    fun bestOwnedForTarget(
        collection: List<OwnedPokemon>,
        catalog: List<PokemonSpecies>,
        targetSpecies: PokemonSpecies,
        league: League,
        settings: RankSettings = RankSettings()
    ): OwnedRankEvaluation? {
        val byId = catalog.associateBy { it.speciesId }
        return collection.mapNotNull { owned ->
            val currentSpecies = byId[owned.speciesId] ?: return@mapNotNull null
            if (!canReach(currentSpecies, targetSpecies.speciesId, catalog)) return@mapNotNull null
            val rank = RankRepository.find(targetSpecies, league, owned.iv, settings) ?: return@mapNotNull null
            OwnedRankEvaluation(owned, targetSpecies, league, rank)
        }.minWithOrNull(
            compareBy<OwnedRankEvaluation> { it.rank.rank }
                .thenByDescending { it.rank.stats.statProduct }
                .thenByDescending { it.rank.stats.attack }
        )
    }

    private fun canReach(
        source: PokemonSpecies,
        targetSpeciesId: String,
        catalog: List<PokemonSpecies>
    ): Boolean {
        if (source.speciesId == targetSpeciesId) return true
        return EvolutionRepository.descendants(source, catalog).any { it.speciesId == targetSpeciesId }
    }
}
