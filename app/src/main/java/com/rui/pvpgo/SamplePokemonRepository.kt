package com.rui.pvpgo

import com.rui.pvpgo.engine.PokemonSpecies

/**
 * Tiny emergency dataset used only when a fresh install has neither network nor cache.
 * Keep canonical speciesId values here because search, favorites, evolutions and Radar
 * all key off that identifier.
 */
object SamplePokemonRepository {
    val all = listOf(
        PokemonSpecies(
            dex = 6,
            name = "Charizard",
            speciesId = "charizard",
            types = listOf("fire", "flying"),
            familyId = "charmander",
            parentSpeciesId = "charmeleon",
            baseAttack = 223,
            baseDefense = 173,
            baseStamina = 186
        ),
        PokemonSpecies(
            dex = 184,
            name = "Azumarill",
            speciesId = "azumarill",
            types = listOf("water", "fairy"),
            familyId = "marill",
            parentSpeciesId = "marill",
            baseAttack = 112,
            baseDefense = 152,
            baseStamina = 225
        ),
        PokemonSpecies(
            dex = 260,
            name = "Swampert",
            speciesId = "swampert",
            types = listOf("water", "ground"),
            familyId = "mudkip",
            parentSpeciesId = "marshtomp",
            baseAttack = 208,
            baseDefense = 175,
            baseStamina = 225
        ),
        PokemonSpecies(
            dex = 308,
            name = "Medicham",
            speciesId = "medicham",
            types = listOf("fighting", "psychic"),
            familyId = "meditite",
            parentSpeciesId = "meditite",
            baseAttack = 121,
            baseDefense = 152,
            baseStamina = 155
        ),
        PokemonSpecies(
            dex = 618,
            name = "Stunfisk (Galarian)",
            form = "Galarian",
            speciesId = "stunfisk_galarian",
            types = listOf("ground", "steel"),
            familyId = "stunfisk",
            baseAttack = 144,
            baseDefense = 171,
            baseStamina = 240
        )
    )
}
