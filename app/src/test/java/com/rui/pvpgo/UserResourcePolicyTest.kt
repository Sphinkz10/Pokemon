package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unknown is not zero; an incomplete account never becomes ResourceLedger(). */
class UserResourcePolicyTest {
    private val full = UserResourceBalances(
        stardust = 12345, candy = 23, xlCandy = 2,
        fastTm = 5, chargedTm = 6, eliteFastTm = 1, eliteChargedTm = 0
    )

    @Test fun newInventoryStartsEntirelyUnknown() {
        assertEquals(0, UserResourceBalances().knownCount)
        assertNull(UserResourcePolicy.completeLedgerOrNull(UserResourceBalances(), "family"))
    }

    @Test fun explicitZeroIsKnownAndPreserved() {
        assertEquals(0, UserResourcePolicy.parse("0"))
        assertEquals(7, UserResourceBalances(
            0, 0, 0, 0, 0, 0, 0
        ).knownCount)
    }

    @Test fun blankIsUnknownAndNotZero() {
        assertNull(UserResourcePolicy.parse(""))
        assertNull(UserResourcePolicy.parse("  "))
        assertTrue(UserResourcePolicy.isValidInput(" "))
    }

    @Test fun negativeAndNonNumericAreInvalidNotMissing() {
        for (value in listOf("-1", "N/A", "2,5", "1.3", "+2", "∞")) {
            assertFalse(value, UserResourcePolicy.isValidInput(value))
            assertNull(UserResourcePolicy.parse(value))
        }
    }

    @Test fun enormousBalancesAreRejected() {
        assertFalse(UserResourcePolicy.isValidInput("1000000000"))
        assertNull(UserResourcePolicy.parse("9999999999"))
        assertEquals(999999999, UserResourcePolicy.parse("999999999"))
    }

    @Test fun leadingZerosAreExplicitNumberNotUnknown() {
        assertEquals(3, UserResourcePolicy.parse("003"))
        assertEquals(0, UserResourcePolicy.parse("000"))
    }

    @Test fun whitespaceAroundNumberIsHandled() {
        assertEquals(24, UserResourcePolicy.parse(" 24 "))
    }

    @Test fun completeResourceInventoryCanBeConvertedOnlyForNamedFamily() {
        val ledger = UserResourcePolicy.completeLedgerOrNull(full, "pika-family")
        assertEquals(12345, ledger?.stardust)
        assertEquals(23, ledger?.candyByFamily?.get("pika-family"))
        assertEquals(2, ledger?.xlCandyByFamily?.get("pika-family"))
        assertEquals(5, ledger?.fastTm)
        assertEquals(6, ledger?.chargedTm)
        assertEquals(1, ledger?.eliteFastTm)
        assertEquals(0, ledger?.eliteChargedTm)
    }

    @Test fun noUnrelatedFamiliesAreInvented() {
        val ledger = UserResourcePolicy.completeLedgerOrNull(full, "evo-line")
        assertEquals(setOf("evo-line"), ledger?.candyByFamily?.keys)
        assertEquals(setOf("evo-line"), ledger?.xlCandyByFamily?.keys)
        assertFalse(ledger?.candyByFamily?.containsKey("other") ?: true)
    }

    @Test fun anyOneUnknownValuePreventsCastingToCompleteLedger() {
        val incomplete = listOf(
            full.copy(stardust = null),
            full.copy(candy = null),
            full.copy(xlCandy = null),
            full.copy(fastTm = null),
            full.copy(chargedTm = null),
            full.copy(eliteFastTm = null),
            full.copy(eliteChargedTm = null)
        )
        assertEquals(7, incomplete.size)
        assertTrue(incomplete.all {
            it.knownCount == 6 && UserResourcePolicy.completeLedgerOrNull(it, "family") == null
        })
    }

    @Test fun blankFamilyNeverCreatesInventedCandyBucket() {
        assertNull(UserResourcePolicy.completeLedgerOrNull(full, ""))
    }

    @Test fun invalidNegativeBalanceCannotBePersistedOrConverted() {
        val negative = full.copy(stardust = -1)
        assertFalse(UserResourcePolicy.validated(negative))
        assertNull(UserResourcePolicy.completeLedgerOrNull(negative, "family"))
    }

    @Test fun tooLargeBalanceCannotBePersistedOrConverted() {
        val huge = full.copy(candy = 1_000_000_000)
        assertFalse(UserResourcePolicy.validated(huge))
        assertNull(UserResourcePolicy.completeLedgerOrNull(huge, "family"))
    }

    @Test fun fullInputRoundTripPreservesExplicitZero() {
        val inputs = UserResourcePolicy.inputsFrom(full)
        assertEquals("0", inputs[ResourceBalanceField.ELITE_CHARGED_TM])
        assertEquals(full, UserResourcePolicy.balancesFrom(inputs))
    }

    @Test fun unknownFieldsAreRepresentedByBlankInputs() {
        val fields = UserResourcePolicy.inputsFrom(UserResourceBalances())
        assertTrue(fields.values.all { it.isBlank() })
        assertEquals(UserResourceBalances(), UserResourcePolicy.balancesFrom(fields))
    }

    @Test fun invalidTextInputNeverBecomesUnknownBalance() {
        val inputs = UserResourcePolicy.inputsFrom(full)
        assertNull(UserResourcePolicy.balancesFrom(
            inputs + (ResourceBalanceField.CANDY to "invalid")
        ))
    }

    @Test fun clearingOneFieldDoesNotAffectOtherExplicitBalances() {
        val inputs = UserResourcePolicy.inputsFrom(full)
        val revised = UserResourcePolicy.balancesFrom(
            inputs + (ResourceBalanceField.FAST_TM to "")
        )
        assertNull(revised?.fastTm)
        assertEquals(12345, revised?.stardust)
        assertEquals(0, revised?.eliteChargedTm)
        assertEquals(6, revised?.knownCount)
    }

    @Test fun UIExplainsFamilyScopingAndStaleData() {
        assertTrue(UserResourcePolicy.SCOPE_NOTE.contains("família"))
        assertTrue(UserResourcePolicy.SCOPE_NOTE.contains("desatualizados"))
        assertTrue(UserResourcePolicy.SCOPE_NOTE.contains("compense investir"))
    }
}
