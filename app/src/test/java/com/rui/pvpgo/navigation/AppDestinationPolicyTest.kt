package com.rui.pvpgo.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationPolicyTest {

    @Test
    fun topLevelDestinations_areUniqueAndComplete() {
        val sections = topLevelDestinations.map { it.section }
        assertEquals(AppSection.entries.toSet(), sections.toSet())
        assertEquals(sections.size, sections.distinct().size)
    }

    @Test
    fun sharedDestination_preservesJourneyOrigin() {
        assertEquals(AppSection.HOME, AppDestination.Events(AppSection.HOME).origin)
        assertEquals(AppSection.MORE, AppDestination.Events(AppSection.MORE).origin)
        assertEquals(AppSection.COLLECTION, AppDestination.Pokemon("azumarill", AppSection.COLLECTION).origin)
        assertEquals(AppSection.HOME, AppDestination.Pokemon("azumarill", AppSection.HOME).origin)
    }

    @Test
    fun rootDestinations_haveCanonicalOwners() {
        val roots = listOf(
            AppDestination.Today to AppSection.HOME,
            AppDestination.Collection to AppSection.COLLECTION,
            AppDestination.Teams to AppSection.TEAMS,
            AppDestination.Battles to AppSection.BATTLES,
            AppDestination.More to AppSection.MORE
        )
        assertTrue(roots.all { (destination, owner) -> destination.origin == owner })
    }
}
