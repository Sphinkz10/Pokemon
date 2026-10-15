package com.rui.pvpgo

import com.rui.pvpgo.domain.BuildStatus
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.engine.League
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionPlanLeaguePolicyTest {
    private fun plan(
        id: String, owned: String = "one",
        league: League = League.GREAT, priority: Int = 50,
        status: BuildStatus = BuildStatus.IDEA,
        updated: Long = 100L
    ) = PvpBuildPlan(
        id = id, ownedPokemonId = owned, league = league,
        priority = priority, status = status,
        createdAtEpochMs = 50L, updatedAtEpochMs = updated
    )

    @Test fun greatGuideNeverPresentsUltraOrMasterPlans() {
        val items = listOf(
            plan("great", league = League.GREAT),
            plan("ultra", league = League.ULTRA, priority = 99),
            plan("master", league = League.MASTER, priority = 100)
        )
        val selected = CollectionPlanLeaguePolicy.preferredByOwned(items, League.GREAT)
        assertEquals("great", selected["one"]?.id)
    }

    @Test fun ultraGuideNeverPresentsHigherPriorityGreatPlan() {
        val items = listOf(
            plan("great", league = League.GREAT, priority = 100),
            plan("ultra", league = League.ULTRA, priority = 1)
        )
        assertEquals("ultra",
            CollectionPlanLeaguePolicy.preferredByOwned(items, League.ULTRA)["one"]?.id)
    }

    @Test fun aPlanForGreatStillAllowsCreatingNewPlanForMaster() {
        val items = listOf(plan("great", league = League.GREAT))
        assertTrue(CollectionPlanLeaguePolicy.hasSavedPlanInLeague("one", League.GREAT, items))
        assertFalse(CollectionPlanLeaguePolicy.hasSavedPlanInLeague("one", League.MASTER, items))
        assertTrue(CollectionPlanLeaguePolicy.preferredByOwned(items, League.MASTER).isEmpty())
    }

    @Test fun theSameOwnedSpecimenCanHaveIndependentPlansByLeague() {
        val items = League.entries.map { league ->
            plan(league.name, league = league)
        }
        League.entries.forEach { league ->
            assertEquals(league.name,
                CollectionPlanLeaguePolicy.preferredByOwned(items, league)["one"]?.id)
        }
    }

    @Test fun preferenceKeepsMultipleOwnedRecordsIsolated() {
        val items = listOf(
            plan("a", owned = "one"),
            plan("b", owned = "two"),
            plan("c", owned = "other", league = League.ULTRA)
        )
        val chosen = CollectionPlanLeaguePolicy.preferredByOwned(items, League.GREAT)
        assertEquals(2, chosen.size)
        assertEquals("a", chosen["one"]?.id)
        assertEquals("b", chosen["two"]?.id)
        assertFalse(chosen.containsKey("other"))
    }

    @Test fun nonArchivedPlanWinsOverHigherPriorityArchivedPlan() {
        val items = listOf(
            plan("retired", priority = 100, status = BuildStatus.ARCHIVED),
            plan("active", priority = 10, status = BuildStatus.IDEA)
        )
        assertEquals("active",
            CollectionPlanLeaguePolicy.preferredByOwned(items, League.GREAT)["one"]?.id)
    }

    @Test fun archivedPlanRemainsVisibleOnlyForReviewWhenAlone() {
        val items = listOf(plan("retired", status = BuildStatus.ARCHIVED))
        assertEquals(BuildStatus.ARCHIVED,
            CollectionPlanLeaguePolicy.preferredByOwned(items, League.GREAT)["one"]?.status)
    }

    @Test fun highestPriorityWinsBetweenLivePlans() {
        val items = listOf(plan("lower", priority = 1), plan("higher", priority = 80))
        assertEquals("higher",
            CollectionPlanLeaguePolicy.preferredByOwned(items, League.GREAT)["one"]?.id)
    }

    @Test fun newestUpdateBreaksEqualPriorityTie() {
        val items = listOf(
            plan("old", priority = 50, updated = 100L),
            plan("new", priority = 50, updated = 200L)
        )
        assertEquals("new",
            CollectionPlanLeaguePolicy.preferredByOwned(items, League.GREAT)["one"]?.id)
    }

    @Test fun blankOwnedIdIsRejectedBeforeAnyGuideSelection() {
        var rejected = false
        try {
            plan("bad", owned = "")
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(CollectionPlanLeaguePolicy.hasSavedPlanInLeague("", League.GREAT, emptyList()))
    }

    @Test fun anEmptyListHasNoSavedPlanForAnyLeague() {
        League.entries.forEach { league ->
            assertTrue(CollectionPlanLeaguePolicy.preferredByOwned(emptyList(), league).isEmpty())
            assertFalse(CollectionPlanLeaguePolicy.hasSavedPlanInLeague("one", league, emptyList()))
        }
    }

    @Test fun choosingPlanDoesNotMutateOriginalSavedData() {
        val items = listOf(plan("great"), plan("ultra", league = League.ULTRA))
        val before = items.toList()
        CollectionPlanLeaguePolicy.preferredByOwned(items, League.ULTRA)
        assertEquals(before, items)
    }
    @Test fun archivedOnlyPlanAllowsNewPlanForSameLeague() {
        val items = listOf(plan("old", league = League.GREAT, status = BuildStatus.ARCHIVED))
        assertTrue(CollectionPlanLeaguePolicy.hasSavedPlanInLeague("one", League.GREAT, items))
        assertFalse(CollectionPlanLeaguePolicy.hasNonArchivedPlanInLeague("one", League.GREAT, items))
        assertEquals("old", CollectionPlanLeaguePolicy.preferredByOwned(items, League.GREAT)["one"]?.id)
    }

    @Test fun livePlanStillPreventsDuplicateCreationEvenWithArchivedHistory() {
        val items = listOf(
            plan("old", status = BuildStatus.ARCHIVED, priority = 100),
            plan("live", status = BuildStatus.IDEA, priority = 1)
        )
        assertTrue(CollectionPlanLeaguePolicy.hasNonArchivedPlanInLeague("one", League.GREAT, items))
        assertEquals("live", CollectionPlanLeaguePolicy.preferredByOwned(items, League.GREAT)["one"]?.id)
    }

    @Test fun archivedUltraPlanDoesNotAffectActiveGreatPlan() {
        val items = listOf(
            plan("ultra-archived", league = League.ULTRA, status = BuildStatus.ARCHIVED),
            plan("great", league = League.GREAT)
        )
        assertTrue(CollectionPlanLeaguePolicy.hasNonArchivedPlanInLeague("one", League.GREAT, items))
        assertFalse(CollectionPlanLeaguePolicy.hasNonArchivedPlanInLeague("one", League.ULTRA, items))
        assertFalse(CollectionPlanLeaguePolicy.hasNonArchivedPlanInLeague("one", League.MASTER, items))
    }

    @Test fun archivedHistoryFromOtherSpecimenDoesNotPreventNewPlan() {
        val items = listOf(plan("retired", owned = "other", status = BuildStatus.ARCHIVED))
        assertFalse(CollectionPlanLeaguePolicy.hasNonArchivedPlanInLeague("one", League.GREAT, items))
        assertFalse(CollectionPlanLeaguePolicy.hasNonArchivedPlanInLeague("", League.GREAT, items))
    }

}
