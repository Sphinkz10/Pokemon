package com.rui.pvpgo.navigation

import kotlinx.serialization.Serializable

/**
 * Product-level navigation contract.
 *
 * Feature-local routers still exist during the migration, but root/shared
 * destinations must use these typed routes rather than manual tab mutation.
 */
@Serializable
enum class AppSection {
    HOME,
    COLLECTION,
    TEAMS,
    BATTLES,
    MORE
}

@Serializable
sealed interface AppDestination {
    val origin: AppSection?

    @Serializable
    data object Today : AppDestination {
        override val origin: AppSection = AppSection.HOME
    }

    @Serializable
    data object Collection : AppDestination {
        override val origin: AppSection = AppSection.COLLECTION
    }

    @Serializable
    data object Teams : AppDestination {
        override val origin: AppSection = AppSection.TEAMS
    }

    @Serializable
    data object Battles : AppDestination {
        override val origin: AppSection = AppSection.BATTLES
    }

    @Serializable
    data object More : AppDestination {
        override val origin: AppSection = AppSection.MORE
    }

    @Serializable
    data class Pokemon(
        val speciesId: String,
        override val origin: AppSection
    ) : AppDestination

    @Serializable
    data class Events(
        override val origin: AppSection
    ) : AppDestination

    @Serializable
    data class Agenda(
        override val origin: AppSection
    ) : AppDestination

    @Serializable
    data class Radar(
        override val origin: AppSection
    ) : AppDestination
}

data class TopLevelDestination(
    val section: AppSection,
    val label: String,
    val glyph: String,
    val route: AppDestination
)

val topLevelDestinations: List<TopLevelDestination> = listOf(
    TopLevelDestination(AppSection.HOME, "Hoje", "⌂", AppDestination.Today),
    TopLevelDestination(AppSection.COLLECTION, "Coleção", "▣", AppDestination.Collection),
    TopLevelDestination(AppSection.TEAMS, "Equipas", "◉", AppDestination.Teams),
    TopLevelDestination(AppSection.BATTLES, "Batalhas", "⚔", AppDestination.Battles),
    TopLevelDestination(AppSection.MORE, "Mais", "⋯", AppDestination.More)
)

fun topLevelRoute(section: AppSection): AppDestination = when (section) {
    AppSection.HOME -> AppDestination.Today
    AppSection.COLLECTION -> AppDestination.Collection
    AppSection.TEAMS -> AppDestination.Teams
    AppSection.BATTLES -> AppDestination.Battles
    AppSection.MORE -> AppDestination.More
}
