package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies

internal enum class LeagueCapTargetStatus {
    AVAILABLE,
    NEEDS_SAVED_DATA,
    INCONSISTENT_CP,
    ALREADY_OVER_CAP,
    INELIGIBLE_STANDARD_LEAGUE,
    LEVEL_OVER_SIMULATOR_LIMIT,
    COMPUTATION_FAILED
}

/**
 * A convenience target for the same owned Pokémon and its current form.
 * NEVER a PvP power/IV/meta recommendation: this is only the highest
 * mathematical half-level that still fits a generic league CP limit.
 */
internal data class LeagueCapTargetSuggestion(
    val status: LeagueCapTargetStatus,
    val explanation: String,
    val targetLevel: Double? = null,
    val targetCp: Int? = null,
    val halfLevelSteps: Int? = null
) {
    val canApply: Boolean get() =
        status == LeagueCapTargetStatus.AVAILABLE &&
            targetLevel != null && targetCp != null && halfLevelSteps != null
}

internal object LeagueCapTargetPolicy {
    const val DISCLAIMER =
        "Objetivo por limite de CP, não por força em PvP. Não valida copas especiais, " +
            "evoluções, movimentos, rank IV nem melhores equipas. Usa apenas a forma atual, " +
            "os IVs registados e níveis até 50 (sem Best Buddy)."

    fun displayLevel(value: Double): String =
        if (value == value.toInt().toDouble()) value.toInt().toString()
        else value.toString().replace('.', ',')

    fun suggest(
        owned: OwnedPokemon,
        species: PokemonSpecies?,
        league: League
    ): LeagueCapTargetSuggestion {
        val currentLevel = owned.level
        val recordedCp = owned.cp
        if (species == null || species.speciesId != owned.speciesId ||
            currentLevel == null || recordedCp == null
        ) return LeagueCapTargetSuggestion(
            LeagueCapTargetStatus.NEEDS_SAVED_DATA,
            "Faltam espécie, forma, CP ou nível registados no Build real."
        )
        if (currentLevel > 50.0) return LeagueCapTargetSuggestion(
            LeagueCapTargetStatus.LEVEL_OVER_SIMULATOR_LIMIT,
            "Esta ajuda suporta níveis até 50, sem bónus Best Buddy. Não propomos baixar níveis."
        )
        val derivedCp = runCatching {
            PokemonMath.cp(species, owned.iv, currentLevel)
        }.getOrNull() ?: return LeagueCapTargetSuggestion(
            LeagueCapTargetStatus.COMPUTATION_FAILED,
            "O motor não conseguiu confirmar o CP atual deste exemplar."
        )
        if (derivedCp != recordedCp) return LeagueCapTargetSuggestion(
            LeagueCapTargetStatus.INCONSISTENT_CP,
            "O CP guardado ($recordedCp) difere do calculado ($derivedCp). " +
                "Revê nível, forma e IVs antes de escolher um objetivo."
        )

        // An existing specimen already above the limit cannot be powered down.
        // This is a hard stop, regardless of whether a lower hypothetical level exists.
        val cap = league.cpCap
        if (cap != null && recordedCp > cap) return LeagueCapTargetSuggestion(
            LeagueCapTargetStatus.ALREADY_OVER_CAP,
            "Este exemplar já tem $recordedCp CP, acima dos $cap CP da liga. " +
                "Não é possível reduzir o nível com power-ups."
        )

        // Reuse the same fail-closed owned ruleset gate as manual targets.
        val current = InvestmentTargetSimulator.simulate(
            owned, species, league, displayLevel(currentLevel)
        )
        if (!current.canRequestCosting) return LeagueCapTargetSuggestion(
            LeagueCapTargetStatus.INELIGIBLE_STANDARD_LEAGUE,
            "Não é possível sugerir este objetivo: ${current.message}"
        )

        val maximum = runCatching {
            if (cap == null) 50.0
            else PokemonMath.highestLevelAtOrBelowCap(
                species = species,
                iv = owned.iv,
                cap = cap,
                minLevel = currentLevel,
                maxLevel = 50.0
            )
        }.getOrNull() ?: return LeagueCapTargetSuggestion(
            LeagueCapTargetStatus.COMPUTATION_FAILED,
            "Não foi encontrado um nível suportado a partir do nível atual."
        )

        // Deliberately run the final target through all the same validation
        // gates as typing a level manually. No independent eligibility shortcut.
        val final = InvestmentTargetSimulator.simulate(
            owned, species, league, displayLevel(maximum)
        )
        if (!final.canRequestCosting) return LeagueCapTargetSuggestion(
            LeagueCapTargetStatus.INELIGIBLE_STANDARD_LEAGUE,
            "Objetivo automático recusado pelas regras base da liga: ${final.message}"
        )
        val steps = requireNotNull(final.powerUpSteps)
        val cp = requireNotNull(final.targetCp)
        val limit = if (cap == null) "sem limite de CP" else "limite de $cap CP"
        return LeagueCapTargetSuggestion(
            status = LeagueCapTargetStatus.AVAILABLE,
            explanation = if (steps == 0)
                "Já atingiste o nível mais alto permitido nesta simulação " +
                    "($limit): nível ${displayLevel(maximum)}, $cp CP. Sem novos power-ups."
            else
                "Até nível ${displayLevel(maximum)} · $cp CP · " +
                    "$steps incrementos de meio nível ($limit). " +
                    "Não é uma recomendação de investimento.",
            targetLevel = maximum,
            targetCp = cp,
            halfLevelSteps = steps
        )
    }
}
