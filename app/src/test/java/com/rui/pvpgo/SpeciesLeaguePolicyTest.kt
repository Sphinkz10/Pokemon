package com.rui.pvpgo

import com.rui.pvpgo.engine.League
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Confirm every league can be selected and labels are never hardcoded to Great. */
class SpeciesLeaguePolicyTest {
    @Test fun includesEverySupportedLeagueExactlyOnce() {
        val options = SpeciesLeaguePolicy.selectable
        assertEquals(listOf(League.LITTLE, League.GREAT, League.ULTRA, League.MASTER), options)
        assertEquals(options.size, options.toSet().size)
    }

    @Test fun eachLeagueHasCorrectCap() {
        assertEquals("Até 500 CP", SpeciesLeaguePolicy.cpLimitLabel(League.LITTLE))
        assertEquals("Até 1 500 CP", SpeciesLeaguePolicy.cpLimitLabel(League.GREAT))
        assertEquals("Até 2 500 CP", SpeciesLeaguePolicy.cpLimitLabel(League.ULTRA))
        assertEquals("Sem limite CP", SpeciesLeaguePolicy.cpLimitLabel(League.MASTER))
    }

    @Test fun selectedLeagueTitlesHaveConsistentNames() {
        for (option in SpeciesLeaguePolicy.selectable) {
            assertTrue(SpeciesLeaguePolicy.title(option).endsWith(" League"))
            assertTrue(SpeciesLeaguePolicy.title(option).startsWith(SpeciesLeaguePolicy.shortLabel(option)))
        }
    }

    @Test fun unavailableRanksStayUnavailable() {
        assertEquals("—", SpeciesLeaguePolicy.rankLabel(null))
        assertEquals("Ranking indisponível", SpeciesLeaguePolicy.statusLabel(null, false))
        assertEquals("A calcular ranking…", SpeciesLeaguePolicy.statusLabel(null, true))
        assertEquals("#1", SpeciesLeaguePolicy.rankLabel(1))
    }

    @Test fun comparisonNeedsTwoDifferentRealIds() {
        assertTrue(SpeciesLeaguePolicy.validSelection("owned-a", "owned-b"))
        assertFalse(SpeciesLeaguePolicy.validSelection(null, "owned-b"))
        assertFalse(SpeciesLeaguePolicy.validSelection("owned-a", "owned-a"))
    }
}
