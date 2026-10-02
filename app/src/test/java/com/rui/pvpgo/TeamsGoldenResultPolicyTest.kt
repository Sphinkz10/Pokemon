package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamsGoldenResultPolicyTest {
    private val primary = listOf("a", "b", "c")
    private val alternatives = listOf(listOf("a", "d", "c"), listOf("a", "b", "e"))
    private val roles = mapOf("a" to "LEAD", "b" to "SAFE_SWITCH", "c" to "CLOSER")

    @Test fun onlyDistinctCompleteTeamsAreValid() {
        assertTrue(TeamsGoldenResultPolicy.validIds(primary))
        assertFalse(TeamsGoldenResultPolicy.validIds(listOf("a", "b")))
        assertFalse(TeamsGoldenResultPolicy.validIds(listOf("a", "a", "c")))
        assertFalse(TeamsGoldenResultPolicy.validIds(listOf("a", "", "c")))
    }

    @Test fun unverifiedOrReorderedTeamsCannotBeSaved() {
        assertTrue(TeamsGoldenResultPolicy.isVerified(primary, alternatives, alternatives.first()))
        assertFalse(TeamsGoldenResultPolicy.isVerified(primary, alternatives, listOf("a", "c", "d")))
        assertNull(TeamsGoldenResultPolicy.rolesForVerifiedSingleSwap(primary, alternatives, listOf("x", "y", "z"), roles))
    }

    @Test fun verifiedSwapPreservesUnchangedRoles() {
        assertEquals(mapOf("a" to "LEAD", "d" to "SAFE_SWITCH", "c" to "CLOSER"),
            TeamsGoldenResultPolicy.rolesForVerifiedSingleSwap(primary, alternatives, alternatives.first(), roles))
        assertNull(TeamsGoldenResultPolicy.rolesForVerifiedSingleSwap(primary, alternatives, alternatives.first(), roles - "a"))
    }

    @Test fun invalidCoverageCannotOutrankValidEngineResult() {
        assertEquals(alternatives.first(), TeamsGoldenResultPolicy.highestCoverage(primary, Double.NaN, listOf(alternatives.first() to 62.0)))
        assertEquals(primary, TeamsGoldenResultPolicy.highestCoverage(primary, 65.0, listOf(alternatives.first() to Double.POSITIVE_INFINITY)))
        assertEquals(primary, TeamsGoldenResultPolicy.highestCoverage(primary, 65.0, listOf(alternatives.first() to 102.0)))
    }
}
