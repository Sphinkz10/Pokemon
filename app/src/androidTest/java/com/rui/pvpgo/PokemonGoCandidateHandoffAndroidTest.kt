package com.rui.pvpgo

import android.content.Context
import androidx.room.Room
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/**
 * Production app-shell journey, isolated Android Room: copy GO filter ->
 * manually enter verified IVs -> explicit Review Inbox.
 * Clipboard text alone is never accepted as a Pokémon record.
 */
@RunWith(AndroidJUnit4::class)
class PokemonGoCandidateHandoffAndroidTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var db: PvpUserDatabase
    private lateinit var repository: CollectionRepository
    private val species = PokemonSpecies(
        dex = 25, speciesId = "go-hand-off-pikachu", familyId = "go-hand-off-family",
        name = "Pikachu", baseAttack = 105, baseDefense = 115, baseStamina = 120
    )

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PvpUserDatabase::class.java).build()
        repository = CollectionRepository.forTesting(db)
    }

    @After fun tearDown() { db.close() }

    private fun startComparison() {
        rule.setContent {
            PvPGoApp(
                catalogOverride = listOf(species),
                collectionRepositoryOverride = repository
            )
        }
        rule.onNodeWithTag("app-tab-collection").performClick()
        // Room data is asynchronous and this card may be below the LazyColumn
        // fold. Wait for the real scroll container, not the uncomposed child.
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("collection-overview-scroll"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-guide-compare"))
        rule.onNodeWithTag("collection-guide-compare").performClick()
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("go-search-panel"))
    }

    private fun openGuidedCapture() {
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("go-search-record-candidate"))
        rule.onNodeWithTag("go-search-record-candidate").performClick()
        rule.onNodeWithText("Adicionar · Guiado").assertExists()
    }

    private fun openReviewInbox() {
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("go-search-review-inbox"))
        rule.onNodeWithTag("go-search-review-inbox").performClick()
    }


    /** Real manual-capture UI; no test repository shortcuts to confirm/discard. */
    private fun captureOnePendingCandidate(): String {
        startComparison()
        openGuidedCapture()
        rule.onNodeWithTag("manual-import-species-search").performTextInput("Pikachu")
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("manual-import-species-go-hand-off-pikachu"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("manual-import-species-go-hand-off-pikachu").performClick()
        rule.onNodeWithTag("manual-import-iv-ataque")
            .performScrollTo().performTextInput("0")
        rule.onNodeWithTag("manual-import-iv-defesa")
            .performScrollTo().performTextInput("15")
        rule.onNodeWithTag("manual-import-iv-hp")
            .performScrollTo().performTextInput("15")
        rule.onNodeWithTag("manual-import-scroll")
            .performScrollToNode(hasTestTag("manual-import-submit"))
        rule.onNodeWithTag("manual-import-submit").performClick()
        rule.waitUntil(15000) { runBlocking { repository.pendingInbox.first().size } == 1 }
        val candidateId = runBlocking { repository.pendingInbox.first().single().id }
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("inbox-candidate-$candidateId"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        return candidateId
    }

    @Test fun filterStepHasRealActionsAndDoesNotInventAnImportedPokemon() {
        startComparison()
        rule.onNodeWithTag("go-search-next-guidance").assertExists()
        rule.onNodeWithTag("go-search-record-candidate").assertExists()
        rule.onNodeWithTag("go-search-review-inbox").assertExists()
        rule.onNodeWithTag("go-search-copy").performScrollTo().performClick()
        rule.onNodeWithTag("go-search-copied").assertExists()
        rule.runOnIdle {
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
            assertTrue(runBlocking { repository.pendingInbox.first() }.isEmpty())
        }
    }

    @Test fun goSearchOpensRealGuidedManualCaptureAndBackRestoresComparison() {
        startComparison()
        openGuidedCapture()
        rule.onNodeWithTag("manual-import-species-search").assertExists()
        rule.onNodeWithTag("manual-import-back").performClick()
        rule.onNodeWithTag("collection-guide-compare").assertExists()
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("go-search-panel"))
        rule.onNodeWithTag("go-search-record-candidate").assertExists()
        rule.runOnIdle {
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
        }
    }

    @Test fun reviewInboxFromFilterOpensTrueEmptyStateWithoutCreatingCandidate() {
        startComparison()
        openReviewInbox()
        rule.onNodeWithText("Não há candidatos pendentes.").assertExists()
        rule.runOnIdle {
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
            assertTrue(runBlocking { repository.pendingInbox.first() }.isEmpty())
        }
    }

    @Test fun manualAppraisalCreatesPendingInboxCandidateButNeverAutoConfirmsOwned() {
        startComparison()
        openGuidedCapture()
        rule.onNodeWithTag("manual-import-species-search").performTextInput("Pikachu")
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("manual-import-species-go-hand-off-pikachu"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("manual-import-species-go-hand-off-pikachu").performClick()
        rule.onNodeWithTag("manual-import-iv-ataque")
            .performScrollTo().performTextInput("0")
        rule.onNodeWithTag("manual-import-iv-defesa")
            .performScrollTo().performTextInput("15")
        rule.onNodeWithTag("manual-import-iv-hp")
            .performScrollTo().performTextInput("15")
        rule.onNodeWithTag("manual-import-scroll")
            .performScrollToNode(hasTestTag("manual-import-submit"))
        rule.onNodeWithTag("manual-import-submit").performClick()
        rule.waitUntil(15000) {
            runBlocking { repository.pendingInbox.first().size } == 1
        }
        rule.onNodeWithText("Review Inbox · 1").assertExists()
        rule.runOnIdle {
            val pending = runBlocking { repository.pendingInbox.first() }.single()
            assertEquals(species.speciesId, pending.candidate.speciesId)
            assertEquals(0, pending.candidate.iv.attack)
            assertEquals(15, pending.candidate.iv.defense)
            assertEquals(15, pending.candidate.iv.stamina)
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
        }
    }

    @Test fun candidateIvPreviewOpensAndClosesWithoutAutoAddingOwnedPokemon() {
        val candidateId = captureOnePendingCandidate()
        rule.onNodeWithTag("inbox-preview-$candidateId").performClick()
        rule.onNodeWithText("Fechar pré-visualização de IVs").assertExists()
        rule.onNodeWithTag("inbox-preview-$candidateId").performClick()
        rule.onNodeWithText("Ver ranking de IVs por liga").assertExists()
        rule.runOnIdle {
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
            assertEquals(1, runBlocking { repository.pendingInbox.first().size })
        }
    }

    @Test fun explicitlyDiscardedCandidateNeverEntersOwnedCollection() {
        val candidateId = captureOnePendingCandidate()
        rule.onNodeWithTag("inbox-list")
            .performScrollToNode(hasTestTag("inbox-discard-$candidateId"))
        rule.onNodeWithTag("inbox-discard-$candidateId").performClick()
        rule.onNodeWithText("Descartar este candidato?").assertExists()
        rule.runOnIdle {
            assertEquals(1, runBlocking { repository.pendingInbox.first().size })
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
        }
        rule.onNodeWithTag("inbox-discard-confirm").performClick()
        rule.waitUntil(15000) {
            runBlocking { repository.pendingInbox.first().isEmpty() }
        }
        rule.onNodeWithText("Não há candidatos pendentes.").assertExists()
        rule.runOnIdle {
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
            val reviewed = runBlocking { repository.inbox.first() }.single()
            assertEquals(candidateId, reviewed.id)
            assertEquals("DISCARDED", reviewed.status.name)
            assertEquals(null, reviewed.linkedOwnedPokemonId)
        }
    }

    @Test fun explicitConfirmCreatesExactOwnedPokemonAndOpensReal360() {
        val candidateId = captureOnePendingCandidate()
        val before = runBlocking { repository.pendingInbox.first().single() }
        val candidate = before.candidate
        rule.onNodeWithTag("inbox-list")
            .performScrollToNode(hasTestTag("inbox-confirm-$candidateId"))
        rule.onNodeWithTag("inbox-confirm-$candidateId").performClick()
        rule.waitUntil(15000) {
            runBlocking {
                repository.currentCollection().size == 1 &&
                    repository.pendingInbox.first().isEmpty()
            }
        }
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("pokemon-360-back"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.runOnIdle {
            val saved = runBlocking { repository.currentCollection() }.single()
            assertEquals(candidate.id, saved.id)
            assertEquals(species.speciesId, saved.speciesId)
            assertEquals(0, saved.iv.attack)
            assertEquals(15, saved.iv.defense)
            assertEquals(15, saved.iv.stamina)
            val reviewed = runBlocking { repository.inbox.first() }.single()
            assertEquals(candidateId, reviewed.id)
            assertEquals("CONFIRMED_NEW", reviewed.status.name)
            assertEquals(saved.id, reviewed.linkedOwnedPokemonId)
        }
    }

}
