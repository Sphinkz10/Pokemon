package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentTargetSimulatorTest {
    private val species = PokemonSpecies(
        dex = 12, speciesId = "fixture", name = "Exemplo",
        baseAttack = 105, baseDefense = 110, baseStamina = 110
    )
    private val iv = IvSpread(0, 15, 15)
    private fun owned(level: Double? = 20.0, cp: Int? = level?.let {
        PokemonMath.cp(species, iv, it)
    }): OwnedPokemon = OwnedPokemon("x", "fixture", iv, cp = cp, level = level, createdAtEpochMs = 1)

    @Test fun blankLevelRequestsExplicitChoice() {
        val result = InvestmentTargetSimulator.simulate(owned(), species, League.GREAT, "")
        assertEquals(TargetSimulationStatus.NEEDS_DATA, result.status)
        assertNull(result.targetCp)
    }

    @Test fun invalidInputIsNotCoercedOrGuessed() {
        for (raw in listOf("abc", "-1", "0", "50.5", "51", "23.3", "NaN", "100")) {
            val actual = InvestmentTargetSimulator.simulate(owned(), species, League.GREAT, raw)
            assertEquals(raw, TargetSimulationStatus.INVALID_LEVEL, actual.status)
            assertFalse(actual.canRequestCosting)
        }
    }

    @Test fun portugueseCommaAndDecimalPointAreBothAccepted() {
        val comma = InvestmentTargetSimulator.simulate(owned(), species, League.GREAT, "20,5")
        val dot = InvestmentTargetSimulator.simulate(owned(), species, League.GREAT, "20.5")
        assertEquals(TargetSimulationStatus.READY_FOR_COSTING, comma.status)
        assertEquals(comma.targetCp, dot.targetCp)
        assertEquals(1, comma.powerUpSteps)
    }

    @Test fun preciseTargetCpComesFromRealStatsAndSelectedLevel() {
        val result = InvestmentTargetSimulator.simulate(owned(), species, League.GREAT, "22")
        assertEquals(PokemonMath.cp(species, iv, 22.0), result.targetCp)
        assertEquals(4, result.powerUpSteps)
        assertTrue(result.message.contains("CP projetado"))
    }

    @Test fun currentLevelIsNeverGuessed() {
        val result = InvestmentTargetSimulator.simulate(owned(level = null, cp = null), species, League.GREAT, "30")
        assertEquals(TargetSimulationStatus.NEEDS_DATA, result.status)
    }

    @Test fun currentCpIsNeverDerivedAsAnObservedValue() {
        val result = InvestmentTargetSimulator.simulate(owned(cp = null), species, League.GREAT, "30")
        assertEquals(TargetSimulationStatus.NEEDS_DATA, result.status)
    }

    @Test fun missingOrMismatchedSpeciesCannotProceed() {
        for (value in listOf<PokemonSpecies?>(null, species.copy(speciesId = "another"))) {
            val actual = InvestmentTargetSimulator.simulate(owned(), value, League.GREAT, "22")
            assertEquals(TargetSimulationStatus.NEEDS_DATA, actual.status)
        }
    }

    @Test fun wrongCpBlocksProjectionRatherThanSilentlyCorrectingIt() {
        val actual = InvestmentTargetSimulator.simulate(
            owned(cp = PokemonMath.cp(species, iv, 20.0) + 1), species, League.GREAT, "22"
        )
        assertEquals(TargetSimulationStatus.INVALID_CURRENT_DATA, actual.status)
        assertNull(actual.targetCp)
    }

    @Test fun targetBelowCurrentDoesNotSuggestDeLeveling() {
        val actual = InvestmentTargetSimulator.simulate(owned(), species, League.GREAT, "19,5")
        assertEquals(TargetSimulationStatus.INVALID_LEVEL, actual.status)
    }

    @Test fun noChangeNeedsZeroStepsButIsNotAMetaRecommendation() {
        val actual = InvestmentTargetSimulator.simulate(owned(), species, League.GREAT, "20")
        assertEquals(TargetSimulationStatus.READY_FOR_COSTING, actual.status)
        assertEquals(0, actual.powerUpSteps)
        assertTrue(actual.message.contains("custo e utilidade PvP continuam desconhecidos"))
    }

    @Test fun littleLeagueCapIsEnforced() {
        val actual = InvestmentTargetSimulator.simulate(owned(), species, League.LITTLE, "50")
        assertEquals(TargetSimulationStatus.BASE_LEAGUE_BLOCKED, actual.status)
        assertTrue(actual.message.contains("CP_CAP_EXCEEDED"))
        assertFalse(actual.canRequestCosting)
    }

    @Test fun masterLeagueHasNoInventedCap() {
        val actual = InvestmentTargetSimulator.simulate(owned(), species, League.MASTER, "50")
        assertEquals(TargetSimulationStatus.READY_FOR_COSTING, actual.status)
        assertTrue(actual.canRequestCosting)
    }

    @Test fun megaIsNotEligibleForGenericStandardLeague() {
        val mega = species.copy(speciesId = "fixture_mega", tags = setOf("mega"))
        val specimen = owned().copy(speciesId = mega.speciesId)
        val actual = InvestmentTargetSimulator.simulate(specimen, mega, League.GREAT, "21")
        assertEquals(TargetSimulationStatus.BASE_LEAGUE_BLOCKED, actual.status)
    }

    @Test fun primalIsNotEligibleForGenericStandardLeague() {
        val primal = species.copy(speciesId = "fixture_primal", tags = setOf("primal"))
        val specimen = owned().copy(speciesId = primal.speciesId)
        val actual = InvestmentTargetSimulator.simulate(specimen, primal, League.GREAT, "21")
        assertEquals(TargetSimulationStatus.BASE_LEAGUE_BLOCKED, actual.status)
    }

    @Test fun differentLeaguesCanGiveDifferentFeasibility() {
        val cap = InvestmentTargetSimulator.simulate(owned(), species, League.LITTLE, "50")
        val noCap = InvestmentTargetSimulator.simulate(owned(), species, League.MASTER, "50")
        assertFalse(cap.canRequestCosting)
        assertTrue(noCap.canRequestCosting)
    }

    @Test fun incompleteStepDoesNotProduceInventedResources() {
        val result = InvestmentTargetSimulator.simulate(owned(), species, League.GREAT, "20.5")
        assertTrue(result.canRequestCosting)
        assertTrue(InvestmentTargetSimulator.SAFETY_NOTE.contains("Stardust/Candy"))
        assertFalse(result.message.contains("Stardust:"))
    }
}
