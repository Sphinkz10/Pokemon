package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same Back rules run in production and in JVM unit tests. */
class CollectionNavigationPolicyTest {
    private fun go(
        current: CollectionRoute,
        detail: CollectionRoute = CollectionRoute.SPECIES,
        compare: CollectionRoute = CollectionRoute.EXEMPLARS,
        targets: CollectionRoute = CollectionRoute.SPECIES
    ) = CollectionNavigationPolicy.backTarget(current, detail, compare, targets)

    @Test fun ownedDetailReturnsToActualOrigin() {
        assertEquals(CollectionRoute.SPECIES,
            go(CollectionRoute.DETAIL, detail = CollectionRoute.SPECIES))
        assertEquals(CollectionRoute.EXEMPLARS,
            go(CollectionRoute.DETAIL, detail = CollectionRoute.EXEMPLARS))
    }

    @Test fun comparisonReturnsToActualOrigin() {
        assertEquals(CollectionRoute.SPECIES,
            go(CollectionRoute.COMPARE, compare = CollectionRoute.SPECIES))
        assertEquals(CollectionRoute.EXEMPLARS,
            go(CollectionRoute.COMPARE, compare = CollectionRoute.EXEMPLARS))
    }

    @Test fun ivTargetsReturnToActualOrigin() {
        assertEquals(CollectionRoute.LIST,
            go(CollectionRoute.IV_TARGETS, targets = CollectionRoute.LIST))
        assertEquals(CollectionRoute.SPECIES,
            go(CollectionRoute.IV_TARGETS, targets = CollectionRoute.SPECIES))
    }

    @Test fun importAndReviewBackPathsAreDeterministic() {
        assertEquals(CollectionRoute.LIST, go(CollectionRoute.IMPORT_HOME))
        assertEquals(CollectionRoute.IMPORT_HOME, go(CollectionRoute.MANUAL_QUICK))
        assertEquals(CollectionRoute.IMPORT_HOME, go(CollectionRoute.MANUAL_GUIDED))
        assertEquals(CollectionRoute.IMPORT_HOME, go(CollectionRoute.PASTE))
        assertEquals(CollectionRoute.LIST, go(CollectionRoute.INBOX))
    }

    @Test fun speciesAndExemplarsGoBackOneLevel() {
        assertEquals(CollectionRoute.LIST, go(CollectionRoute.SPECIES))
        assertEquals(CollectionRoute.SPECIES, go(CollectionRoute.EXEMPLARS))
        assertEquals(CollectionRoute.LIST, go(CollectionRoute.LIST))
    }

    @Test fun recordIdentifiersRequiredForOnlyAppropriateRoutes() {
        assertTrue(CollectionNavigationPolicy.canRestoreOwned(CollectionRoute.DETAIL))
        assertFalse(CollectionNavigationPolicy.canRestoreOwned(CollectionRoute.SPECIES))
        assertTrue(CollectionNavigationPolicy.canRestoreSpecies(CollectionRoute.EXEMPLARS))
        assertTrue(CollectionNavigationPolicy.canRestoreSpecies(CollectionRoute.COMPARE))
        assertTrue(CollectionNavigationPolicy.canRestoreSpecies(CollectionRoute.IV_TARGETS))
        assertFalse(CollectionNavigationPolicy.canRestoreSpecies(CollectionRoute.LIST))
    }

    @Test fun comparisonOnlyRestoredWithOriginalPair() {
        assertTrue(CollectionNavigationPolicy.canRestorePair(CollectionRoute.COMPARE))
        assertFalse(CollectionNavigationPolicy.canRestorePair(CollectionRoute.DETAIL))
        assertFalse(CollectionNavigationPolicy.canRestorePair(CollectionRoute.IV_TARGETS))
    }
}
