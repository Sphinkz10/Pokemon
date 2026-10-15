package com.rui.pvpgo

import com.rui.pvpgo.domain.BuildStatus
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.engine.PokemonSpecies

/** Next practical task, never a recommendation to spend or a PvP meta ranking. */
internal enum class SavedPlanNextStep {
    REVIEW_PLAN, REVIEW_SPECIES, VERIFY_SPECIMEN, CORRECT_LEAGUE,
    REGISTER_MOVES, REGISTER_RESOURCES, SET_INVESTMENT_TARGET
}

internal data class SavedPlanNextAction(
    val step: SavedPlanNextStep,
    val title: String,
    val explanation: String
)

internal object SavedPlanNextActionPolicy {
    const val CAVEAT =
        "Plano guardado não significa investimento aprovado: custos, meta e copas especiais " +
            "exigem verificação própria. A app não altera o Pokémon GO."

    fun next(
        owned: OwnedPokemon,
        species: PokemonSpecies?,
        plan: PvpBuildPlan,
        balances: UserResourceBalances
    ): SavedPlanNextAction {
        if (plan.ownedPokemonId != owned.id || plan.status == BuildStatus.ARCHIVED) {
            return SavedPlanNextAction(
                SavedPlanNextStep.REVIEW_PLAN, "Rever estado do plano",
                "O plano está arquivado ou não corresponde a este exemplar. Confirma a intenção no Pokémon 360."
            )
        }
        if (species == null || species.speciesId != owned.speciesId) {
            return SavedPlanNextAction(
                SavedPlanNextStep.REVIEW_SPECIES, "Confirmar espécie e forma",
                "Faltam dados válidos da espécie/forma no catálogo. Não é possível validar esta liga."
            )
        }
        if (owned.cp == null || owned.level == null || owned.uncertainFields.isNotEmpty()) {
            return SavedPlanNextAction(
                SavedPlanNextStep.VERIFY_SPECIMEN, "Confirmar CP, nível e dados reais",
                "Confirma no Pokémon GO os campos desconhecidos ou por rever. " +
                    "Não substituímos campos vazios por valores calculados."
            )
        }
        val readiness = InvestmentReadinessPolicy.inspect(owned, species, plan.league)
        if (readiness.summary == ReadinessSummary.NEEDS_CORRECTION) {
            return SavedPlanNextAction(
                SavedPlanNextStep.CORRECT_LEAGUE, "Rever CP, nível ou limite da liga",
                "O CP/nível é incoerente ou o exemplar falha os limites genéricos de " +
                    SpeciesLeaguePolicy.shortLabel(plan.league) +
                    ". Não verifica regras de copas especiais."
            )
        }
        if (owned.fastMoveId.isNullOrBlank() || owned.chargedMoveIds.isEmpty()) {
            return SavedPlanNextAction(
                SavedPlanNextStep.REGISTER_MOVES, "Confirmar ataques reais",
                "Regista Fast Move e pelo menos um Charged Move no Pokémon 360. " +
                    "O plano pretendido não altera ataques reais."
            )
        }
        if (balances.stardust == null || balances.candy == null || balances.xlCandy == null) {
            val missing = buildList {
                if (balances.stardust == null) add("Stardust")
                if (balances.candy == null) add("Candy")
                if (balances.xlCandy == null) add("XL")
            }.joinToString(", ")
            return SavedPlanNextAction(
                SavedPlanNextStep.REGISTER_RESOURCES, "Registar recursos em falta",
                "Falta confirmar: " + missing + ". Saldo desconhecido não é zero. " +
                    "Estes três recursos não bastam para orçamentar evoluções, ataques ou TMs."
            )
        }
        return SavedPlanNextAction(
            SavedPlanNextStep.SET_INVESTMENT_TARGET,
            "Definir nível alvo e validar custos",
            "CP/nível e saldos básicos estão registados; não prova que sejam atuais nem suficientes. " +
                "Abre o Pokémon 360, escolhe o alvo e verifica os custos antes de investir."
        )
    }
}
