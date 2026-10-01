package com.rui.pvpgo

import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvPRankEntry
import com.rui.pvpgo.engine.RankSettings

enum class KeepTier {
    MUST_KEEP,
    STRONG_KEEP,
    KEEP_IF_USEFUL,
    LOW_PRIORITY
}

data class PvPUse(
    val species: PokemonSpecies,
    val league: League,
    val rank: PvPRankEntry
)

data class KeepAdvice(
    val tier: KeepTier,
    val bestUse: PvPUse?,
    val uses: List<PvPUse>
)

/**
 * IV-only recommendation layer.
 *
 * This deliberately does NOT claim that a Pokemon is strong in the current meta.
 * It answers a narrower question: "how valuable is this exact IV spread among the
 * legal IV spreads of this species/evolution for a league?"
 */
object PvPAdvisor {
    private val leagues = listOf(League.GREAT, League.ULTRA, League.MASTER)

    fun evaluate(
        species: PokemonSpecies,
        catalog: List<PokemonSpecies>,
        iv: IvSpread,
        settings: RankSettings = RankSettings()
    ): KeepAdvice {
        val familyTargets = listOf(species) + EvolutionRepository.descendants(species, catalog)
        val uses = buildList {
            for (target in familyTargets.distinctBy { it.speciesId.ifBlank { "${it.dex}:${it.form}:${it.name}" } }) {
                for (league in leagues) {
                    RankRepository.find(target, league, iv, settings)?.let { add(PvPUse(target, league, it)) }
                }
            }
        }.sortedWith(
            compareBy<PvPUse> { it.rank.rank }
                .thenByDescending { it.rank.percentOfRank1 }
                .thenBy { it.species.dex }
                .thenBy { it.league.ordinal }
        )

        val best = uses.firstOrNull()
        val tier = when (best?.rank?.rank ?: Int.MAX_VALUE) {
            1 -> KeepTier.MUST_KEEP
            in 2..50 -> KeepTier.STRONG_KEEP
            in 51..250 -> KeepTier.KEEP_IF_USEFUL
            else -> KeepTier.LOW_PRIORITY
        }
        return KeepAdvice(tier, best, uses)
    }
}
