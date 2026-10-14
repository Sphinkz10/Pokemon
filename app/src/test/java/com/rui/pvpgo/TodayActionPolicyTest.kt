package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayActionPolicyTest {
    private fun actions(
        owned: Int = 0, pending: Int = 0, incomplete: Int = 0,
        teams: Int = 0, events: Boolean = false, stale: Boolean = true
    ) = TodayActionPolicy.decide(owned, pending, incomplete, teams, events, stale)

    @Test fun newPlayerReceivesUsefulActionsWithoutPretendPvpPredictions() {
        val result = actions()
        assertEquals(2, result.size)
        assertEquals("Começar a tua coleção", result[0].title)
        assertEquals(TodayDestination.COLLECTION, result[0].destination)
        assertEquals(TodayDestination.RADAR, result[1].destination)
        assertTrue(result[1].explanation.contains("por atualizar"))
    }

    @Test fun pendingCapturesTakePriorityOverSpeciesSearch() {
        val result = actions(pending = 3)
        assertTrue(result.first().title.contains("3"))
        assertTrue(result.first().explanation.contains("por confirmar"))
    }

    @Test fun incompleteCollectionRequiresVerificationNotBattleReadiness() {
        val result = actions(owned = 4, incomplete = 2)
        assertEquals("Completar 2 exemplar(es)", result.first().title)
        assertFalse(result.any { it.explanation.contains("elegível para combater") })
    }

    @Test fun anIncompleteTeamStillOffersConstructionNotConfirmedEligibility() {
        val result = actions(owned = 3, incomplete = 1, teams = 0)
        val team = result.first { it.destination == TodayDestination.TEAMS }
        assertTrue(team.title.contains("Construir"))
        assertTrue(team.explanation.contains("confirmada"))
    }

    @Test fun existingTeamsAreAvailableButNotCalledMetaWinners() {
        val result = actions(owned = 4, teams = 2)
        assertTrue(result.first().title.contains("Rever"))
        assertTrue(result.first().explanation.contains("2 equipa(s)"))
        assertFalse(result.first().explanation.contains("ganhar"))
    }

    @Test fun freshCalendarIsTreatedAsDatesToVerify() {
        val result = actions(events = true, stale = false)
        val radar = result.first { it.destination == TodayDestination.RADAR }
        assertTrue(radar.title.contains("próximos"))
        assertTrue(radar.explanation.contains("confirma a fonte"))
    }

    @Test fun staleCalendarDoesNotInventEventAvailability() {
        val result = actions(events = true, stale = true)
        assertTrue(result.last().explanation.contains("por atualizar"))
    }

    @Test fun neverMoreThanThreeActionsOrDuplicateNavigationSideEffects() {
        val result = actions(owned = 7, pending = 4, incomplete = 3, teams = 2, events = true, stale = false)
        assertEquals(3, result.size)
        assertEquals(listOf(TodayDestination.COLLECTION, TodayDestination.COLLECTION, TodayDestination.TEAMS),
            result.map { it.destination })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCountsAreRejected() {
        actions(owned = 2, incomplete = 3)
    }
}
