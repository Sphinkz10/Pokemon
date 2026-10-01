package com.rui.pvpgo

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class OwnedBuildValidationTest {
    @Test fun emptyConfirmedFieldsRemainUnknown() {
        assertNull(OwnedBuildValidation.cpError(""))
        assertNull(OwnedBuildValidation.levelError(""))
        assertNull(OwnedBuildValidation.chargedMovesError(emptyList()))
    }

    @Test fun cpHasPokemonGoMinimumAndRejectsMalformedValues() {
        for (valid in listOf("10", "1500", "10000")) assertNull(OwnedBuildValidation.cpError(valid))
        for (invalid in listOf("0", "9", "10001", "abc", "99999999999999999"))
            assertNotNull(OwnedBuildValidation.cpError(invalid))
    }

    @Test fun halfLevelsAllowedBothWithCommaAndDot() {
        for (valid in listOf("1", "1.5", "23,5", "50", "51"))
            assertNull(OwnedBuildValidation.levelError(valid))
        for (invalid in listOf("0.5", "51.5", "25.3", "abc", "NaN"))
            assertNotNull(OwnedBuildValidation.levelError(invalid))
    }

    @Test fun chargedMovesNeverExceedTwoAndAreUnique() {
        assertNull(OwnedBuildValidation.chargedMovesError(listOf("hydro-cannon")))
        assertNull(OwnedBuildValidation.chargedMovesError(listOf("hydro-cannon", "ice-beam")))
        assertNotNull(OwnedBuildValidation.chargedMovesError(listOf("one", "two", "three")))
        assertNotNull(OwnedBuildValidation.chargedMovesError(listOf("one", "one")))
    }
    @Test fun newMoveMustExistWhenCatalogKnown() {
        val allowedFast = setOf("vine-whip", "tackle")
        val allowedCharged = setOf("frenzy-plant", "sludge-bomb")
        assertNull(OwnedBuildValidation.movePoolError(
            "vine-whip", listOf("frenzy-plant"), allowedFast, allowedCharged,
            previousFastId = "tackle", previousChargedIds = listOf("sludge-bomb")
        ))
        assertNotNull(OwnedBuildValidation.movePoolError(
            "invented-move", listOf("frenzy-plant"), allowedFast, allowedCharged,
            previousFastId = "tackle", previousChargedIds = emptyList()
        ))
        assertNotNull(OwnedBuildValidation.movePoolError(
            "vine-whip", listOf("invented-charged"), allowedFast, allowedCharged,
            previousFastId = "tackle", previousChargedIds = emptyList()
        ))
    }

    @Test fun legacyEventMovesRemainIntactWithoutBeingOfferedAsNew() {
        val allowedFast = setOf("tackle")
        val allowedCharged = setOf("sludge-bomb")
        assertNull(OwnedBuildValidation.movePoolError(
            "legacy-fast", listOf("legacy-charged"), allowedFast, allowedCharged,
            previousFastId = "legacy-fast", previousChargedIds = listOf("legacy-charged")
        ))
        assertNotNull(OwnedBuildValidation.movePoolError(
            "legacy-fast", listOf("legacy-charged", "new-unknown"),
            allowedFast, allowedCharged,
            previousFastId = "legacy-fast", previousChargedIds = listOf("legacy-charged")
        ))
    }

    @Test fun unavailableCatalogDoesNotInventMoveRestrictions() {
        assertNull(OwnedBuildValidation.movePoolError(
            "unlisted", listOf("move-one", "move-two"),
            emptySet(), emptySet(), null, emptyList()
        ))
    }

}
