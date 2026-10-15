package com.rui.pvpgo

import com.rui.pvpgo.domain.InvestmentCostPack
import com.rui.pvpgo.domain.InvestmentPlan
import com.rui.pvpgo.domain.InvestmentPlanStatus
import com.rui.pvpgo.domain.InvestmentRequirement
import com.rui.pvpgo.domain.InvestmentTarget
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.ResourceLedger
import com.rui.pvpgo.engine.InvestmentCostPackValidator
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies

internal enum class AffordableLevelStatus {
    CONSENT_REQUIRED,
    NO_ELIGIBLE_TARGET,
    UNSUPPORTED_COST_MODIFIER,
    UNVERIFIED_REFERENCE,
    UNKNOWN_BALANCES,
    INCOMPLETE_QUOTE,
    AVAILABLE
}

/**
 * The largest CP-legal current-form half-level the player's *reported*
 * resources can cover, for normal power-ups only. This never initiates an
 * investment, alters actual specimen data or ranks a Pokémon against the meta.
 */
internal data class AffordableLevelResult(
    val status: AffordableLevelStatus,
    val explanation: String,
    val goalLevel: Double? = null,
    val affordableLevel: Double? = null,
    val affordableCp: Int? = null,
    val affordableSteps: Int? = null,
    val affordablePlan: InvestmentPlan? = null,
    val goalShortages: List<InvestmentRequirement> = emptyList(),
    val nextStepShortages: List<InvestmentRequirement> = emptyList()
) {
    val canApply: Boolean get() =
        status == AffordableLevelStatus.AVAILABLE &&
            affordableLevel != null && affordableCp != null && affordableSteps != null &&
            affordablePlan?.status == InvestmentPlanStatus.READY_NOW &&
            affordablePlan?.exactCostKnown == true
}

/**
 * Uses InvestmentCostQuoteGate for *every queried point*, never calculates
 * prices from CP differences. Binary search makes at most O(log N) calls to
 * the versioned InvestmentPlanner instead of rebuilding 99 full budgets.
 */
internal object AffordableLevelPolicy {
    const val LIMITATION =
        "Este é o nível que os saldos introduzidos podem suportar para power-ups " +
            "com a tabela comunitária datada de 15/10/2026. Não inclui evoluções, " +
            "movimentos, segundo ataque, modificadores especiais, qualidade PvP " +
            "ou condições de copas e eventos. Não gasta nem reserva recursos."

