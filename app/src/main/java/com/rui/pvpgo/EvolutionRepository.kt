package com.rui.pvpgo

import com.rui.pvpgo.engine.PokemonSpecies

object EvolutionRepository {
    fun descendants(species: PokemonSpecies, all: List<PokemonSpecies>): List<PokemonSpecies> {
        val byId = all.associateBy { it.speciesId }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<PokemonSpecies>()

        fun walk(id: String) {
            val current = byId[id] ?: return
            current.evolutionSpeciesIds.forEach { childId ->
                if (seen.add(childId)) {
                    byId[childId]?.let(result::add)
                    walk(childId)
                }
            }
        }

        walk(species.speciesId)
        return result
    }
}
