package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayGoalPolicyTest {
    private fun actions(
        goal: TodayGoal,
        owned: Int = 0,
        pending: Int = 0,
        incomplete: Int = 0,
        teams: Int = 0,
        events: Boolean = false,
        stale: Boolean = true
    ) = TodayActionPolicy.decide(
        ownedCount = owned,
        pendingCount = pending,
        missingCpOrLevelCount = incomplete,
        savedTeamCount = teams,
        calendarAvailable = events,
        calendarStale = stale,
        goal = goal
    )

    @Test fun storedGoalParsingIsStableAndSafe() {
        assertEquals(TodayGoal.GREAT, TodayGoal.fromStored("GREAT"))
        assertEquals(TodayGoal.ULTRA, TodayGoal.fromStored("ULTRA"))
        assertEquals(TodayGoal.EVENTS, TodayGoal.fromStored("EVENTS"))
        assertEquals(TodayGoal.COLLECTION, TodayGoal.fromStored("COLLECTION"))
    }

    @Test fun unknownOrNullSavedGoalFallsBackToCollection() {
        assertEquals(TodayGoal.COLLECTION, TodayGoal.fromStored(null))
        assertEquals(TodayGoal.COLLECTION, TodayGoal.fromStored("UNKNOWN"))
        assertEquals(TodayGoal.COLLECTION, TodayGoal.fromStored(""))
    }

    @Test fun legacyDefaultStillMatchesCollectionGoal() {
        val old = TodayActionPolicy.decide(4, 1, 0, 2, true, false)
        val explicit = actions(TodayGoal.COLLECTION, owned = 4, pending = 1, teams = 2, events = true, stale = false)
        assertEquals(old, explicit)
    }

    @Test fun greatLeagueWithEmptyCollectionStartsAtCollection() {
        val first = actions(TodayGoal.GREAT).first()
        assertEquals(TodayDestination.COLLECTION, first.destination)
        assertTrue(first.title.contains("Grande Liga"))
        assertTrue(first.explanation.contains("não há elegibilidade confirmada"))
    }

    @Test fun ultraLeagueRequiresMissingCpAndLevelBeforeTeamPlanning() {
        val first = actions(TodayGoal.ULTRA, owned = 7, incomplete = 2).first()
        assertEquals(TodayDestination.COLLECTION, first.destination)
        assertTrue(first.title.contains("Ultra Liga"))
        assertTrue(first.explanation.contains("CP e nível"))
    }

    @Test fun leagueGoalWithOneOrTwoOwnedRequiresThreeExemplars() {
        val first = actions(TodayGoal.GREAT, owned = 2).first()
        assertEquals(TodayDestination.COLLECTION, first.destination)
        assertTrue(first.title.contains("três exemplares"))
    }

    @Test fun leagueGoalOpensTeamTabWithoutClaimingEligibility() {
        val first = actions(TodayGoal.GREAT, owned = 3).first()
        assertEquals(TodayDestination.TEAMS, first.destination)
        assertTrue(first.title.contains("Grande Liga"))
        assertTrue(first.explanation.contains("a elegibilidade ainda requer validação"))
    }

    @Test fun existingTeamsAreNotAssumedToBelongToGoalLeague() {
        val first = actions(TodayGoal.ULTRA, owned = 5, teams = 10).first()
        assertEquals(TodayDestination.TEAMS, first.destination)
        assertTrue(first.explanation.contains("seleciona Ultra Liga"))
        assertFalse(first.explanation.contains("10 equipa(s) da Ultra"))
    }

    @Test fun eventGoalPrioritizesRadarAndExplainsFreshness() {
        val result = actions(TodayGoal.EVENTS, owned = 3, events = true, stale = false)
        assertEquals(TodayDestination.RADAR, result.first().destination)
        assertTrue(result.first().explanation.contains("confirma a fonte"))
    }

    @Test fun staleCalendarNeverClaimsAnActiveEvent() {
        val result = actions(TodayGoal.EVENTS, owned = 3, events = true, stale = true)
        assertEquals(TodayDestination.RADAR, result.first().destination)
        assertTrue(result.first().explanation.contains("por confirmar"))
    }

    @Test fun pendingCapturesRemainVisibleButDoNotHideTheGoal() {
        val result = actions(TodayGoal.GREAT, owned = 3, pending = 12, events = true, stale = false)
        assertEquals(TodayDestination.TEAMS, result.first().destination)
        assertEquals(TodayDestination.COLLECTION, result[1].destination)
        assertEquals(3, result.size)
        assertTrue(result[1].title.contains("12"))
    }

    @Test fun eventsGoalCanOfferCollectionActionWithoutInventingPokemon() {
        val result = actions(TodayGoal.EVENTS, owned = 0, pending = 0)
        assertEquals(2, result.size)
        assertEquals(TodayDestination.COLLECTION, result[1].destination)
        assertTrue(result[1].title.contains("coleção"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImpossibleLocalDataEvenWhenGoalIsEvents() {
        actions(TodayGoal.EVENTS, owned = 1, incomplete = 2)
    }
}