    fun advise(
        owned: OwnedPokemon,
        species: PokemonSpecies?,
        league: League,
        consented: Boolean,
        pack: InvestmentCostPack?,
        ledger: ResourceLedger?,
        suggestion: LeagueCapTargetSuggestion = LeagueCapTargetPolicy.suggest(owned, species, league)
    ): AffordableLevelResult {
        if (!suggestion.canApply) return AffordableLevelResult(
            AffordableLevelStatus.NO_ELIGIBLE_TARGET,
            "Sem objetivo válido de CP para esta liga: ${suggestion.explanation}"
        )
        if (!consented) return AffordableLevelResult(
            AffordableLevelStatus.CONSENT_REQUIRED,
            "Ativa a utilização da tabela comunitária para consultar um nível possível."
        )
        if (species == null || species.speciesId != owned.speciesId ||
            !CommunityNormalPowerUpPack.isSupportedNormalPokemon(species.speciesId, species.form) ||
            species.isMega || owned.isLucky || owned.isShadow || owned.isPurified
        ) return AffordableLevelResult(
            AffordableLevelStatus.UNSUPPORTED_COST_MODIFIER,
            "Não há custos normais verificados para esta forma ou condição especial."
        )
        if (pack == null || !CommunityNormalPowerUpPack.authenticatedReference() ||
            pack.id != CommunityNormalPowerUpPack.ID ||
            pack.version != CommunityNormalPowerUpPack.VERSION ||
            pack.sourceName != CommunityNormalPowerUpPack.SOURCE_NAME ||
            pack.sourceVersion != CommunityNormalPowerUpPack.SOURCE_VERSION ||
            pack.generatedAtEpochMs != CommunityNormalPowerUpPack.REVIEWED_EPOCH_MS ||
            pack.contentHashSha256 != CommunityNormalPowerUpPack.PINNED_HASH_SHA256 ||
            InvestmentCostPackValidator.validate(pack).isNotEmpty()
        ) return AffordableLevelResult(
            AffordableLevelStatus.UNVERIFIED_REFERENCE,
            "Sem uma tabela comunitária válida, datada e com integridade confirmada."
        )

        // ResourceLedger's domain defaults are numeric zero; requiring actual
        // family keys prevents a missing species balance being treated as 0.
        val familyId = species.familyId?.takeIf(String::isNotBlank) ?: species.speciesId
        if (ledger == null ||
            !ledger.candyByFamily.containsKey(familyId) ||
            !ledger.xlCandyByFamily.containsKey(familyId)
        ) return AffordableLevelResult(
            AffordableLevelStatus.UNKNOWN_BALANCES,
            "Regista Stardust, Candy, XL Candy e TMs. Um valor em branco é desconhecido, não zero."
        )
        val current = owned.level ?: return AffordableLevelResult(
            AffordableLevelStatus.NO_ELIGIBLE_TARGET, "Nível atual por confirmar."
        )
        val maximum = suggestion.targetLevel ?: return AffordableLevelResult(
            AffordableLevelStatus.NO_ELIGIBLE_TARGET, "Objetivo máximo por confirmar."
        )
        val count = ((maximum - current) * 2).toInt()
        if (count < 0 || count > 98) return AffordableLevelResult(
            AffordableLevelStatus.NO_ELIGIBLE_TARGET, "O intervalo de níveis não é suportado."
        )

        fun quote(step: Int): CostQuoteGateResult {
            val level = current + step / 2.0
            val preview = InvestmentTargetSimulator.simulate(
                owned, species, league, LeagueCapTargetPolicy.displayLevel(level)
            )
            return InvestmentCostQuoteGate.evaluate(
                owned = owned, simulation = preview, catalog = listOf(species),
                movesById = emptyMap(), pack = pack, ledger = ledger,
                target = InvestmentTarget(
                    ownedPokemonId = owned.id, league = league,
                    targetSpeciesId = owned.speciesId, targetLevel = level
                )
            )
        }

        fun trustworthy(result: CostQuoteGateResult): Boolean =
            result.canDisplayExactAmounts &&
                result.plan?.status in setOf(
                    InvestmentPlanStatus.READY_NOW, InvestmentPlanStatus.MISSING_RESOURCES
                )
        // Check that the target can be priced completely BEFORE a monotonic
        // resource-budget search. Partial cost information is not affordability.
        val full = quote(count)
        if (!trustworthy(full)) return AffordableLevelResult(
            AffordableLevelStatus.INCOMPLETE_QUOTE,
            "O motor não dispõe de custos completos para o objetivo. Não estimamos o nível acessível."
        )
        val fullPlan = requireNotNull(full.plan)
        var low = 0
        var high = count
        var chosen = if (fullPlan.status == InvestmentPlanStatus.READY_NOW) full else quote(0)
        if (!trustworthy(chosen) || chosen.plan?.status != InvestmentPlanStatus.READY_NOW) {
            return AffordableLevelResult(
                AffordableLevelStatus.INCOMPLETE_QUOTE,
                "Não foi possível validar nem sequer o custo do nível atual."
            )
        }
        if (fullPlan.status == InvestmentPlanStatus.READY_NOW) {
            low = count
        } else {
            // Power-up price is non-negative by model construction, so if step
            // n is affordable, all smaller steps are affordable too.
            while (low < high) {
                val mid = (low + high + 1) / 2
                val attempt = quote(mid)
                if (!trustworthy(attempt)) return AffordableLevelResult(
                    AffordableLevelStatus.INCOMPLETE_QUOTE,
                    "Custo incompleto num incremento intermédio; nenhuma sugestão aproximada."
                )
                if (attempt.plan?.status == InvestmentPlanStatus.READY_NOW) {
                    low = mid
                    chosen = attempt
                } else {
                    high = mid - 1
                }
            }
            if (chosen.plan?.target?.targetLevel != current + low / 2.0) {
                chosen = quote(low)
            }
        }
        if (!trustworthy(chosen) ||
            chosen.plan?.status != InvestmentPlanStatus.READY_NOW
        ) return AffordableLevelResult(
            AffordableLevelStatus.INCOMPLETE_QUOTE,
            "O nível encontrado não passou a validação final do motor."
        )

        val finalPlan = requireNotNull(chosen.plan)
        val level = current + low / 2.0
        val preview = InvestmentTargetSimulator.simulate(
            owned, species, league, LeagueCapTargetPolicy.displayLevel(level)
        )
        if (!preview.canRequestCosting || preview.targetCp == null) return AffordableLevelResult(
            AffordableLevelStatus.INCOMPLETE_QUOTE,
            "O CP do nível acessível não pôde ser confirmado."
        )
        val next = if (low < count) quote(low + 1) else null
        if (next != null && !trustworthy(next)) return AffordableLevelResult(
            AffordableLevelStatus.INCOMPLETE_QUOTE,
            "Não foi possível validar o custo do próximo incremento."
        )
        val targetText = LeagueCapTargetPolicy.displayLevel(maximum)
        val levelText = LeagueCapTargetPolicy.displayLevel(level)
        return AffordableLevelResult(
            status = AffordableLevelStatus.AVAILABLE,
            explanation = when {
                count == 0 -> "O exemplar já está no nível máximo desta simulação ($levelText)."
                low == count -> "Os saldos registados cobrem o nível $targetText nesta liga."
                low == 0 -> "Não há recursos registados suficientes para outro power-up; " +
                    "mantém-se nível $levelText."
                else -> "Os saldos registados permitem subir até nível $levelText " +
                    "(${preview.targetCp} CP), antes de faltar algum recurso."
            },
            goalLevel = maximum,
            affordableLevel = level,
            affordableCp = preview.targetCp,
            affordableSteps = low,
            affordablePlan = finalPlan,
            goalShortages = fullPlan.missingResources,
            nextStepShortages = next?.plan?.missingResources.orEmpty()
        )
    }
}
