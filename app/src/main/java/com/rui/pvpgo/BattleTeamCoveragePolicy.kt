package com.rui.pvpgo

import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.MatchupAdvisorResult
import com.rui.pvpgo.domain.MatchupAdvisorStatus
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.domain.TeamRole

/**
 * B04 is a VIEW of already certified, explicitly-configured 1v1 evaluations.
 * It is NOT a 3v3 simulator, team win probability, or a synthetic coverage score.
 * No rating may appear when the parent request was refused or is for another league.
 */
enum class B04MemberState { EVALUATED, EXCLUDED, UNAVAILABLE }

data class B04MemberView(
    val ownedPokemonId: String,
    val speciesId: String?,
    val role: TeamRole,
    val state: B04MemberState,
    val rating: Int? = null,
    val outcome: BattleOutcome? = null,
    val detail: String
)

data class B04TeamView(
    val teamId: String,
    val teamName: String,
    val members: List<B04MemberView>,
    val evaluated: Int,
    val winsInOneVsOne: Int,
    val unavailable: Int,
    val canDisplayEvaluations: Boolean,
    val explanation: String
)

object BattleTeamCoveragePolicy {
    fun project(
        team: SavedTeam,
        collection: List<OwnedPokemon>,
        analysis: MatchupAdvisorResult
    ): B04TeamView {
        val valid = analysis.status == MatchupAdvisorStatus.READY &&
            analysis.recommendations.isNotEmpty() && team.league == analysis.scenario.league
        val recs = if (valid) analysis.recommendations.associateBy { it.ownedPokemonId } else emptyMap()
        val exclusions = if (valid) analysis.exclusions.associateBy { it.ownedPokemonId } else emptyMap()
        val owned = collection.associateBy { it.id }
        val rows = team.members.map { member ->
            val specimen = owned[member.ownedPokemonId]
            val rec = if (specimen == null) null else recs[member.ownedPokemonId]
            val excluded = if (specimen == null) null else exclusions[member.ownedPokemonId]
            when {
                specimen == null -> B04MemberView(member.ownedPokemonId, null, member.role,
                    B04MemberState.UNAVAILABLE, detail = "Exemplar removido ou indisponível na coleção.")
                !valid -> B04MemberView(member.ownedPokemonId, specimen.speciesId, member.role,
                    B04MemberState.UNAVAILABLE, detail = "Análise 1v1 recusada, vazia ou noutra liga.")
                rec != null && rec.evaluation.scenario == analysis.scenario &&
                    rec.evaluation.ownedPokemonId == specimen.id ->
                    B04MemberView(member.ownedPokemonId, specimen.speciesId, member.role,
                        B04MemberState.EVALUATED, rating = rec.evaluation.battleRating,
                        outcome = rec.evaluation.outcome,
                        detail = rec.why)
                excluded != null -> B04MemberView(member.ownedPokemonId, specimen.speciesId, member.role,
                    B04MemberState.EXCLUDED, detail = excluded.detail)
                else -> B04MemberView(member.ownedPokemonId, specimen.speciesId, member.role,
                    B04MemberState.UNAVAILABLE,
                    detail = "Sem avaliação entre os 50 resultados devolvidos. Não inferir rating.")
            }
        }
        val evaluated = rows.count { it.state == B04MemberState.EVALUATED }
        return B04TeamView(
            teamId = team.id, teamName = team.name, members = rows,
            evaluated = evaluated,
            winsInOneVsOne = rows.count { it.state == B04MemberState.EVALUATED && it.outcome == BattleOutcome.WIN },
            unavailable = rows.size - evaluated,
            canDisplayEvaluations = valid,
            explanation = when {
                team.league != analysis.scenario.league -> "A equipa e o matchup pertencem a ligas diferentes."
                analysis.status == MatchupAdvisorStatus.REFUSED -> analysis.refusalReason ?: "Análise recusada."
                !valid -> "Não existem avaliações certificadas deste cenário."
                evaluated < 3 -> "Cobertura individual incompleta: consulta as exclusões por membro."
                else -> "Três avaliações individuais para o mesmo adversário e cenário. Não é batalha 3v3."
            }
        )
    }
}
