package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.OwnedEligibilityGate
import com.rui.pvpgo.engine.OwnedEligibilityStatus
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.StandardRulesets

internal enum class TargetSimulationStatus {
    NEEDS_DATA, INVALID_LEVEL, INVALID_CURRENT_DATA, BASE_LEAGUE_BLOCKED, READY_FOR_COSTING
}

internal data class TargetSimulation(
    val status: TargetSimulationStatus,
    val message: String,
    val targetLevel: Double? = null,
    val targetCp: Int? = null,
    val powerUpSteps: Int? = null
) {
    val canRequestCosting: Boolean get() = status == TargetSimulationStatus.READY_FOR_COSTING
}

/**
 * Read-only target-level simulator. It never assumes the user owns resources,
 * never estimates Stardust/Candy from level alone, never mutates collection
 * and never confirms eligibility for a special cup.
 */
internal object InvestmentTargetSimulator {
    const val SAFETY_NOTE =
        "Projeção matemática baseada nos IVs, espécie e nível registados. " +
            "Não verifica regras de copas especiais, movimentos, viabilidade da evolução, " +
            "força no meta nem Stardust/Candy necessários. Níveis acima de 50 " +
            "e bónus Best Buddy exigem análise separada."

    fun simulate(
        owned: OwnedPokemon,
        species: PokemonSpecies?,
        league: League,
        targetRaw: String
    ): TargetSimulation {
        if (targetRaw.isBlank()) return TargetSimulation(
            TargetSimulationStatus.NEEDS_DATA,
            "Escolhe um nível objetivo (entre 1 e 50, em passos de 0,5)."
        )
        val targetLevel = targetRaw.trim().replace(',', '.').toDoubleOrNull()
        if (targetLevel == null || !targetLevel.isFinite() ||
            targetLevel !in 1.0..50.0 || targetLevel * 2 != (targetLevel * 2).toInt().toDouble()
        ) return TargetSimulation(
            TargetSimulationStatus.INVALID_LEVEL,
            "Nível inválido. Usa um valor entre 1 e 50, em passos de 0,5. " +
                "Best Buddy não está incluído nesta simulação."
        )
        val currentLevel = owned.level
        val recordedCp = owned.cp
        if (species == null || species.speciesId != owned.speciesId ||
            currentLevel == null || recordedCp == null
        ) return TargetSimulation(
            TargetSimulationStatus.NEEDS_DATA,
            "Faltam espécie/forma, CP ou nível atual. Regista-os primeiro no Build real."
        )
        val currentCalculated = runCatching { PokemonMath.cp(species, owned.iv, currentLevel) }
            .getOrNull() ?: return TargetSimulation(
            TargetSimulationStatus.INVALID_CURRENT_DATA,
            "Não foi possível verificar o CP atual com estes dados."
        )
        if (currentCalculated != recordedCp) return TargetSimulation(
            TargetSimulationStatus.INVALID_CURRENT_DATA,
            "CP registado ($recordedCp) difere do calculado ($currentCalculated). " +
                "Corrige espécie, IVs, CP ou nível antes de simular."
        )
        if (targetLevel < currentLevel) return TargetSimulation(
            TargetSimulationStatus.INVALID_LEVEL,
            "O nível objetivo não pode ser inferior ao nível atual ($currentLevel)."
        )

        val cp = runCatching { PokemonMath.cp(species, owned.iv, targetLevel) }.getOrNull()
            ?: return TargetSimulation(TargetSimulationStatus.INVALID_LEVEL, "Nível não suportado pelo motor CP.")
        val steps = ((targetLevel - currentLevel) * 2.0).toInt()
        val eligibility = OwnedEligibilityGate.evaluate(
            StandardRulesets.forLeague(league), species, cp, targetLevel
        )
        if (eligibility.status != OwnedEligibilityStatus.CONFIRMED) return TargetSimulation(
            TargetSimulationStatus.BASE_LEAGUE_BLOCKED,
            "CP projetado $cp. Não cumpre os limites genéricos da liga ou faltam dados " +
                "para os confirmar: " +
                (eligibility.rulesetReasons.map { it.name } + eligibility.issues.map { it.name })
                    .joinToString(", ").ifBlank { "verificação incompleta" } +
                ". Isto não avalia copas especiais.",
            targetLevel, cp, steps
        )
        return TargetSimulation(
            TargetSimulationStatus.READY_FOR_COSTING,
            "CP projetado $cp no nível ${formatLevel(targetLevel)}. " +
                "$steps incrementos de meio nível. " +
                "Limites genéricos da liga verificados; custo e utilidade PvP continuam desconhecidos.",
            targetLevel, cp, steps
        )
    }

    private fun formatLevel(level: Double): String =
        if (level == level.toInt().toDouble()) level.toInt().toString()
        else level.toString().replace('.', ',')
}
