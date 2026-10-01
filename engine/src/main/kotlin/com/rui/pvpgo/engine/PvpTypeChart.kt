package com.rui.pvpgo.engine

/** Pokémon GO effectiveness values, aligned with PvPoke's DamageCalculator constants. */
object PvpTypeChart {
    const val SUPER_EFFECTIVE = 1.600000023841858
    const val RESISTED = 0.625
    const val IMMUNITY_AS_DOUBLE_RESIST = 0.390625

    private data class Traits(
        val weaknesses: Set<String> = emptySet(),
        val resistances: Set<String> = emptySet(),
        val immunities: Set<String> = emptySet()
    )

    private val traits = mapOf(
        "normal" to Traits(weaknesses = setOf("fighting"), immunities = setOf("ghost")),
        "fighting" to Traits(resistances = setOf("rock", "bug", "dark"), weaknesses = setOf("flying", "psychic", "fairy")),
        "flying" to Traits(resistances = setOf("fighting", "bug", "grass"), weaknesses = setOf("rock", "electric", "ice"), immunities = setOf("ground")),
        "poison" to Traits(resistances = setOf("fighting", "poison", "bug", "fairy", "grass"), weaknesses = setOf("ground", "psychic")),
        "ground" to Traits(resistances = setOf("poison", "rock"), weaknesses = setOf("water", "grass", "ice"), immunities = setOf("electric")),
        "rock" to Traits(resistances = setOf("normal", "flying", "poison", "fire"), weaknesses = setOf("fighting", "ground", "steel", "water", "grass")),
        "bug" to Traits(resistances = setOf("fighting", "ground", "grass"), weaknesses = setOf("flying", "rock", "fire")),
        "ghost" to Traits(resistances = setOf("poison", "bug"), weaknesses = setOf("ghost", "dark"), immunities = setOf("normal", "fighting")),
        "steel" to Traits(resistances = setOf("normal", "flying", "rock", "bug", "steel", "grass", "psychic", "ice", "dragon", "fairy"), weaknesses = setOf("fighting", "ground", "fire"), immunities = setOf("poison")),
        "fire" to Traits(resistances = setOf("bug", "steel", "fire", "grass", "ice", "fairy"), weaknesses = setOf("ground", "rock", "water")),
        "water" to Traits(resistances = setOf("steel", "fire", "water", "ice"), weaknesses = setOf("grass", "electric")),
        "grass" to Traits(resistances = setOf("ground", "water", "grass", "electric"), weaknesses = setOf("flying", "poison", "bug", "fire", "ice")),
        "electric" to Traits(resistances = setOf("flying", "steel", "electric"), weaknesses = setOf("ground")),
        "psychic" to Traits(resistances = setOf("fighting", "psychic"), weaknesses = setOf("bug", "ghost", "dark")),
        "ice" to Traits(resistances = setOf("ice"), weaknesses = setOf("fighting", "fire", "steel", "rock")),
        "dragon" to Traits(resistances = setOf("fire", "water", "grass", "electric"), weaknesses = setOf("dragon", "ice", "fairy")),
        "dark" to Traits(resistances = setOf("ghost", "dark"), weaknesses = setOf("fighting", "fairy", "bug"), immunities = setOf("psychic")),
        "fairy" to Traits(resistances = setOf("fighting", "bug", "dark"), weaknesses = setOf("poison", "steel"), immunities = setOf("dragon"))
    )

    fun effectiveness(moveType: String, defenderTypes: List<String>): Double {
        val attack = moveType.lowercase()
        return defenderTypes.fold(1.0) { acc, rawDef ->
            val def = rawDef.lowercase()
            val t = traits[def] ?: return@fold acc
            acc * when {
                attack in t.weaknesses -> SUPER_EFFECTIVE
                attack in t.resistances -> RESISTED
                attack in t.immunities -> IMMUNITY_AS_DOUBLE_RESIST
                else -> 1.0
            }
        }
    }
}
