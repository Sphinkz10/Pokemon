package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayJourneyPolicyTest {
    @Test fun everyGoalHasCopyableAndNonemptySuggestion() {
        for (goal in TodayGoal.entries) {
            val suggestion = TodayJourneyPolicy.suggestion(goal)
            assertTrue(goal.name, suggestion.filter.isNotBlank())
            assertTrue(goal.name, suggestion.purpose.isNotBlank())
            assertTrue(goal.name, suggestion.limitation.isNotBlank())
            assertFalse(goal.name, suggestion.filter.contains(" "))
        }
    }

    @Test fun greatAndUltraOfferOnlyCandidatesNotEligibility() {
        for (goal in listOf(TodayGoal.GREAT, TodayGoal.ULTRA)) {
            val suggestion = TodayJourneyPolicy.suggestion(goal)
            assertEquals("0-1attack&3-4defense&3-4hp", suggestion.filter)
            assertTrue(suggestion.requiresEnglishGoTerms)
            assertTrue(suggestion.limitation.contains("Não transferir"))
        }
    }

    @Test fun leagueCapIsNotUsedToDiscardUpgradeablePokemon() {
        val suggestions = listOf(TodayGoal.GREAT, TodayGoal.ULTRA).map(TodayJourneyPolicy::suggestion)
        assertTrue(suggestions.none { it.filter.contains("cp") })
    }

    @Test fun masterDoesNotInferRankFromPerfectAppraisal() {
        val suggestion = TodayJourneyPolicy.suggestion(TodayGoal.MASTER)
        assertEquals("4*", suggestion.filter)
        assertTrue(suggestion.limitation.contains("Exclui bons candidatos"))
        assertFalse(suggestion.requiresEnglishGoTerms)
    }

    @Test fun collectionReviewDoesNotHideTransferRisk() {
        val suggestion = TodayJourneyPolicy.suggestion(TodayGoal.COLLECTION)
        assertEquals("3*,4*", suggestion.filter)
        assertTrue(suggestion.limitation.contains("Não usar para decidir transferências"))
    }

    @Test fun eventSearchIsOnlyARecentCaptureHeuristic() {
        val suggestion = TodayJourneyPolicy.suggestion(TodayGoal.EVENTS)
        assertEquals("age0-7", suggestion.filter)
        assertTrue(suggestion.limitation.contains("não identifica") ||
            suggestion.limitation.contains("não confirma"))
        assertTrue(suggestion.requiresEnglishGoTerms)
    }

    @Test fun pvpIvSearchIsLabeledAsTriagingOnly() {
        val suggestion = TodayJourneyPolicy.suggestion(TodayGoal.IVS)
        assertTrue(suggestion.limitation.contains("não calcula ranking"))
    }

    @Test fun persistedUnknownStageFallsBackToCopy() {
        assertEquals(TodayJourneyStage.COPY_FILTER, TodayJourneyStage.fromStored(null))
        assertEquals(TodayJourneyStage.COPY_FILTER, TodayJourneyStage.fromStored("NOT_VALID"))
    }

    @Test fun persistedStagesResumeExactly() {
        for (stage in TodayJourneyStage.entries) {
            assertEquals(stage, TodayJourneyStage.fromStored(stage.name))
        }
    }

    @Test fun copyingAdvancesOnlyFromSearchStage() {
        assertEquals(
            TodayJourneyStage.REVIEW_CANDIDATES,
            TodayJourneyPolicy.afterCopy(TodayJourneyStage.COPY_FILTER)
        )
        assertEquals(
            TodayJourneyStage.NEXT_ACTION,
            TodayJourneyPolicy.afterCopy(TodayJourneyStage.NEXT_ACTION)
        )
    }

    @Test fun collectionReviewRequiresExplicitUserConfirmation() {
        assertEquals(
            TodayJourneyStage.COPY_FILTER,
            TodayJourneyPolicy.afterUserReview(TodayJourneyStage.COPY_FILTER)
        )
        assertEquals(
            TodayJourneyStage.NEXT_ACTION,
            TodayJourneyPolicy.afterUserReview(TodayJourneyStage.REVIEW_CANDIDATES)
        )
    }

    @Test fun resetReopensSearchStep() {
        assertEquals(TodayJourneyStage.COPY_FILTER, TodayJourneyPolicy.reset())
    }

    @Test fun allProgressLabelsAreMonotonicAndSmall() {
        assertEquals(listOf(1, 2, 3), TodayJourneyStage.entries.map { it.index })
    }
}
