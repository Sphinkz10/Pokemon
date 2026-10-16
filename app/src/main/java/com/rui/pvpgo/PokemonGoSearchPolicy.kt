package com.rui.pvpgo

import com.rui.pvpgo.engine.League

/**
 * Queries copied into the *official Pokémon GO inventory search*.
 *
 * The game's 0–4 attribute buckets are ranges, NOT precise individual IVs.
 * Screening is optional and never used to delete, transfer, or rank owned
 * Pokémon in this app. Species, level, evolution, CP cap and move meta can
 * make excluded spreads competitive: no profile here is a safe trash filter.
 *
 * Source: https://niantic.helpshift.com/hc/en/6-pokemon-go/faq/1486-searching-filtering-your-pokemon-inventory/
 * Cross-check PT naming: official /hc/pt-br/6-pokemon-go/faq/1486
 */
internal enum class PokemonGoQueryLanguage(
    val title: String,
    val attack: String,
    val defense: String,
    val stamina: String
) {
    EN("EN · Attack/Defense/HP", "attack", "defense", "hp"),
    PT("PT · Ataque/Defesa/PS", "ataque", "defesa", "ps")
}

internal enum class PokemonGoSearchPreset(val label: String, val explanation: String) {
    ALL("Ver tudo", "Não restringe a avaliação de Ataque. Serve para começar sem excluir IVs."),
    BROAD("Explorar candidatos", "Pesquisa alargada e apenas exploratória: poderá omitir Pokémon PvP úteis."),
    LOW_ATTACK("Perfil clássico", "Ataque baixo e Defesa/PS altos. Não é um ranking por espécie."),
    HIGH_ATTACK("Ataque alto", "Útil para procurar alguns perfis Master e espécies com nível máximo limitado; não é universal."),
    PERFECT("IV 100%", "IV 15/15/15 exatos. Não assume que um hundo é sempre o melhor PvP.")
}

internal object PokemonGoSearchPolicy {
    fun suggestedFor(league: League): PokemonGoSearchPreset =
        if (league == League.MASTER) PokemonGoSearchPreset.HIGH_ATTACK
        else PokemonGoSearchPreset.BROAD

    fun build(
        preset: PokemonGoSearchPreset,
        language: PokemonGoQueryLanguage
    ): String {
        fun attribute(range: String, key: String) = "$range$key"
        val attack = language.attack
        val defense = language.defense
        val hp = language.stamina
        return when (preset) {
            PokemonGoSearchPreset.ALL -> attribute("0-4", attack)
            PokemonGoSearchPreset.BROAD -> listOf(
                attribute("0-2", attack), attribute("2-4", defense),
                attribute("2-4", hp)
            ).joinToString("&")
            PokemonGoSearchPreset.LOW_ATTACK -> listOf(
                attribute("0-1", attack), attribute("3-4", defense),
                attribute("3-4", hp)
            ).joinToString("&")
            PokemonGoSearchPreset.HIGH_ATTACK -> listOf(
                attribute("3-4", attack), attribute("3-4", defense),
                attribute("3-4", hp)
            ).joinToString("&")
            PokemonGoSearchPreset.PERFECT -> "4*"
        }
    }

    /** Explicitly forbid misleading/discard commands in all our templates. */
    fun isNonDestructive(query: String): Boolean =
        query.isNotBlank() &&
            "!" !in query &&
            "cp" !in query.lowercase() &&
            "#" !in query &&
            query.length <= 100
}
