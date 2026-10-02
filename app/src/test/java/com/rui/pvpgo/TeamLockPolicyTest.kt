package com.rui.pvpgo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamLockPolicyTest {
    @Test fun zeroToThreeLockedPokemonAreAllowed() {
        for (count in 0..3) assertTrue(TeamLockPolicy.hasValidLockCount(count))
    }

    @Test fun fourOrMoreLockedPokemonAreRejected() {
        assertFalse(TeamLockPolicy.hasValidLockCount(4))
        assertFalse(TeamLockPolicy.hasValidLockCount(10))
    }

    @Test fun invalidNegativeCountIsRejected() {
        assertFalse(TeamLockPolicy.hasValidLockCount(-1))
    }
}
