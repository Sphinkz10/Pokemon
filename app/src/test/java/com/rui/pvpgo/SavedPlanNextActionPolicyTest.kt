package com.rui.pvpgo

import com.rui.pvpgo.domain.BuildStatus
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.OwnedPokemonField
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlanNextActionPolicyTest {
    private val species = PokemonSpecies(
        dex = 25, speciesId = "next-25", familyId = "next-family",
        name = "Pikachu de teste", baseAttack = 105,
        baseDefense = 115, baseStamina = 120
    )
    private val iv = IvSpread(0, 15, 15)
    private val cp = PokemonMath.cp(species, iv, 20.0)
    private val complete = OwnedPokemon(
        id = "next-owned", speciesId = species.speciesId, iv = iv,
        cp = cp, level = 20.0, fastMoveId = "thunder-fast",
        chargedMoveIds = listOf("thunder-charged"), createdAtEpochMs = 100L
    )
    private val plan = PvpBuildPlan(
        id = "next-plan", ownedPokemonId = complete.id,
        league = League.MASTER, createdAtEpochMs = 100L
    )
    private val balances = UserResourceBalances(
        stardust = 500000, candy = 200, xlCandy = 0
    )

    private fun decide(
        owned: OwnedPokemon = complete,
        speciesForOwned: PokemonSpecies? = species,
        savedPlan: PvpBuildPlan = plan,
        resources: UserResourceBalances = balances
    ) = SavedPlanNextActionPolicy.next(owned, speciesForOwned, savedPlan, resources)

    @Test fun mismatchedPlanCannotBeSuggestedAsUsable() {
        assertEquals(SavedPlanNextStep.REVIEW_PLAN,
            decide(savedPlan = plan.copy(ownedPokemonId = "different")).step)
    }

    @Test fun archivedPlanMustBeExplicitlyReviewed() {
        assertEquals(SavedPlanNextStep.REVIEW_PLAN,
            decide(savedPlan = plan.copy(status = BuildStatus.ARCHIVED)).step)
    }

    @Test fun missingOrMismatchedSpeciesRequiresIdentityReview() {
        assertEquals(SavedPlanNextStep.REVIEW_SPECIES, decide(speciesForOwned = null).step)
        assertEquals(SavedPlanNextStep.REVIEW_SPECIES,
            decide(speciesForOwned = species.copy(speciesId = "different")).step)
    }

    @Test fun unknownCpOrLevelNeverBecomesAnEstimatedSavedValue() {
        val unknownCp = decide(owned = complete.copy(cp = null))
        assertEquals(SavedPlanNextStep.VERIFY_SPECIMEN, unknownCp.step)
        assertTrue(unknownCp.explanation.contains("valores calculados"))
        assertEquals(SavedPlanNextStep.VERIFY_SPECIMEN,
            decide(owned = complete.copy(level = null)).step)
    }

    @Test fun uncertainButRecordedValuesStillNeedManualConfirmation() {
        assertEquals(SavedPlanNextStep.VERIFY_SPECIMEN, decide(
            owned = complete.copy(uncertainFields = setOf(OwnedPokemonField.CP))
        ).step)
    }

    @Test fun inconsistentCpOrGenericLeagueViolationCannotGiveGreenLight() {
        assertEquals(SavedPlanNextStep.CORRECT_LEAGUE,
            decide(owned = complete.copy(cp = cp + 1)).step)
        assertEquals(SavedPlanNextStep.CORRECT_LEAGUE,
            decide(savedPlan = plan.copy(league = League.LITTLE),
                owned = complete.copy(level = 50.0,
                    cp = PokemonMath.cp(species, iv, 50.0))).step)
    }

    @Test fun unknownActualMovesNeedVerificationBeforeBudget() {
        assertEquals(SavedPlanNextStep.REGISTER_MOVES,
            decide(owned = complete.copy(fastMoveId = null)).step)
        assertEquals(SavedPlanNextStep.REGISTER_MOVES,
            decide(owned = complete.copy(chargedMoveIds = emptyList())).step)
    }

    @Test fun unknownRelevantResourcesAreListedWithoutInventingZero() {
        val result = decide(resources = UserResourceBalances(
            stardust = 0, candy = null, xlCandy = null
        ))
        assertEquals(SavedPlanNextStep.REGISTER_RESOURCES, result.step)
        assertTrue(result.explanation.contains("Candy, XL"))
        assertFalse(result.explanation.contains("Stardust, Candy"))
        assertTrue(result.explanation.contains("desconhecido não é zero"))
    }

    @Test fun threeExplicitKnownBalancesDoNotRequireUnrelatedTmsForNextAction() {
        val result = decide(resources = balances)
        assertEquals(SavedPlanNextStep.SET_INVESTMENT_TARGET, result.step)
        assertTrue(result.explanation.contains("não prova"))
        assertTrue(result.explanation.contains("custos"))
    }

    @Test fun zeroResourcesDoNotMeanAffordabilityOrPermissionToSpend() {
        val result = decide(resources = UserResourceBalances(0, 0, 0))
        assertEquals(SavedPlanNextStep.SET_INVESTMENT_TARGET, result.step)
        assertFalse(result.title.contains("Pronto"))
        assertTrue(SavedPlanNextActionPolicy.CAVEAT.contains("não significa investimento aprovado"))
    }
}
