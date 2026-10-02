package com.rui.pvpgo

/**
 * Pure navigation rules. Keep all return routes in one place so the Android
 * system Back button follows the same path as the explicit screen back button.
 * Stored routes and species/exemplar IDs are saveable primitives.
 */
internal enum class CollectionRoute {
    LIST, SPECIES, EXEMPLARS, COMPARE, DETAIL, IV_TARGETS,
    IMPORT_HOME, MANUAL_QUICK, MANUAL_GUIDED, PASTE, INBOX
}

internal object CollectionNavigationPolicy {
    fun backTarget(
        current: CollectionRoute,
        detailOrigin: CollectionRoute,
        compareOrigin: CollectionRoute,
        targetsOrigin: CollectionRoute
    ): CollectionRoute = when (current) {
        CollectionRoute.LIST -> CollectionRoute.LIST
        CollectionRoute.SPECIES -> CollectionRoute.LIST
        CollectionRoute.EXEMPLARS -> CollectionRoute.SPECIES
        CollectionRoute.COMPARE -> compareOrigin
        CollectionRoute.DETAIL -> detailOrigin
        CollectionRoute.IV_TARGETS -> targetsOrigin
        CollectionRoute.IMPORT_HOME -> CollectionRoute.LIST
        CollectionRoute.MANUAL_QUICK,
        CollectionRoute.MANUAL_GUIDED,
        CollectionRoute.PASTE -> CollectionRoute.IMPORT_HOME
        CollectionRoute.INBOX -> CollectionRoute.LIST
    }

    /** An empty asynchronous catalog is not proof that a saved species disappeared. */
    fun shouldFallbackMissingSpecies(catalogLoaded: Boolean): Boolean = catalogLoaded

    fun canRestoreSpecies(route: CollectionRoute): Boolean =
        route in setOf(CollectionRoute.SPECIES, CollectionRoute.EXEMPLARS,
            CollectionRoute.COMPARE, CollectionRoute.IV_TARGETS)

    fun canRestoreOwned(route: CollectionRoute): Boolean =
        route == CollectionRoute.DETAIL

    fun canRestorePair(route: CollectionRoute): Boolean =
        route == CollectionRoute.COMPARE
}
