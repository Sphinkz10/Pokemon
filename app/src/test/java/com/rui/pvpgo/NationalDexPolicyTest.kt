package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests of the real National Pokédex policy; no internet or emulator required. */
class NationalDexPolicyTest {
    private val fixtures = NationalDexPolicy.fromApiRecords(
        listOf(1026 to "example-future", 1 to "bulbasaur", 37 to "vulpix", 1025 to "pecharunt")
    )

    @Test fun indexSortsAndKeepsNewGenerations() {
        assertEquals(listOf(1, 37, 1025, 1026), fixtures.map { it.dex })
        assertEquals("Novas", NationalDexPolicy.generation(1026))
    }

    @Test fun numericSearchSupportsLeadingZeros() {
        for (query in listOf("1", "#1", "0001", "#0001")) {
            assertEquals("search: $query", listOf(1), NationalDexPolicy.filter(fixtures, query).map { it.dex })
        }
    }

    @Test fun nameSearchIsCaseInsensitive() {
        assertEquals(listOf(37), NationalDexPolicy.filter(fixtures, "VULPIX").map { it.dex })
    }

    @Test fun generationBoundaryRules() {
        val samples = mapOf(
            1 to "G1", 151 to "G1", 152 to "G2", 251 to "G2",
            252 to "G3", 386 to "G3", 387 to "G4", 493 to "G4",
            494 to "G5", 649 to "G5", 650 to "G6", 721 to "G6",
            722 to "G7", 809 to "G7", 810 to "G8", 905 to "G8",
            906 to "G9", 1025 to "G9", 1026 to "Novas"
        )
        samples.forEach { (dex, expected) ->
            assertEquals("Dex #$dex", expected, NationalDexPolicy.generation(dex))
        }
    }

    @Test fun filterUsesGenerationWithoutTruncatingCatalog() {
        assertEquals(listOf(1025), NationalDexPolicy.filter(fixtures, "", "G9").map { it.dex })
        assertEquals(listOf(1026), NationalDexPolicy.filter(fixtures, "", "Novas").map { it.dex })
    }

    @Test fun invalidOrDuplicateEntriesAreExcluded() {
        val result = NationalDexPolicy.fromApiRecords(
            listOf(0 to "invalid", 1 to "bulbasaur", 1 to "duplicate",
                65535 to "possible-future", 65536 to "invalid-upper", 42 to "")
        )
        assertEquals(listOf(1, 65535), result.map { it.dex })
        assertEquals("Bulbasaur", result.first().name)
    }

    @Test fun nationalArtworkSupportsFutureDexWithoutClaimingPvPCoverage() {
        assertFalse(PokemonArtworkSources.urls(1026).isEmpty())
        assertTrue(PokemonArtworkSources.urls(1026, shiny = true).first().contains("/shiny/"))
        assertTrue(PokemonArtworkSources.urls(1026, form = "alolan").isEmpty())
        assertTrue(PokemonArtworkSources.urls(0).isEmpty())
    }
}
