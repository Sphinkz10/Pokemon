package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No static IV pattern is allowed to imply meta quality or league eligibility. */
class CollectionGuidePolicyTest {
    private val saved = OwnedPokemon(
        id = "known", speciesId = "pikachu", iv = IvSpread(0, 15, 15),
        cp = 490, level = 20.0, createdAtEpochMs = 10L
    )
    private val unknown = saved.copy(id = "missing", cp = null, level = null)

    @Test fun allDoesNotFilterTheCollection() {
        assertTrue(CollectionGuidePolicy.matches(CollectionGuideStep.ALL, saved, false, false))
        assertTrue(CollectionGuidePolicy.matches(CollectionGuideStep.ALL, unknown, false, false))
    }

    @Test fun reviewOnlyListsUnknownAndUncertainSpecimens() {
        assertTrue(CollectionGuidePolicy.matches(CollectionGuideStep.REVIEW, unknown, true, false))
        assertFalse(CollectionGuidePolicy.matches(CollectionGuideStep.REVIEW, saved, true, false))
    }

    @Test fun reviewTreatsMissingCpAloneAsNeedingConfirmation() {
        assertTrue(CollectionGuidePolicy.matches(
            CollectionGuideStep.REVIEW, saved.copy(cp = null), true, false
        ))
    }

    @Test fun reviewTreatsMissingLevelAloneAsNeedingConfirmation() {
        assertTrue(CollectionGuidePolicy.matches(
            CollectionGuideStep.REVIEW, saved.copy(level = null), true, false
        ))
    }

    @Test fun compareRequiresKnownCatalogSpecies() {
        assertTrue(CollectionGuidePolicy.matches(CollectionGuideStep.COMPARE, saved, true, false))
        assertFalse(CollectionGuidePolicy.matches(CollectionGuideStep.COMPARE, saved, false, false))
    }

    @Test fun zeroAttackDoesNotBecomeAnAutomaticQualityDecision() {
        assertTrue(CollectionGuidePolicy.matches(CollectionGuideStep.COMPARE, saved, true, false))
        assertTrue(CollectionGuidePolicy.matches(
            CollectionGuideStep.COMPARE,
            saved.copy(iv = IvSpread(15, 15, 15)), true, false
        ))
        assertTrue(CollectionGuideStep.COMPARE.description.contains("não é garantia"))
    }

    @Test fun compareWithMissingCpRemainsInformationalNotAffordable() {
        assertTrue(CollectionGuidePolicy.matches(
            CollectionGuideStep.COMPARE, unknown, true, false
        ))
    }

    @Test fun planStageRequiresAnActualSavedPlan() {
        assertFalse(CollectionGuidePolicy.matches(CollectionGuideStep.PLAN, saved, true, false))
        assertTrue(CollectionGuidePolicy.matches(CollectionGuideStep.PLAN, saved, true, true))
    }

    @Test fun leagueComparisonActionOnlyExistsAtComparisonStep() {
        assertTrue(CollectionGuidePolicy.canOpenIvComparison(CollectionGuideStep.COMPARE, true))
        assertFalse(CollectionGuidePolicy.canOpenIvComparison(CollectionGuideStep.COMPARE, false))
        assertFalse(CollectionGuidePolicy.canOpenIvComparison(CollectionGuideStep.REVIEW, true))
        assertFalse(CollectionGuidePolicy.canOpenIvComparison(CollectionGuideStep.PLAN, true))
    }
}
