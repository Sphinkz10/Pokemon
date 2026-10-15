package com.rui.pvpgo

import android.content.Context
import androidx.room.Room
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full app shell navigation on Android: HOME -> COLLECTION -> SPECIES ->
 * the real persisted OWNED POKEMON 360, then a reversible back path.
 * Only catalog and Room are isolated; routes and click controls are real.
 */
@RunWith(AndroidJUnit4::class)
class CollectionNavigationJourneyAndroidTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var database: PvpUserDatabase
    private lateinit var repository: CollectionRepository

    private val species = PokemonSpecies(
        dex = 25, speciesId = "journey-nav-species-25", name = "Navegação Pikachu",
        familyId = "journey-nav-family", baseAttack = 105,
        baseDefense = 115, baseStamina = 120
    )
    private val iv = IvSpread(0, 15, 15)
    private val original = OwnedPokemon(
        id = "nav-owned-one",
        speciesId = species.speciesId,
        iv = iv,
        cp = PokemonMath.cp(species, iv, 20.0),
        level = 20.0,
        createdAtEpochMs = 123L,
        updatedAtEpochMs = 123L
    )

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PvpUserDatabase::class.java).build()
        repository = CollectionRepository.forTesting(database)
        runBlocking { repository.upsertOwned(original) }
    }

    @After fun teardown() {
        database.close()
    }

    private fun launchRoot() {
        rule.setContent {
            PvPGoApp(
                catalogOverride = listOf(species),
                collectionRepositoryOverride = repository
            )
        }
    }

    private fun intoCollection() {
        rule.onNodeWithTag("app-tab-collection").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("owned-row-nav-owned-one"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun intoSpecies() {
        intoCollection()
        rule.onNodeWithTag("owned-row-nav-owned-one").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("species-owned-nav-owned-one"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun intoDetail() {
        intoSpecies()
        rule.onNodeWithTag("species-owned-nav-owned-one").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("pokemon-360-back"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun changeCpDraft() {
        rule.onNodeWithTag("pokemon-360-scroll")
            .performScrollToNode(hasTestTag("pokemon-360-cp"))
        rule.onNodeWithTag("pokemon-360-cp").performTextInput("1")
    }

    @Test fun actualHomeToCollectionToSpeciesToOwned360IsNavigable() {
        launchRoot()
        rule.onNodeWithTag("app-tab-home").assertExists()
        intoDetail()
        rule.onNodeWithTag("pokemon-360-back").assertExists()
        rule.onNodeWithTag("pokemon-360-scroll")
            .performScrollToNode(hasTestTag("investment-level-input"))
        rule.onNodeWithTag("investment-level-input").assertExists()
        rule.runOnIdle {
            assertEquals(original.cp, runBlocking { repository.currentCollection() }.single().cp)
        }
    }

    @Test fun backFromPokemon360ReturnsToSpeciesThenCollectionWithoutDeletingIt() {
        launchRoot()
        intoDetail()
        rule.onNodeWithTag("pokemon-360-back").performClick()
        rule.onNodeWithTag("species-owned-nav-owned-one").assertExists()
        rule.onNodeWithTag("species-back").performClick()
        rule.onNodeWithTag("owned-row-nav-owned-one").assertExists()
        rule.runOnIdle {
            assertEquals(listOf(original.id), runBlocking {
                repository.currentCollection().map { it.id }
            })
        }
    }

    @Test fun unsavedCpEditRequiresExplicitDiscardAndPreservesRoom() {
        launchRoot()
        intoDetail()
        changeCpDraft()
        rule.onNodeWithTag("pokemon-360-back").performClick()
        rule.onNodeWithText("Alterações por guardar").assertExists()
        rule.onNodeWithText("Descartar alterações").performClick()
        rule.onNodeWithTag("species-owned-nav-owned-one").assertExists()
        rule.runOnIdle {
            val persisted = runBlocking { repository.currentCollection() }.single()
            assertEquals(original.cp, persisted.cp)
            assertEquals(original.level, persisted.level)
        }
    }

    @Test fun cancelDiscardKeepsTheUserOnOwned360WithUnsavedDraft() {
        launchRoot()
        intoDetail()
        changeCpDraft()
        rule.onNodeWithTag("pokemon-360-back").performClick()
        rule.onNodeWithText("Continuar a editar").performClick()
        rule.onNodeWithTag("pokemon-360-back").assertExists()
        rule.onNodeWithTag("pokemon-360-scroll")
            .performScrollToNode(hasTestTag("pokemon-360-cp"))
        rule.onNodeWithTag("pokemon-360-cp").assertExists()
        rule.runOnIdle {
            assertEquals(original.cp, runBlocking { repository.currentCollection() }.single().cp)
        }
    }

    @Test fun switchingBetweenBottomTabsReturnsToTheSavedCollectionRoute() {
        launchRoot()
        intoCollection()
        rule.onNodeWithTag("app-tab-home").performClick()
        rule.onNodeWithTag("app-tab-collection").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("owned-row-nav-owned-one"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("owned-row-nav-owned-one").performClick()
        rule.onNodeWithTag("species-owned-nav-owned-one").assertExists()
        rule.runOnIdle {
            assertEquals(1, runBlocking { repository.currentCollection() }.size)
        }
    }
}
