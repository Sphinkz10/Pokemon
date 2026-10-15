package com.rui.pvpgo

import com.rui.pvpgo.domain.InvestmentCostPack
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.ResourceLedger
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

class AffordableLevelPolicyTest {
    private val normal = PokemonSpecies(
        dex = 25, speciesId = "normal-25", name = "Teste",
        familyId = "family-25", baseAttack = 100, baseDefense = 120, baseStamina = 130
    )
    private val bulky = normal.copy(
        dex = 26, speciesId = "bulky-26",
        baseAttack = 180, baseDefense = 180, baseStamina = 180
    )
    private val iv = IvSpread(0, 15, 15)
    private val pack = CommunityNormalPowerUpPack.pack

    private fun pokemon(level: Double = 20.0, species: PokemonSpecies = normal) =
        OwnedPokemon(
            id = "owned-25", speciesId = species.speciesId, iv = iv,
            level = level, cp = PokemonMath.cp(species, iv, level),
            createdAtEpochMs = 1L
        )

    private fun inventory(
        stardust: Int = 600000, candy: Int = 500, xl: Int = 500,
        species: PokemonSpecies = normal
    ): ResourceLedger = requireNotNull(UserResourcePolicy.completeLedgerOrNull(
        UserResourceBalances(stardust, candy, xl, 0, 0, 0, 0),
        species.familyId ?: species.speciesId
    ))

    private fun find(
        player: OwnedPokemon = pokemon(),
        species: PokemonSpecies? = normal,
        league: League = League.MASTER,
        consent: Boolean = true,
        source: InvestmentCostPack? = pack,
        ledger: ResourceLedger? = inventory()
    ) = AffordableLevelPolicy.advise(player, species, league, consent, source, ledger)

    @Test fun consentIsExplicitlyRequired() {
        val result = find(consent = false)
        assertEquals(AffordableLevelStatus.CONSENT_REQUIRED, result.status)
        assertFalse(result.canApply)
        assertNull(result.affordablePlan)
    }

    @Test fun missingCpBlocksAffordability() {
        val result = find(player = pokemon().copy(cp = null))
        assertEquals(AffordableLevelStatus.NO_ELIGIBLE_TARGET, result.status)
        assertFalse(result.canApply)
    }

    @Test fun missingLevelBlocksAffordability() {
        val result = find(player = pokemon().copy(level = null))
        assertEquals(AffordableLevelStatus.NO_ELIGIBLE_TARGET, result.status)
    }

    @Test fun mismatchedCatalogFormBlocksAffordability() {
        val result = find(species = normal.copy(speciesId = "different"))
        assertEquals(AffordableLevelStatus.NO_ELIGIBLE_TARGET, result.status)
        assertNull(result.affordableLevel)
    }

    @Test fun inconsistentCpBlocksWithoutReplacingRecordedObservation() {
        val result = find(player = pokemon().copy(cp = pokemon().cp!! + 1))
        assertEquals(AffordableLevelStatus.NO_ELIGIBLE_TARGET, result.status)
        assertFalse(result.canApply)
    }

    @Test fun overCapExistingPokemonNeverGetsAutomaticDeLevel() {
        val candidate = pokemon(level = 30.0, species = bulky)
        val result = find(
            player = candidate, species = bulky, league = League.LITTLE,
            ledger = inventory(species = bulky)
        )
        assertEquals(AffordableLevelStatus.NO_ELIGIBLE_TARGET, result.status)
        assertNull(result.affordableLevel)
    }

    @Test fun noVerifiedPackMeansNoFinancialAdvice() {
        val result = find(source = null)
        assertEquals(AffordableLevelStatus.UNVERIFIED_REFERENCE, result.status)
        assertFalse(result.canApply)
    }

    @Test fun replacedCostPackWithDifferentHashMustFailEvenIfInternallyValid() {
        val forged = com.rui.pvpgo.engine.InvestmentCostPackValidator.build(
            CommunityNormalPowerUpPack.ID, CommunityNormalPowerUpPack.VERSION,
            CommunityNormalPowerUpPack.SOURCE_NAME, CommunityNormalPowerUpPack.SOURCE_VERSION,
            CommunityNormalPowerUpPack.REVIEWED_EPOCH_MS,
            CommunityNormalPowerUpPack.steps.drop(1),
            emptyList(), emptyList()
        )
        val result = find(source = forged)
        assertEquals(AffordableLevelStatus.UNVERIFIED_REFERENCE, result.status)
        assertFalse(result.canApply)
    }

