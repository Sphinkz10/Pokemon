package com.rui.pvpgo

import com.rui.pvpgo.domain.InvestmentCostPack
import com.rui.pvpgo.domain.InvestmentPlan
import com.rui.pvpgo.domain.InvestmentPlanStatus
import com.rui.pvpgo.domain.InvestmentTarget
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.ResourceLedger
import com.rui.pvpgo.engine.InvestmentCostPackValidator
import com.rui.pvpgo.engine.InvestmentPlanner
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvpMove

internal enum class CostQuoteGateStatus {
    BLOCKED_TARGET, MISSING_COST_PACK, INVALID_COST_PACK, MISSING_INVENTORY, UNVERIFIED_COST_MODIFIER,
    INCOMPLETE_COST, PLAN_INVALID, VERIFIED_COST
}

internal data class CostQuoteGateResult(
    val status: CostQuoteGateStatus,
    val explanation: String,
    val plan: InvestmentPlan? = null
) {
    val canDisplayExactAmounts: Boolean get() =
        status == CostQuoteGateStatus.VERIFIED_COST &&
            plan != null && plan.exactCostKnown &&
            plan.status != InvestmentPlanStatus.INVALID
}

/**
 * Connects the real, existing InvestmentPlanner behind an explicit trust gate.
 * The UI currently has no installed cost pack or resource ledger, therefore
 * it MUST NOT pass ResourceLedger() to fabricate a zero inventory or construct
 * a fake price table. Callers can supply validated inputs in a later phase.
 */
internal object InvestmentCostQuoteGate {
    fun evaluate(
        owned: OwnedPokemon,
        simulation: TargetSimulation,
        catalog: List<PokemonSpecies>,
        movesById: Map<String, PvpMove>,
        pack: InvestmentCostPack?,
        ledger: ResourceLedger?,
        target: InvestmentTarget?
    ): CostQuoteGateResult {
        if (!simulation.canRequestCosting || target == null ||
            target.ownedPokemonId != owned.id ||
            target.targetSpeciesId != owned.speciesId ||
            target.targetLevel != simulation.targetLevel
        ) return CostQuoteGateResult(
            CostQuoteGateStatus.BLOCKED_TARGET,
            "Define um nível objetivo válido e confirma os dados antes de calcular custos."
        )
        if (pack == null) return CostQuoteGateResult(
            CostQuoteGateStatus.MISSING_COST_PACK,
            "Sem pack de custos versionado e verificado. Stardust, Candy, XL e TMs ficam desconhecidos."
        )
        if (InvestmentCostPackValidator.validate(pack).isNotEmpty()) return CostQuoteGateResult(
            CostQuoteGateStatus.INVALID_COST_PACK,
            "Pack de custos rejeitado por falha de integridade; orçamento não disponível."
        )
        if (ledger == null) return CostQuoteGateResult(
            CostQuoteGateStatus.MISSING_INVENTORY,
            "O inventário de recursos é desconhecido; nunca o tratamos como zero."
        )
        // Current InvestmentPlanner consumes generic step prices, without a
        // proven Lucky discount or Shadow/Purified cost modifiers. A signed
        // hash alone cannot certify prices for these individual states.
        if (owned.isLucky || owned.isShadow || owned.isPurified) return CostQuoteGateResult(
            CostQuoteGateStatus.UNVERIFIED_COST_MODIFIER,
            "Este exemplar é Lucky, Shadow ou Purified. Os modificadores de custos " +
                "não estão certificados neste planeador; não mostramos um total exato."
        )
        val plan = runCatching {
            InvestmentPlanner.plan(owned, target, catalog, movesById, pack, ledger)
        }.getOrNull() ?: return CostQuoteGateResult(
            CostQuoteGateStatus.PLAN_INVALID,
            "O motor de investimento não conseguiu validar este objetivo."
        )
        if (plan.status == InvestmentPlanStatus.INVALID) return CostQuoteGateResult(
            CostQuoteGateStatus.PLAN_INVALID,
            "Objetivo rejeitado pelo motor de investimento; não mostrar custos.", plan
        )
        if (!plan.exactCostKnown) return CostQuoteGateResult(
            CostQuoteGateStatus.INCOMPLETE_COST,
            "O pack não cobre todos os passos ou movimentos do build; custo total desconhecido.", plan
        )
        return CostQuoteGateResult(
            CostQuoteGateStatus.VERIFIED_COST,
            "Custo calculado com pack ${pack.id} versão ${pack.version}. " +
                "Disponibilidade de recursos: ${plan.status.name}. " +
                "Não confirma força no meta ou regras especiais.",
            plan
        )
    }
}
