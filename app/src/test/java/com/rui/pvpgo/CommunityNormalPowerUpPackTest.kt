package com.rui.pvpgo

import com.rui.pvpgo.domain.InvestmentPlanStatus
import com.rui.pvpgo.domain.InvestmentTarget
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.InvestmentCostPackValidator
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

class CommunityNormalPowerUpPackTest {
    private val species = PokemonSpecies(
        dex = 26, speciesId = "normal-pokemon", familyId = "normal-family",
        name = "Normal Test", baseAttack = 110, baseDefense = 110, baseStamina = 120
    )
    private val iv = IvSpread(0, 15, 15)
    private val familyId = "normal-family"
    private val full = UserResourceBalances(
        stardust = 600000, candy = 500, xlCandy = 500,
        fastTm = 0, chargedTm = 0, eliteFastTm = 0, eliteChargedTm = 0
    )

    private fun total(from: Double, to: Double): Triple<Int, Int, Int> {
        val rows = CommunityNormalPowerUpPack.steps.filter { it.fromLevel >= from && it.toLevel <= to }
        return Triple(rows.sumOf { it.stardust }, rows.sumOf { it.candy }, rows.sumOf { it.xlCandy })
    }

    private fun quote(from: Double, to: Double, balances: UserResourceBalances = full): CostQuoteGateResult {
        val owned = OwnedPokemon(
            id = "owned-1", speciesId = species.speciesId, iv = iv,
            cp = PokemonMath.cp(species, iv, from), level = from, createdAtEpochMs = 1L
        )
        val simulation = InvestmentTargetSimulator.simulate(owned, species, League.MASTER, to.toString())
        return InvestmentCostQuoteGate.evaluate(
            owned, simulation, listOf(species), emptyMap(),
            CommunityNormalPowerUpPack.pack,
            UserResourcePolicy.completeLedgerOrNull(balances, familyId),
            InvestmentTarget(owned.id, League.MASTER, owned.speciesId, to)
        )
    }

    @Test fun costPackContainsEveryHalfLevelFromOneThroughFifty() {
        assertEquals(98, CommunityNormalPowerUpPack.steps.size)
        val levels = CommunityNormalPowerUpPack.steps.map { it.fromLevel }
        assertEquals((2..99).map { it / 2.0 }, levels)
        assertEquals(50.0, CommunityNormalPowerUpPack.steps.last().toLevel, 0.0)
    }

    @Test fun crossSourceTotalFromLevelOneToForty() {
        assertEquals(Triple(270000, 304, 0), total(1.0, 40.0))
    }

    @Test fun crossSourceTotalFromTwentyToForty() {
        assertEquals(Triple(225000, 248, 0), total(20.0, 40.0))
    }

    @Test fun crossSourceTotalFromThirtyToForty() {
        assertEquals(Triple(150000, 182, 0), total(30.0, 40.0))
    }

    @Test fun independentXlCrossCheckFortyToFifty() {
        assertEquals(Triple(250000, 0, 296), total(40.0, 50.0))
    }

    @Test fun cumulativeTwentyToFiftyHasIndependentReferenceTotal() {
        assertEquals(Triple(475000, 248, 296), total(20.0, 50.0))
    }

    @Test fun totalThirtyToFiftyMatchesIndependentReference() {
        assertEquals(Triple(400000, 182, 296), total(30.0, 50.0))
    }

    @Test fun exactTransitionAtFortyChangesFromCandyToXl() {
        assertEquals(Triple(10000, 15, 0), total(39.0, 39.5))
        assertEquals(Triple(10000, 0, 10), total(40.0, 40.5))
    }

    @Test fun specialCandyTransitionAtTwentyFiveAndTwentySixIsPreserved() {
        assertEquals(Triple(4000, 3, 0), total(25.5, 26.0))
        assertEquals(Triple(4000, 4, 0), total(26.0, 26.5))
    }

    @Test fun pinnedContentHashMatchesIndependentConstant() {
        assertEquals(CommunityNormalPowerUpPack.PINNED_HASH_SHA256,
            CommunityNormalPowerUpPack.pack.contentHashSha256)
        assertTrue(CommunityNormalPowerUpPack.authenticatedReference())
        assertTrue(InvestmentCostPackValidator.validate(CommunityNormalPowerUpPack.pack).isEmpty())
    }

