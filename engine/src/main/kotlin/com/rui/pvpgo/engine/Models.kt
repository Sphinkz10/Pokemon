package com.rui.pvpgo.engine

data class PokemonSpecies(
    val dex: Int,
    val name: String,
    val form: String = "Normal",
    val speciesId: String = "",
    val types: List<String> = emptyList(),
    val tags: Set<String> = emptySet(),
    val nicknames: List<String> = emptyList(),
    val searchPriority: Int = 0,
    val familyId: String? = null,
    val parentSpeciesId: String? = null,
    val evolutionSpeciesIds: List<String> = emptyList(),
    val fastMoveIds: List<String> = emptyList(),
    val chargedMoveIds: List<String> = emptyList(),
    val eliteMoveIds: Set<String> = emptySet(),
    val thirdMoveStardustCost: Int? = null,
    val released: Boolean = true,
    val baseAttack: Int,
    val baseDefense: Int,
    val baseStamina: Int
) {
    val isShadow: Boolean get() = "shadow" in tags || speciesId.endsWith("_shadow")
    val isMega: Boolean get() = "mega" in tags || "supermega" in tags || "_mega" in speciesId
}

data class IvSpread(val attack: Int, val defense: Int, val stamina: Int) {
    init {
        require(attack in 0..15 && defense in 0..15 && stamina in 0..15)
    }
    val total: Int get() = attack + defense + stamina
    override fun toString(): String = "$attack/$defense/$stamina"
}

enum class League(val cpCap: Int?) {
    LITTLE(500), GREAT(1500), ULTRA(2500), MASTER(null)
}

data class RankSettings(
    val minLevel: Double = 1.0,
    val maxLevel: Double = 50.0,
    val ivFloor: Int = 0
) {
    init {
        require(minLevel in 1.0..51.0 && maxLevel in 1.0..51.0 && minLevel <= maxLevel)
        require(ivFloor in 0..15)
        require(minLevel * 2 == (minLevel * 2).toInt().toDouble())
        require(maxLevel * 2 == (maxLevel * 2).toInt().toDouble())
    }

    val spreadCount: Int get() {
        val values = 16 - ivFloor
        return values * values * values
    }
}

data class BattleStats(
    val attack: Double,
    val defense: Double,
    val hp: Int
) {
    val statProduct: Double get() = attack * defense * hp
}

data class PvPRankEntry(
    val rank: Int,
    val mirrorRank: Int,
    val iv: IvSpread,
    val level: Double,
    val cp: Int,
    val stats: BattleStats,
    val percentOfRank1: Double
)
