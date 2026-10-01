package com.rui.pvpgo

import com.rui.pvpgo.engine.PokemonSpecies
import java.text.Normalizer
import java.util.Locale

object PokemonSearch {
    fun find(all: List<PokemonSpecies>, query: String, limit: Int = 100): List<PokemonSpecies> {
        val q = normalize(query)
        if (q.isBlank()) return all.take(limit)
        val dexQuery = q.removePrefix("#").toIntOrNull()

        return all.asSequence()
            .mapNotNull { pokemon ->
                val name = normalize(pokemon.name)
                val id = normalize(pokemon.speciesId.replace('_', ' '))
                val nicknames = pokemon.nicknames.map(::normalize)
                val score = when {
                    dexQuery != null && pokemon.dex == dexQuery -> 0
                    name == q || id == q || nicknames.any { it == q } -> 0
                    name.startsWith(q) || id.startsWith(q) || nicknames.any { it.startsWith(q) } -> 1
                    name.contains(q) || id.contains(q) || nicknames.any { it.contains(q) } -> 2
                    else -> return@mapNotNull null
                }
                Triple(score, pokemon.searchPriority, pokemon)
            }
            .sortedWith(compareBy<Triple<Int, Int, PokemonSpecies>> { it.first }
                .thenByDescending { it.second }
                .thenBy { it.third.dex }
                .thenBy { it.third.name })
            .take(limit)
            .map { it.third }
            .toList()
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replace("[^a-z0-9# ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}
