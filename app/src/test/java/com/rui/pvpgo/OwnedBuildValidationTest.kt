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
}
