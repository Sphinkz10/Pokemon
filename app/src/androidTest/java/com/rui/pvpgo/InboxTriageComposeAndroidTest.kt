package com.rui.pvpgo

import android.content.Context
import androidx.room.Room
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.domain.CandidateInboxItem
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.PokemonSpecies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual app shell + Android Room + Inbox Compose controls, no fake Inbox. */
@RunWith(AndroidJUnit4::class)
class InboxTriageComposeAndroidTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var database: PvpUserDatabase
    private lateinit var repository: CollectionRepository

    private val pikachu = PokemonSpecies(
        dex = 25, speciesId = "triage-pikachu", familyId = "triage-pikachu-family",
        name = "Pikachu", baseAttack = 105, baseDefense = 115, baseStamina = 120
    )
    private val bulbasaur = PokemonSpecies(
        dex = 1, speciesId = "triage-bulbasaur", familyId = "triage-bulba-family",
        name = "Bulbasaur", baseAttack = 110, baseDefense = 125, baseStamina = 128
    )
    private fun owned(id: String, species: String = pikachu.speciesId,
        cp: Int? = 500, level: Double? = 20.0
    ) = OwnedPokemon(
        id = id, speciesId = species, iv = IvSpread(0, 15, 15),
        cp = cp, level = level, createdAtEpochMs = 100L
    )

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PvpUserDatabase::class.java).build()
        repository = CollectionRepository.forTesting(database)
        runBlocking {
            repository.upsertOwned(owned("saved-25"))
            repository.submitCandidates(listOf(
                CandidateInboxItem("triage-match", owned("candidate-match"), 100L),
                CandidateInboxItem("triage-different", owned("candidate-bulba", bulbasaur.speciesId), 101L),
                CandidateInboxItem("triage-unknown", owned("candidate-unknown", bulbasaur.speciesId, null, null), 102L)
            ))
        }
    }
    @After fun teardown() { database.close() }

    private fun openInbox() {
        rule.setContent {
            PvPGoApp(
                catalogOverride = listOf(pikachu, bulbasaur),
                collectionRepositoryOverride = repository
            )
        }
        rule.onNodeWithTag("app-tab-collection").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("collection-open-inbox"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("collection-open-inbox").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("inbox-triage-count"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun filter(tag: String) {
        rule.onNodeWithTag("inbox-triage-$tag").performScrollTo().performClick()
    }

    @Test fun inboxShowsCountsAndAllPendingBeforeFiltering() {
        openInbox()
        rule.onNodeWithTag("inbox-triage-count")
            .assertTextContains("3 de 3", substring = true)
        rule.onNodeWithTag("inbox-candidate-triage-match").assertExists()
        rule.onNodeWithTag("inbox-candidate-triage-different").assertExists()
        rule.onNodeWithTag("inbox-candidate-triage-unknown").assertExists()
    }

    @Test fun possibleCorrespondenceFilterShowsOnlyMatchingSpecimen() {
        openInbox()
        filter("possible_matches")
        rule.onNodeWithTag("inbox-triage-count")
            .assertTextContains("1 de 3", substring = true)
        rule.onNodeWithTag("inbox-candidate-triage-match").assertExists()
        rule.onNodeWithTag("inbox-candidate-triage-different").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(3, runBlocking { repository.pendingInbox.first().size })
            assertEquals(1, runBlocking { repository.currentCollection().size })
        }
    }

    @Test fun missingDataFilterDoesNotInterpretUnknownAsZeroOrDiscardAnything() {
        openInbox()
        filter("missing_data")
        rule.onNodeWithTag("inbox-triage-count")
            .assertTextContains("1 de 3", substring = true)
        rule.onNodeWithTag("inbox-candidate-triage-unknown").assertExists()
        rule.onNodeWithTag("inbox-candidate-triage-match").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(3, runBlocking { repository.pendingInbox.first().size })
        }
    }

    @Test fun searchWithNoResultsOffersResetAndRestoresAllRows() {
        openInbox()
        rule.onNodeWithTag("inbox-triage-search").performTextInput("sem-resultado-999")
        rule.onNodeWithTag("inbox-triage-count")
            .assertTextContains("0 de 3", substring = true)
        rule.onNodeWithTag("inbox-triage-reset").performScrollTo().performClick()
        rule.onNodeWithTag("inbox-triage-count")
            .assertTextContains("3 de 3", substring = true)
        rule.onNodeWithTag("inbox-candidate-triage-match").assertExists()
    }

    @Test fun actualExplicitDiscardAfterFilteringOnlyRemovesChosenPending() {
        openInbox()
        filter("no_matches")
        rule.onNodeWithTag("inbox-discard-triage-different")
            .performScrollTo().performClick()
        rule.waitUntil(15000) {
            runBlocking { repository.pendingInbox.first().size } == 2
        }
        rule.runOnIdle {
            val pendingIds = runBlocking { repository.pendingInbox.first() }.map { it.id }
            assertTrue("triage-match" in pendingIds)
            assertTrue("triage-unknown" in pendingIds)
            val history = runBlocking { repository.inbox.first() }
            assertEquals("DISCARDED", history.single { it.id == "triage-different" }.status.name)
            assertEquals("saved-25", runBlocking { repository.currentCollection() }.single().id)
        }
    }
}