    @Test fun forgedProvenanceWithSameContentHashMustFail() {
        val forged = pack.copy(sourceName = "unverified replacement")
        assertEquals(AffordableLevelStatus.UNVERIFIED_REFERENCE, find(source = forged).status)
    }

    @Test fun missingInventoryIsNotInterpretedAsZero() {
        val result = find(ledger = null)
        assertEquals(AffordableLevelStatus.UNKNOWN_BALANCES, result.status)
        assertFalse(result.canApply)
    }

    @Test fun inventoryWithoutFamilyCandyIsNotAnExplicitZero() {
        val value = ResourceLedger(stardust = 500000, xlCandyByFamily = mapOf("family-25" to 500))
        assertEquals(AffordableLevelStatus.UNKNOWN_BALANCES, find(ledger = value).status)
    }

    @Test fun inventoryWithoutFamilyXlIsNotAnExplicitZero() {
        val value = ResourceLedger(stardust = 500000, candyByFamily = mapOf("family-25" to 500))
        assertEquals(AffordableLevelStatus.UNKNOWN_BALANCES, find(ledger = value).status)
    }

    @Test fun unknownFamilyDoesNotBorrowCandyFromAnotherEvolutionLine() {
        val value = inventory(species = normal.copy(familyId = "other-family"))
        assertEquals(AffordableLevelStatus.UNKNOWN_BALANCES, find(ledger = value).status)
    }

    @Test fun luckyPokemonCannotUseNormalBudget() {
        assertEquals(
            AffordableLevelStatus.UNSUPPORTED_COST_MODIFIER,
            find(player = pokemon().copy(isLucky = true)).status
        )
    }

    @Test fun shadowPokemonCannotUseNormalBudget() {
        assertEquals(
            AffordableLevelStatus.UNSUPPORTED_COST_MODIFIER,
            find(player = pokemon().copy(isShadow = true)).status
        )
    }

    @Test fun purifiedPokemonCannotUseNormalBudget() {
        assertEquals(
            AffordableLevelStatus.UNSUPPORTED_COST_MODIFIER,
            find(player = pokemon().copy(isPurified = true)).status
        )
    }

    @Test fun specialMegaAndPrimalFormsAreBlockedByExistingCapGate() {
        val mega = normal.copy(speciesId = "normal-mega", tags = setOf("mega"))
        val primal = normal.copy(speciesId = "normal-primal", tags = setOf("primal"))
        for (form in listOf(mega, primal)) {
            val result = find(
                player = pokemon(species = form), species = form,
                ledger = inventory(species = form)
            )
            assertFalse(result.canApply)
            assertNull(result.affordableLevel)
        }
    }

    @Test fun enoughFundsGetFullLeagueGoalAtLevelFifty() {
        val result = find()
        assertEquals(AffordableLevelStatus.AVAILABLE, result.status)
        assertTrue(result.canApply)
        assertEquals(50.0, result.goalLevel!!, 0.0)
        assertEquals(50.0, result.affordableLevel!!, 0.0)
        assertEquals(60, result.affordableSteps)
        assertEquals(475000, result.affordablePlan?.costs?.stardust)
        assertEquals(248, result.affordablePlan?.costs?.candyByFamily?.get("family-25"))
        assertEquals(296, result.affordablePlan?.costs?.xlCandyByFamily?.get("family-25"))
        assertTrue(result.goalShortages.isEmpty())
        assertTrue(result.nextStepShortages.isEmpty())
    }

    @Test fun zeroEverythingStillOffersTheCurrentLevelWithNoPowerups() {
        val result = find(ledger = inventory(stardust = 0, candy = 0, xl = 0))
        assertEquals(AffordableLevelStatus.AVAILABLE, result.status)
        assertEquals(20.0, result.affordableLevel!!, 0.0)
        assertEquals(0, result.affordableSteps)
        assertEquals(0, result.affordablePlan?.costs?.stardust)
        assertTrue(result.nextStepShortages.isNotEmpty())
        assertTrue(result.goalShortages.isNotEmpty())
    }

