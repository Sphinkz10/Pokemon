package com.rui.pvpgo

import com.rui.pvpgo.domain.InvestmentTarget
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PowerUpStepCost
import com.rui.pvpgo.domain.ResourceLedger
import com.rui.pvpgo.engine.InvestmentCostPackValidator
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentCostQuoteGateTest {
    private val species = PokemonSpecies(
        dex = 12, speciesId = "fixture", name = "Exemplo", familyId = "fixture",
        baseAttack = 105, baseDefense = 110, baseStamina = 110
    )
    private val iv = IvSpread(0, 15, 15)
    private val owned = OwnedPokemon("x", "fixture", iv,
        level = 20.0, cp = PokemonMath.cp(species, iv, 20.0), createdAtEpochMs = 1)
    private val preview = InvestmentTargetSimulator.simulate(owned, species, League.GREAT, "20.5")
    private val target = InvestmentTarget("x", League.GREAT, "fixture", 20.5)
    private val step = PowerUpStepCost(20.0, 20.5, 3000, candy = 2)
    private val pack = InvestmentCostPackValidator.build(
        "fixture-costs", "v1", "test-fixture-not-public-prices", "1", 1234L,
        listOf(step), emptyList(), emptyList()
    )
    private val ledger = ResourceLedger(stardust = 10000, candyByFamily = mapOf("fixture" to 10))
    private fun quote(
        selected: InvestmentTarget? = target,
        availablePack: com.rui.pvpgo.domain.InvestmentCostPack? = pack,
        availableLedger: ResourceLedger? = ledger,
        simulation: TargetSimulation = preview
    ) = InvestmentCostQuoteGate.evaluate(owned, simulation, listOf(species),
        emptyMap(), availablePack, availableLedger, selected)

    @Test fun invalidOrMissingTargetNeverCallsPlanner() {
        assertEquals(CostQuoteGateStatus.BLOCKED_TARGET, quote(selected = null).status)
        assertEquals(CostQuoteGateStatus.BLOCKED_TARGET,
            quote(selected = target.copy(targetLevel = 21.0)).status)
        assertEquals(CostQuoteGateStatus.BLOCKED_TARGET,
            quote(selected = target.copy(ownedPokemonId = "other")).status)
    }

    @Test fun unavailableCostPackDoesNotExposeAmounts() {
        val result = quote(availablePack = null)
        assertEquals(CostQuoteGateStatus.MISSING_COST_PACK, result.status)
        assertFalse(result.canDisplayExactAmounts)
        assertEquals(null, result.plan)
        assertTrue(result.explanation.contains("desconhecidos"))
    }

    @Test fun integrityFailureCannotBecomeAFakeQuote() {
        val forged = pack.copy(contentHashSha256 = "0".repeat(64))
        val result = quote(availablePack = forged)
        assertEquals(CostQuoteGateStatus.INVALID_COST_PACK, result.status)
        assertFalse(result.canDisplayExactAmounts)
    }

    @Test fun unknownInventoryIsNotAssumedToBeZero() {
        val result = quote(availableLedger = null)
        assertEquals(CostQuoteGateStatus.MISSING_INVENTORY, result.status)
        assertFalse(result.canDisplayExactAmounts)
    }

    @Test fun validatedCostDataCanUseTheExistingPlanner() {
        val result = quote()
        assertEquals(CostQuoteGateStatus.VERIFIED_COST, result.status)
        assertTrue(result.canDisplayExactAmounts)
        assertEquals(3000, result.plan?.costs?.stardust)
        assertEquals(2, result.plan?.costs?.candyByFamily?.get("fixture"))
        assertTrue(result.explanation.contains("fixture-costs"))
    }

    @Test fun explicitEmptyInventoryShowsMissingResourcesNotInvalidCost() {
        val result = quote(availableLedger = ResourceLedger())
        assertEquals(CostQuoteGateStatus.VERIFIED_COST, result.status)
        assertTrue(result.canDisplayExactAmounts)
        assertEquals(com.rui.pvpgo.domain.InvestmentPlanStatus.MISSING_RESOURCES, result.plan?.status)
    }

    @Test fun incompletePackBlocksAnyTotalEvenIfHashIsValid() {
        val missing = InvestmentCostPackValidator.build(
            "fixture-missing", "v1", "test", "1", 1L,
            emptyList(), emptyList(), emptyList()
        )
        val result = quote(availablePack = missing)
        assertEquals(CostQuoteGateStatus.INCOMPLETE_COST, result.status)
        assertFalse(result.canDisplayExactAmounts)
        assertNotNull(result.plan)
        assertFalse(result.plan!!.exactCostKnown)
    }

    @Test fun genericLeagueBlockPreventsAllPriceCalculation() {
        val invalidPreview = InvestmentTargetSimulator.simulate(owned, species, League.LITTLE, "50")
        val result = quote(
            selected = InvestmentTarget("x", League.LITTLE, "fixture", 50.0),
            simulation = invalidPreview
        )
        assertEquals(CostQuoteGateStatus.BLOCKED_TARGET, result.status)
    }

    @Test fun missingCurrentLevelRemainsAPlannerBlockEvenWithCostPack() {
        val bad = owned.copy(level = null)
        val noData = InvestmentTargetSimulator.simulate(bad, species, League.GREAT, "20.5")
        val result = InvestmentCostQuoteGate.evaluate(
            bad, noData, listOf(species), emptyMap(), pack, ledger, target)
        assertEquals(CostQuoteGateStatus.BLOCKED_TARGET, result.status)
    }

    @Test fun exactQuoteDoesNotClaimCompetitiveStrength() {
        val result = quote()
        assertTrue(result.canDisplayExactAmounts)
        assertTrue(result.explanation.contains("Não confirma força no meta"))
        assertFalse(result.explanation.contains("deves investir"))
    }

    @Test fun laterCostPackUpgradeCanChangeQuoteWithoutTouchingIvRank() {
        val moreExpensive = InvestmentCostPackValidator.build(
            "fixture-costs", "v2", "test-fixture-not-public-prices", "2", 1235L,
            listOf(PowerUpStepCost(20.0, 20.5, 6000, candy = 3)), emptyList(), emptyList()
        )
        val old = quote()
        val updated = quote(availablePack = moreExpensive)
        assertEquals(3000, old.plan?.costs?.stardust)
        assertEquals(6000, updated.plan?.costs?.stardust)
        assertEquals(CostQuoteGateStatus.VERIFIED_COST, updated.status)
    }

    @Test fun luckySpecimenCannotReceiveAnUnverifiedExactDiscount() {
        val lucky = owned.copy(isLucky = true)
        val result = InvestmentCostQuoteGate.evaluate(lucky, preview, listOf(species),
            emptyMap(), pack, ledger, target)
        assertEquals(CostQuoteGateStatus.UNVERIFIED_COST_MODIFIER, result.status)
        assertFalse(result.canDisplayExactAmounts)
    }

    @Test fun shadowSpecimenCannotReceiveBasePowerUpPricing() {
        val shadow = owned.copy(isShadow = true)
        val result = InvestmentCostQuoteGate.evaluate(shadow, preview, listOf(species),
            emptyMap(), pack, ledger, target)
        assertEquals(CostQuoteGateStatus.UNVERIFIED_COST_MODIFIER, result.status)
        assertFalse(result.canDisplayExactAmounts)
    }

    @Test fun purifiedSpecimenCannotReceiveBasePowerUpPricing() {
        val purified = owned.copy(isPurified = true)
        val result = InvestmentCostQuoteGate.evaluate(purified, preview, listOf(species),
            emptyMap(), pack, ledger, target)
        assertEquals(CostQuoteGateStatus.UNVERIFIED_COST_MODIFIER, result.status)
        assertFalse(result.canDisplayExactAmounts)
    }

    @Test fun targetHasToBeCurrentSpeciesUntilEvolutionIsVerified() {
        val targetEvolution = target.copy(targetSpeciesId = "future_evolution")
        assertEquals(CostQuoteGateStatus.BLOCKED_TARGET, quote(selected = targetEvolution).status)
    }
}
