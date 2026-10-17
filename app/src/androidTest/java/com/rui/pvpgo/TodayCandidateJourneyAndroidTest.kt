package com.rui.pvpgo

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.engine.PokemonSpecies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Today -> clipboard -> guided form -> Room Inbox, with no GO account access. */
@RunWith(AndroidJUnit4::class)
class TodayCandidateJourneyAndroidTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: PvpUserDatabase
    private lateinit var repository: CollectionRepository
    private val showApp = mutableStateOf(true)
    private val species = PokemonSpecies(
        dex = 25, speciesId = "today-journey-pikachu", familyId = "today-journey-family",
        name = "Pikachu", baseAttack = 105, baseDefense = 115, baseStamina = 120
    )

    private fun clearJourney() {
        listOf("companion_today_goal_v1", "companion_today_journey_v1").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Before fun setup() {
        clearJourney()
        database = Room.inMemoryDatabaseBuilder(context, PvpUserDatabase::class.java).build()
        repository = CollectionRepository.forTesting(database)
    }

    @After fun teardown() {
        try {
            rule.runOnIdle { showApp.value = false }
            rule.waitForIdle()
        } finally {
            database.close()
            clearJourney()
        }
    }

    private fun startAndCopy() {
        rule.setContent {
            if (showApp.value) PvPGoApp(
                catalogOverride = listOf(species),
                collectionRepositoryOverride = repository
            )
        }
        rule.onNodeWithTag("today-goal-ivs").performScrollTo().performClick()
        rule.onNodeWithTag("today-scroll")
            .performScrollToNode(hasTestTag("today-journey-copy"))
        rule.onNodeWithTag("today-journey-copy").performClick()
        rule.runOnIdle {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("0-1attack&3-4defense&3-4hp",
                clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
            assertTrue(runBlocking { repository.pendingInbox.first() }.isEmpty())
        }
    }

    private fun openCapture() {
        rule.onNodeWithTag("today-scroll")
            .performScrollToNode(hasTestTag("today-journey-record-candidate"))
        rule.onNodeWithTag("today-journey-record-candidate").performClick()
        rule.onNodeWithText("Adicionar · Guiado").assertExists()
    }

    @Test fun emptyTodayInboxCanRegisterAndCancelBackToSameJourneyWithoutReplayingRequest() {
        startAndCopy()
        rule.onNodeWithTag("today-journey-inbox-status")
            .assertTextContains("0 candidatos pendentes", substring = true)
        openCapture()
        rule.onNodeWithTag("manual-import-back").performClick()
        rule.onNodeWithText("2 · Rever capturas").assertExists()
        rule.onNodeWithTag("app-tab-collection").performClick()
        rule.onNodeWithTag("collection-overview-scroll").assertExists()
        rule.onNodeWithTag("manual-import-species-search").assertDoesNotExist()
        rule.onNodeWithTag("app-tab-home").performClick()
        openCapture() // A fresh explicit request works after the first was consumed.
        rule.runOnIdle {
            assertTrue(runBlocking { repository.pendingInbox.first() }.isEmpty())
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
        }
    }

    @Test fun todayManualCaptureCreatesOnlyPendingCandidateAndShowsLiveInboxCount() {
        startAndCopy()
        openCapture()
        rule.onNodeWithTag("manual-import-species-search").performTextInput("Pikachu")
        rule.onNodeWithTag("manual-import-species-${species.speciesId}").performClick()
        listOf("ataque" to "0", "defesa" to "15", "hp" to "15").forEach { (field, value) ->
            rule.onNodeWithTag("manual-import-iv-$field").performScrollTo().performTextInput(value)
        }
        rule.onNodeWithTag("manual-import-scroll")
            .performScrollToNode(hasTestTag("manual-import-submit"))
        rule.onNodeWithTag("manual-import-submit").performClick()
        rule.waitUntil(15000) { runBlocking { repository.pendingInbox.first().size } == 1 }
        rule.onNodeWithText("Review Inbox · 1").assertExists()
        rule.runOnIdle {
            val candidate = runBlocking { repository.pendingInbox.first().single().candidate }
            assertEquals(species.speciesId, candidate.speciesId)
            assertEquals(0, candidate.iv.attack)
            assertEquals(15, candidate.iv.defense)
            assertEquals(15, candidate.iv.stamina)
            assertNull(candidate.cp)
            assertNull(candidate.level)
            assertTrue(runBlocking { repository.currentCollection() }.isEmpty())
        }
        rule.onNodeWithTag("app-tab-home").performClick()
        rule.onNodeWithTag("today-scroll")
            .performScrollToNode(hasTestTag("today-journey-inbox-status"))
        rule.waitUntil(15000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText(
                "1 candidato pendente na Companion. Confirma o exemplar na Inbox."
            )).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("today-scroll")
            .performScrollToNode(hasTestTag("today-journey-open-inbox"))
        rule.onNodeWithTag("today-journey-open-inbox").performClick()
        rule.onNodeWithText("Review Inbox · 1").assertExists()
    }
}
