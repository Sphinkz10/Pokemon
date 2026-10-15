package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Non-writing destination policy for the real Pokémon 360 LazyColumn. */
class SavedPlanFocusPolicyTest {
    @Test fun unknownSpecimenPointsToCpAndLevelEditor() {
        assertEquals(6, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.VERIFY_SPECIMEN, 20))
    }
    @Test fun incompatibleLeaguePointsToLeagueSelector() {
        assertEquals(2, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.CORRECT_LEAGUE, 20))
    }
    @Test fun unknownMovesPointsToActualMoveEditor() {
        assertEquals(7, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.REGISTER_MOVES, 20))
    }
    @Test fun missingResourcesPointsToBudgetInput() {
        assertEquals(4, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.REGISTER_RESOURCES, 20))
    }
    @Test fun agreedBudgetStillOnlySuggestsDraftTarget() {
        assertEquals(4, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.SET_INVESTMENT_TARGET, 20))
    }
    @Test fun reviewSpeciesPointsToIdentityHeader() {
        assertEquals(0, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.REVIEW_SPECIES, 20))
    }
    @Test fun reviewPlanTargetsBottomEditorWithoutHardcodingMoveCounts() {
        assertEquals(14, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.REVIEW_PLAN, 20))
        assertEquals(24, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.REVIEW_PLAN, 30))
    }
    @Test fun allDestinationsAreValidForMinimalOrLargeScreens() {
        SavedPlanNextStep.entries.forEach { step ->
            listOf(1, 4, 7, 14, 20, 150).forEach { count ->
                val index = SavedPlanFocusPolicy.targetIndex(step, count)
                assertTrue(index >= 0)
                assertTrue(index < count)
            }
        }
    }
    @Test fun allActionsHaveHumanReadableNonEmptyLabels() {
        SavedPlanNextStep.entries.forEach { step ->
            assertTrue(SavedPlanFocusPolicy.label(step).length >= 8)
        }
    }
    @Test fun explicitPlanReviewCannotAccidentallyShowBuildEditorInstead() {
        assertEquals(14, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.REVIEW_PLAN, 20))
        assertEquals(6, SavedPlanFocusPolicy.targetIndex(SavedPlanNextStep.VERIFY_SPECIMEN, 20))
    }
}
