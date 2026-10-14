package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayGoFilterScopeTest {
    @Test fun invalidSavedScopeFallsBackToStandard() {
        assertEquals(GoFilterScope.STANDARD, GoFilterScope.fromStored(null))
        assertEquals(GoFilterScope.STANDARD, GoFilterScope.fromStored("NOT_SAVED"))
    }

    @Test fun everyScopeRestoresByStableName() {
        for (scope in GoFilterScope.entries)
            assertEquals(scope, GoFilterScope.fromStored(scope.name))
    }

    @Test fun standardScopeDoesNotChangeExistingQueries() {
        for (goal in TodayGoal.entries)
            assertEquals(TodayJourneyPolicy.suggestion(goal),
                TodayJourneyPolicy.scopedSuggestion(goal, GoFilterScope.STANDARD))
    }

    @Test fun wideningGreatAndUltraRelaxStatThresholdsWithoutCPGuess() {
        for (goal in listOf(TodayGoal.GREAT, TodayGoal.ULTRA)) {
            val broad = TodayJourneyPolicy.scopedSuggestion(goal, GoFilterScope.BROAD)
            val focused = TodayJourneyPolicy.scopedSuggestion(goal, GoFilterScope.FOCUSED)
            assertEquals("0-2attack&2-4defense&2-4hp", broad.filter)
            assertEquals("0attack&4defense&4hp", focused.filter)
            assertFalse(broad.filter.contains("cp"))
            assertTrue(broad.limitation.contains("não confirma IVs"))
        }
    }

    @Test fun ivsGoalKeepsCarefulSearchWarningsInEveryScope() {
        for (scope in GoFilterScope.entries) {
            val result = TodayJourneyPolicy.scopedSuggestion(TodayGoal.IVS, scope)
            assertTrue(result.limitation.contains("não calcula ranking"))
            assertTrue(result.filter.isNotBlank())
        }
    }

    @Test fun masterCanWidenToThreeAndFourStarsWithoutPretendingToBeMeta() {
        assertEquals("3*,4*", TodayJourneyPolicy.scopedSuggestion(TodayGoal.MASTER, GoFilterScope.BROAD).filter)
        assertEquals("4*", TodayJourneyPolicy.scopedSuggestion(TodayGoal.MASTER, GoFilterScope.FOCUSED).filter)
        assertFalse(TodayJourneyPolicy.canRefine(TodayGoal.MASTER, GoFilterScope.STANDARD))
    }

    @Test fun collectionCanWidenAcrossAllAppraisalBands() {
        assertEquals("0-4*", TodayJourneyPolicy.scopedSuggestion(TodayGoal.COLLECTION, GoFilterScope.BROAD).filter)
        assertEquals("4*", TodayJourneyPolicy.scopedSuggestion(TodayGoal.COLLECTION, GoFilterScope.FOCUSED).filter)
    }

    @Test fun eventSearchWidensDaysWithoutClaimingEventAttendance() {
        val broad = TodayJourneyPolicy.scopedSuggestion(TodayGoal.EVENTS, GoFilterScope.BROAD)
        val focused = TodayJourneyPolicy.scopedSuggestion(TodayGoal.EVENTS, GoFilterScope.FOCUSED)
        assertEquals("age0-30", broad.filter)
        assertEquals("age0-1", focused.filter)
        assertTrue(broad.limitation.contains("não identifica participações"))
    }

    @Test fun narrowingMovesThroughStandardBeforeFocused() {
        assertEquals(GoFilterScope.STANDARD, TodayJourneyPolicy.refine(TodayGoal.GREAT, GoFilterScope.BROAD))
        assertEquals(GoFilterScope.FOCUSED, TodayJourneyPolicy.refine(TodayGoal.GREAT, GoFilterScope.STANDARD))
        assertEquals(GoFilterScope.FOCUSED, TodayJourneyPolicy.refine(TodayGoal.GREAT, GoFilterScope.FOCUSED))
    }

    @Test fun wideningMovesThroughStandardBeforeBroad() {
        assertEquals(GoFilterScope.STANDARD, TodayJourneyPolicy.broaden(GoFilterScope.FOCUSED))
        assertEquals(GoFilterScope.BROAD, TodayJourneyPolicy.broaden(GoFilterScope.STANDARD))
        assertEquals(GoFilterScope.BROAD, TodayJourneyPolicy.broaden(GoFilterScope.BROAD))
    }

    @Test fun controlsRespectBoundsWithoutHiddenSearchGuessing() {
        assertFalse(TodayJourneyPolicy.canBroaden(GoFilterScope.BROAD))
        assertTrue(TodayJourneyPolicy.canBroaden(GoFilterScope.STANDARD))
        assertFalse(TodayJourneyPolicy.canRefine(TodayGoal.COLLECTION, GoFilterScope.FOCUSED))
        assertTrue(TodayJourneyPolicy.canRefine(TodayGoal.COLLECTION, GoFilterScope.STANDARD))
    }

    @Test fun allVariantsUseCopyableOperatorSyntaxAndWarnings() {
        val allowed = Regex("^[a-zA-Z0-9*&,\\-]+$")
        for (goal in TodayGoal.entries) for (scope in GoFilterScope.entries) {
            val sample = TodayJourneyPolicy.scopedSuggestion(goal, scope)
            assertTrue(goal.name + scope.name, allowed.matches(sample.filter))
            assertTrue(goal.name + scope.name, sample.limitation.isNotBlank())
            assertFalse(goal.name + scope.name, sample.purpose.contains("garant"))
        }
    }
}