    @Test fun packIsPowerUpOnlyWithNoUnverifiedEvolutionOrMoveCosts() {
        assertTrue(CommunityNormalPowerUpPack.pack.evolutionCosts.isEmpty())
        assertTrue(CommunityNormalPowerUpPack.pack.speciesCosts.isEmpty())
        assertTrue(CommunityNormalPowerUpPack.LIMITATIONS.contains("sem evoluções"))
        assertTrue(CommunityNormalPowerUpPack.LIMITATIONS.contains("TMs"))
    }

    @Test fun reviewedDateAndCommunitySourcesAreShown() {
        assertEquals(1792022400000L, CommunityNormalPowerUpPack.REVIEWED_EPOCH_MS)
        assertTrue(CommunityNormalPowerUpPack.SOURCE_URL.startsWith("https://"))
        assertTrue(CommunityNormalPowerUpPack.CROSS_CHECK_URL.startsWith("https://"))
        assertTrue(CommunityNormalPowerUpPack.SOURCE_NAME.contains("community"))
    }

    @Test fun explicitKnownInventoryMakesRealPlannerCalculateNormalPowerUps() {
        val report = quote(20.0, 40.0)
        assertEquals(CostQuoteGateStatus.VERIFIED_COST, report.status)
        assertTrue(report.canDisplayExactAmounts)
        assertEquals(225000, report.plan?.costs?.stardust)
        assertEquals(248, report.plan?.costs?.candyByFamily?.get(familyId))
        assertNull(report.plan?.costs?.xlCandyByFamily?.get(familyId))
    }

    @Test fun fullXlPowerUpQuotesCostsForAlreadyLevelFortyNormalPokemon() {
        val report = quote(40.0, 50.0)
        assertEquals(CostQuoteGateStatus.VERIFIED_COST, report.status)
        assertEquals(250000, report.plan?.costs?.stardust)
        assertEquals(296, report.plan?.costs?.xlCandyByFamily?.get(familyId))
    }

    @Test fun missingPlayerCandyKeepsCostQuoteUnavailable() {
        val report = quote(20.0, 40.0, full.copy(candy = null))
        assertEquals(CostQuoteGateStatus.MISSING_INVENTORY, report.status)
        assertFalse(report.canDisplayExactAmounts)
    }

    @Test fun explicitlyZeroResourcesShowsMissingQuantitiesAndDoesNotGuessAvailability() {
        val empty = UserResourceBalances(0, 0, 0, 0, 0, 0, 0)
        val report = quote(40.0, 50.0, empty)
        assertEquals(CostQuoteGateStatus.VERIFIED_COST, report.status)
        assertEquals(InvestmentPlanStatus.MISSING_RESOURCES, report.plan?.status)
        assertEquals(250000, report.plan?.missingResources
            ?.firstOrNull { it.kind.name == "STARDUST" }?.amount)
        assertEquals(296, report.plan?.missingResources
            ?.firstOrNull { it.kind.name == "XL_CANDY" }?.amount)
    }

    @Test fun absentCurrentLevelBlocksAnyQuoteEvenWithSourceReviewedPack() {
        val owned = OwnedPokemon("owned-2", species.speciesId, iv, createdAtEpochMs = 1L)
        val simulation = InvestmentTargetSimulator.simulate(owned, species, League.MASTER, "40")
        val report = InvestmentCostQuoteGate.evaluate(
            owned, simulation, listOf(species), emptyMap(),
            CommunityNormalPowerUpPack.pack,
            UserResourcePolicy.completeLedgerOrNull(full, familyId),
            InvestmentTarget(owned.id, League.MASTER, species.speciesId, 40.0)
        )
        assertEquals(CostQuoteGateStatus.BLOCKED_TARGET, report.status)
    }

    @Test fun restrictedSpecialFormsAndEternatusRequireOtherCostRules() {
        for (id in listOf("eternatus", "eternatus_eternamax", "normal_mega", "normal_primal")) {
            assertFalse(CommunityNormalPowerUpPack.isSupportedNormalPokemon(id, "Normal"))
        }
        assertFalse(CommunityNormalPowerUpPack.isSupportedNormalPokemon("normal", "Primal"))
        assertTrue(CommunityNormalPowerUpPack.isSupportedNormalPokemon("normal", "Normal"))
    }

    @Test fun sourceReviewedCostDoesNotClaimMetaRankOrTransferSafety() {
        assertTrue(CommunityNormalPowerUpPack.LIMITATIONS.contains("relevância no meta"))
        assertTrue(CommunityNormalPowerUpPack.LIMITATIONS.contains("Confirma sempre"))
        assertFalse(CommunityNormalPowerUpPack.LIMITATIONS.contains("deves investir"))
    }
}
