package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamPickerSearchPolicyTest {
    private val charizard = OwnedPokemon(
        id = "owned-6", speciesId = "charizard", nickname = "Dragão",
        iv = IvSpread(0, 14, 15), level = 20.0,
        fastMoveId = "fire-spin", chargedMoveIds = listOf("blast-burn"),
        createdAtEpochMs = 1L
    )

    @Test fun searchesDexWithOrWithoutHash() {
        assertTrue(TeamPickerSearchPolicy.matches(charizard, "Charizard", 6, "#6"))
        assertTrue(TeamPickerSearchPolicy.matches(charizard, "Charizard", 6, "6"))
        assertFalse(TeamPickerSearchPolicy.matches(charizard, "Charizard", 6, "#7"))
    }

    @Test fun searchesNamesAndSpeciesIdIgnoringCase() {
        assertTrue(TeamPickerSearchPolicy.matches(charizard, "Charizard", 6, "CHAR"))
        assertTrue(TeamPickerSearchPolicy.matches(charizard, "Charizard", 6, "IZARD"))
        assertTrue(TeamPickerSearchPolicy.matches(charizard, "Charizard", 6, "dragão"))
    }

    @Test fun emptyQueryShowsAllAndUnrelatedQueryDoesNotMatch() {
        assertTrue(TeamPickerSearchPolicy.matches(charizard, "Charizard", 6, "  "))
        assertFalse(TeamPickerSearchPolicy.matches(charizard, "Charizard", 6, "pikachu"))
    }
}
