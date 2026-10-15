package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvPRankEntry

/**
 * IV stat-product order of the user's OWNED specimens, not a team/meta rating,
 * current CP recommendation, special-cup eligibility or investment permission.
 * An IV rank can exist even when saved CP/level are missing or inconsistent.
 */
internal data class OwnedIvDecision(
    val owned: OwnedPokemon,
    val rank: PvPRankEntry?,
    val readiness: PvpGuideAssessment
)

internal object OwnedIvDecisionPolicy {
    fun sort(
        species: PokemonSpecies,
        league: League,
        owned: List<OwnedPokemon>,
        ranks: List<PvPRankEntry>
    ): List<OwnedIvDecision> {
        val byIv = ranks.associateBy { it.iv }
        return owned.asSequence()
            .filter { it.speciesId == species.speciesId }
            .map { item ->
                OwnedIvDecision(
                    owned = item,
                    rank = byIv[item.iv],
                    readiness = PvpGuidePolicy.assess(item, species, league)
                )
            }
            .sortedWith(
                compareBy<OwnedIvDecision> { it.rank?.rank ?: Int.MAX_VALUE }
                    .thenBy { it.owned.id }
            )
            .toList()
    }

    fun explanation(candidate: OwnedIvDecision): String = when {
        candidate.rank == null ->
            "Sem rank IV nesta configuração. Revê a espécie, forma e IVs."
        candidate.readiness.verdict == PvpGuideVerdict.NEEDS_REVIEW ->
            "Rank IV potencial, mas dados do exemplar por confirmar: " +
                candidate.readiness.reason
        candidate.readiness.verdict == PvpGuideVerdict.OVER_LIMIT ->
            "Rank IV potencial. CP atual acima do limite desta liga; não é possível reduzir CP."
        else ->
            "Rank de stat product, não é garantia de meta, movimentos ou custo pagável."
    }
}