    @Test fun firstPowerUpIsAffordableWithExactStepCost() {
        val first = CommunityNormalPowerUpPack.steps.single { it.fromLevel == 20.0 }
        val result = find(ledger = inventory(stardust = first.stardust, candy = first.candy, xl = 0))
        assertEquals(AffordableLevelStatus.AVAILABLE, result.status)
        assertEquals(20.5, result.affordableLevel!!, 0.0)
        assertEquals(1, result.affordableSteps)
        assertEquals(first.stardust, result.affordablePlan?.costs?.stardust)
        assertTrue(result.nextStepShortages.isNotEmpty())
    }

    @Test fun dustShortageLimitsOtherwiseUnlimitedCandy() {
        val result = find(ledger = inventory(stardust = 1000, candy = 500, xl = 500))
        assertEquals(20.0, result.affordableLevel!!, 0.0)
        assertTrue(result.nextStepShortages.any { it.kind.name == "STARDUST" })
    }

    @Test fun candyShortageLimitsOtherwiseUnlimitedDust() {
        val result = find(ledger = inventory(stardust = 600000, candy = 0, xl = 500))
        assertEquals(20.0, result.affordableLevel!!, 0.0)
        assertTrue(result.nextStepShortages.any { it.kind.name == "CANDY" })
    }

    @Test fun xlShortageStopsAtFortyWhenRegularCandyAndDustAreEnough() {
        val result = find(ledger = inventory(stardust = 600000, candy = 500, xl = 0))
        assertEquals(40.0, result.affordableLevel!!, 0.0)
        assertEquals(225000, result.affordablePlan?.costs?.stardust)
        assertEquals(40, result.affordableSteps)
        assertTrue(result.nextStepShortages.any { it.kind.name == "XL_CANDY" })
    }

    @Test fun existingLevelFiftyMeansNoUpgradeIsNeeded() {
        val result = find(
            player = pokemon(level = 50.0),
            ledger = inventory(stardust = 0, candy = 0, xl = 0)
        )
        assertTrue(result.canApply)
        assertEquals(50.0, result.affordableLevel!!, 0.0)
        assertEquals(0, result.affordableSteps)
        assertTrue(result.goalShortages.isEmpty())
    }

    @Test fun greatLeagueBudgetNeverExceedsCpCap() {
        val player = pokemon(level = 20.0, species = bulky)
        val result = find(
            player = player, species = bulky, league = League.GREAT,
            ledger = inventory(species = bulky)
        )
        assertTrue(result.canApply)
        assertTrue(result.affordableCp!! <= 1500)
        assertTrue(result.affordableLevel!! >= 20.0)
        assertTrue(result.affordableLevel!! <= result.goalLevel!!)
    }

    @Test fun affordabilityCanReachButNeverBypassCapGoal() {
        val player = pokemon(level = 20.0, species = bulky)
        val result = find(
            player = player, species = bulky, league = League.GREAT,
            ledger = inventory(species = bulky, stardust = 0, candy = 0, xl = 0)
        )
        assertTrue(result.canApply)
        assertEquals(20.0, result.affordableLevel!!, 0.0)
        assertTrue(result.goalLevel!! >= result.affordableLevel!!)
        assertTrue(result.goalShortages.isNotEmpty())
    }

    @Test fun nextIncrementDeficitDoesNotClaimCostWasPaidOrDeductResources() {
        val ledger = inventory(stardust = 0, candy = 0, xl = 0)
        val before = ledger.copy()
        val result = find(ledger = ledger)
        assertTrue(result.canApply)
        assertEquals(before, ledger)
        assertTrue(result.nextStepShortages.any { (it.amount ?: 0) > 0 })
        assertTrue(AffordableLevelPolicy.LIMITATION.contains("Não gasta nem reserva recursos"))
    }

    @Test fun affordabilityNeverClaimsMetaOrSpecialCupEligibility() {
        assertTrue(AffordableLevelPolicy.LIMITATION.contains("copas e eventos"))
        assertTrue(AffordableLevelPolicy.LIMITATION.contains("qualidade PvP"))
        assertFalse(AffordableLevelPolicy.LIMITATION.contains("recomendação de equipa aprovada"))
    }
}
