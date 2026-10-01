package com.rui.pvpgo.engine

/**
 * Immutable provenance for parity work. Never mix simulator rules from one upstream revision with
 * final-result fixtures captured from another without making the version boundary explicit.
 */
data class UpstreamReferencePin(
    val repository: String,
    val commitSha: String,
    val shortSha: String,
    val siteVersion: String,
    val checkedAt: String,
    val actionLogicUrl: String,
    val battleUrl: String,
    val pokemonUrl: String
)

object PvPokeReferencePin {
    const val REPOSITORY = "https://github.com/pvpoke/pvpoke"
    const val COMMIT_SHA = "a93147bf1f2e829758958bfb5b37e56bbadc9678"
    const val SHORT_SHA = "a93147b"
    const val SITE_VERSION = "1.40.1.3"
    const val CHECKED_AT = "2026-09-13"

    const val ACTION_LOGIC_URL = "https://raw.githubusercontent.com/pvpoke/pvpoke/$COMMIT_SHA/src/js/battle/actions/ActionLogic.js"
    const val BATTLE_URL = "https://raw.githubusercontent.com/pvpoke/pvpoke/$COMMIT_SHA/src/js/battle/Battle.js"
    const val POKEMON_URL = "https://raw.githubusercontent.com/pvpoke/pvpoke/$COMMIT_SHA/src/js/pokemon/Pokemon.js"

    /** Older indexed result page used only as a regression clue, never as proof of current parity. */
    const val HISTORICAL_RESULT_VERSION = "1.37.4.7"
    const val HISTORICAL_RESULT_URL = "https://pvpoke.com/battle/multi/1500/custom/azumarill/11/0-2-1/2-1/marsh"

    val current = UpstreamReferencePin(
        repository = REPOSITORY,
        commitSha = COMMIT_SHA,
        shortSha = SHORT_SHA,
        siteVersion = SITE_VERSION,
        checkedAt = CHECKED_AT,
        actionLogicUrl = ACTION_LOGIC_URL,
        battleUrl = BATTLE_URL,
        pokemonUrl = POKEMON_URL
    )
}
