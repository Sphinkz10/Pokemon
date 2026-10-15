package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueCapTargetPolicyTest {
    private val species = PokemonSpecies(
        dex = 12, speciesId = "fixture", name = "Exemplo",
        baseAttack = 105, baseDefense = 110, baseStamina = 110
    )
    private val iv = IvSpread(0, 15, 15)
    private fun saved(
        level: Double? = 20.0,
        cp: Int? = level?.let { PokemonMath.cp(species, iv, it) }
    ) = OwnedPokemon(
        id = "owned", speciesId = "fixture", iv = iv,
        cp = cp, level = level, createdAtEpochMs = 1L
    )
    private fun suggest(
        league: League = League.GREAT,
        owned: OwnedPokemon = saved(),
        catalog: PokemonSpecies? = species
    ) = LeagueCapTargetPolicy.suggest(owned, catalog, league)

    @Test fun greatReturnsHighestHalfLevelAllowedByCp() {
        val result = suggest()
        assertTrue(result.canApply)
        assertTrue(result.targetCp!! <= 1500)
        assertTrue(result.targetLevel!! >= 20.0)
        assertEquals(PokemonMath.highestLevelAtOrBelowCap(species, iv, 1500, 20.0, 50.0),
            result.targetLevel)
    }

    @Test fun greatSuggestedNextHalfLevelWouldExceedCapWhenBelowFifty() {
        val result = suggest()
        val next = result.targetLevel!! + 0.5
        if (next <= 50.0) {
            assertTrue(PokemonMath.cp(species, iv, next) > 1500)
        }
    }

    @Test fun ultraReturnsItsOwnMaximumNotTheGreatTarget() {
        val great = suggest(League.GREAT)
        val ultra = suggest(League.ULTRA)
        assertTrue(ultra.canApply)
        assertTrue(ultra.targetCp!! <= 2500)
        assertTrue(ultra.targetLevel!! >= great.targetLevel!!)
    }

    @Test fun littleStartsWithSmallSpecimenAndNeverGoesOverFiveHundred() {
        val result = suggest(League.LITTLE, saved(level = 1.0))
        assertEquals(LeagueCapTargetStatus.AVAILABLE, result.status)
        assertTrue(result.targetCp!! <= 500)
        assertEquals(PokemonMath.cp(species, iv, result.targetLevel!!), result.targetCp)
    }

    @Test fun masterReturnsFiftyWithoutPretendingThereIsACpCeiling() {
        val result = suggest(League.MASTER)
        assertEquals(LeagueCapTargetStatus.AVAILABLE, result.status)
        assertEquals(50.0, result.targetLevel!!, 0.0)
        assertEquals(60, result.halfLevelSteps)
        assertTrue(result.explanation.contains("sem limite de CP"))
    }

    @Test fun alreadyAtMaximumIsZeroPowerupsNotAMandatoryUpgrade() {
        val top = suggest()
        val atTop = suggest(owned = saved(level = top.targetLevel))
        assertEquals(LeagueCapTargetStatus.AVAILABLE, atTop.status)
        assertEquals(0, atTop.halfLevelSteps)
        assertTrue(atTop.explanation.contains("Sem novos power-ups"))
    }

    @Test fun alreadyOverLeagueCapMustNeverProposeDeLeveling() {
        val result = suggest(League.LITTLE, saved(level = 50.0))
        assertEquals(LeagueCapTargetStatus.ALREADY_OVER_CAP, result.status)
        assertFalse(result.canApply)
        assertNull(result.targetLevel)
        assertTrue(result.explanation.contains("Não é possível reduzir"))
    }

    @Test fun missingCpDoesNotAssumeComputedCpWasObserved() {
        val result = suggest(owned = saved(cp = null))
        assertEquals(LeagueCapTargetStatus.NEEDS_SAVED_DATA, result.status)
        assertNull(result.targetCp)
    }

    @Test fun missingLevelDoesNotAssumeDefaultTwenty() {
        val result = suggest(owned = saved(level = null, cp = 500))
        assertEquals(LeagueCapTargetStatus.NEEDS_SAVED_DATA, result.status)
        assertFalse(result.canApply)
    }

    @Test fun missingSpeciesIsNotAutoDetectedFromDex() {
        val result = suggest(catalog = null)
        assertEquals(LeagueCapTargetStatus.NEEDS_SAVED_DATA, result.status)
    }

    @Test fun mismatchedCatalogFormIsRejected() {
        val result = suggest(catalog = species.copy(speciesId = "other-form"))
        assertEquals(LeagueCapTargetStatus.NEEDS_SAVED_DATA, result.status)
    }

    @Test fun storedCpMismatchingIvsRequiresCorrectionBeforeTarget() {
        val wrong = saved(cp = PokemonMath.cp(species, iv, 20.0) + 1)
        val result = suggest(owned = wrong)
        assertEquals(LeagueCapTargetStatus.INCONSISTENT_CP, result.status)
        assertFalse(result.canApply)
    }

    @Test fun megaIsNotMadeEligibleByGenericLimitSearch() {
        val mega = species.copy(speciesId = "fixture_mega", tags = setOf("mega"))
        val item = saved().copy(speciesId = mega.speciesId)
        val result = suggest(owned = item, catalog = mega)
        assertEquals(LeagueCapTargetStatus.INELIGIBLE_STANDARD_LEAGUE, result.status)
    }

    @Test fun primalIsNotMadeEligibleByGenericLimitSearch() {
        val primal = species.copy(speciesId = "fixture_primal", tags = setOf("primal"))
        val item = saved().copy(speciesId = primal.speciesId)
        val result = suggest(owned = item, catalog = primal)
        assertEquals(LeagueCapTargetStatus.INELIGIBLE_STANDARD_LEAGUE, result.status)
    }

    @Test fun unreleasedSpeciesIsNotPromotedToEligible() {
        val future = species.copy(released = false)
        val result = suggest(catalog = future)
        assertEquals(LeagueCapTargetStatus.INELIGIBLE_STANDARD_LEAGUE, result.status)
    }

    @Test fun levelOverFiftyCannotBeSimulatedWithoutBestBuddySupport() {
        val result = suggest(League.MASTER, saved(level = 50.5, cp = 9999))
        assertEquals(LeagueCapTargetStatus.LEVEL_OVER_SIMULATOR_LIMIT, result.status)
        assertFalse(result.canApply)
    }

    @Test fun allSuggestedTargetsPassExistingManualTargetGate() {
        for ((league, item) in listOf(
            League.LITTLE to saved(level = 1.0),
            League.GREAT to saved(),
            League.ULTRA to saved(),
            League.MASTER to saved()
        )) {
            val target = suggest(league, item)
            assertTrue("$league: ${target.explanation}", target.canApply)
            val manual = InvestmentTargetSimulator.simulate(
                item, species, league, LeagueCapTargetPolicy.displayLevel(target.targetLevel!!)
            )
            assertTrue(manual.canRequestCosting)
            assertEquals(manual.targetCp, target.targetCp)
            assertEquals(manual.powerUpSteps, target.halfLevelSteps)
        }
    }

    @Test fun allSuggestedTargetsKeepTheSameSpeciesAndOriginalIvs() {
        val before = saved()
        val after = suggest(League.GREAT, before)
        assertTrue(after.canApply)
        assertEquals(IvSpread(0, 15, 15), before.iv)
        assertEquals("fixture", before.speciesId)
        assertEquals(20.0, before.level!!, 0.0)
    }

    @Test fun levelFormattingIsPortugueseAndHalfLevelExact() {
        assertEquals("25", LeagueCapTargetPolicy.displayLevel(25.0))
        assertEquals("25,5", LeagueCapTargetPolicy.displayLevel(25.5))
        assertEquals("1", LeagueCapTargetPolicy.displayLevel(1.0))
    }

    @Test fun noMetaOrCupVerdictMayBeImpliedByTheSuggestion() {
        assertTrue(LeagueCapTargetPolicy.DISCLAIMER.contains("não por força em PvP"))
        assertTrue(LeagueCapTargetPolicy.DISCLAIMER.contains("copas especiais"))
        assertTrue(LeagueCapTargetPolicy.DISCLAIMER.contains("rank IV"))
        assertFalse(suggest().explanation.contains("recomendo investir"))
    }
}
