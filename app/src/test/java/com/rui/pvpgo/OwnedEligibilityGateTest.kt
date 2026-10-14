package com.rui.pvpgo

import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.OwnedEligibilityGate
import com.rui.pvpgo.engine.OwnedEligibilityIssue
import com.rui.pvpgo.engine.OwnedEligibilityStatus
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.StandardRulesets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedEligibilityGateTest {
    private val base = PokemonSpecies(
        dex = 1, name = "Bulbasaur", speciesId = "bulbasaur",
        baseAttack = 118, baseDefense = 111, baseStamina = 128
    )

    private fun check(
        pokemon: PokemonSpecies = base,
        league: League = League.GREAT,
        cp: Int? = 450,
        level: Double? = 20.0,
        evolutionRequired: Boolean = false,
        canEvolve: Boolean? = null,
        allowPrimal: Boolean = false,
        ruleset: PvPRuleset = StandardRulesets.forLeague(league)
    ) = OwnedEligibilityGate.evaluate(
        ruleset, pokemon, cp, level, evolutionRequired, canEvolve, allowPrimal
    )

    @Test fun missingCpAndLevelCannotBeConfirmed() {
        val decision = check(cp = null, level = null)
        assertEquals(OwnedEligibilityStatus.NEEDS_DATA, decision.status)
        assertFalse(decision.confirmed)
        assertTrue(OwnedEligibilityIssue.CP_UNKNOWN in decision.issues)
        assertTrue(OwnedEligibilityIssue.LEVEL_UNKNOWN in decision.issues)
    }

    @Test fun missingLevelCannotBeConfirmed() {
        val decision = check(level = null)
        assertEquals(OwnedEligibilityStatus.NEEDS_DATA, decision.status)
    }

    @Test fun knownEligibleInputsAreConfirmed() {
        assertEquals(OwnedEligibilityStatus.CONFIRMED, check().status)
    }

    @Test fun cpExceedingCapIsIneligibleEvenIfLevelIsUnknown() {
        assertEquals(OwnedEligibilityStatus.INELIGIBLE,
            check(cp = 1600, level = null).status)
    }

    @Test fun invalidCpIsNotTreatedAsMissing() {
        val decision = check(cp = 0)
        assertEquals(OwnedEligibilityStatus.INELIGIBLE, decision.status)
        assertTrue(OwnedEligibilityIssue.INVALID_CP in decision.issues)
    }

    @Test fun primalNeedsSeparatePermissionEvenWhenMegaIsAllowed() {
        val primal = base.copy(
            speciesId = "groudon_primal", form = "Primal", tags = setOf("primal")
        )
        val megaCup = StandardRulesets.forLeague(League.GREAT).copy(allowMega = true)
        val decision = check(pokemon = primal, ruleset = megaCup)
        assertEquals(OwnedEligibilityStatus.INELIGIBLE, decision.status)
        assertTrue(OwnedEligibilityIssue.PRIMAL_NOT_ALLOWED in decision.issues)
        assertEquals(OwnedEligibilityStatus.CONFIRMED,
            check(pokemon = primal, ruleset = megaCup, allowPrimal = true).status)
    }

    @Test fun littleCupRuleWithUnknownEvolutionIsNotConfirmed() {
        val decision = check(
            league = League.LITTLE, evolutionRequired = true, canEvolve = null
        )
        assertEquals(OwnedEligibilityStatus.NEEDS_DATA, decision.status)
        assertTrue(OwnedEligibilityIssue.EVOLUTION_UNKNOWN in decision.issues)
    }

    @Test fun littleCupRuleRejectsNonEvolvingSpecies() {
        val decision = check(
            league = League.LITTLE, evolutionRequired = true, canEvolve = false
        )
        assertEquals(OwnedEligibilityStatus.INELIGIBLE, decision.status)
    }

    @Test fun littleCupMayConfirmWhenEvolutionRequirementIsSatisfied() {
        assertEquals(OwnedEligibilityStatus.CONFIRMED, check(
            league = League.LITTLE, evolutionRequired = true, canEvolve = true
        ).status)
    }

    @Test fun masterWithoutCapDoesNotNeedCpButStillNeedsLevel() {
        assertEquals(OwnedEligibilityStatus.CONFIRMED,
            check(league = League.MASTER, cp = null).status)
        assertEquals(OwnedEligibilityStatus.NEEDS_DATA,
            check(league = League.MASTER, cp = null, level = null).status)
    }

    @Test fun explicitlyExcludedSpeciesRemainIneligibleEvenWithUnknownData() {
        val cup = StandardRulesets.forLeague(League.GREAT).copy(
            explicitlyExcludedSpeciesIds = setOf("bulbasaur")
        )
        assertEquals(OwnedEligibilityStatus.INELIGIBLE,
            check(cp = null, level = null, ruleset = cup).status)
    }
}
