package com.rui.pvpgo.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

object PokemonMath {
    fun cp(species: PokemonSpecies, iv: IvSpread, level: Double): Int {
        val cpm = CpMultipliers.at(level)
        val raw = (species.baseAttack + iv.attack) *
            sqrt((species.baseDefense + iv.defense).toDouble()) *
            sqrt((species.baseStamina + iv.stamina).toDouble()) *
            cpm * cpm / 10.0
        return max(10, floor(raw).toInt())
    }

    fun battleStats(species: PokemonSpecies, iv: IvSpread, level: Double): BattleStats {
        val cpm = CpMultipliers.at(level)
        return BattleStats(
            attack = (species.baseAttack + iv.attack) * cpm,
            defense = (species.baseDefense + iv.defense) * cpm,
            hp = max(10, floor((species.baseStamina + iv.stamina) * cpm).toInt())
        )
    }

    /** CP is monotonic with level, so binary search avoids checking every half-level. */
    fun highestLevelAtOrBelowCap(
        species: PokemonSpecies,
        iv: IvSpread,
        cap: Int,
        minLevel: Double = 1.0,
        maxLevel: Double = 50.0
    ): Double? {
        val levels = CpMultipliers.supportedLevels
        var low = levels.indexOf(minLevel)
        var high = levels.indexOf(maxLevel)
        require(low >= 0 && high >= 0 && low <= high)

        if (cp(species, iv, levels[low]) > cap) return null
        var best = low
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cp(species, iv, levels[mid]) <= cap) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return levels[best]
    }
}
