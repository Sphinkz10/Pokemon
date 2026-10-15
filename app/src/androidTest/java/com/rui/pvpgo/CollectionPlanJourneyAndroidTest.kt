package com.rui.pvpgo

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
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

/** Real collection screens & Android SQLite; nobody auto-spends resources. */
@RunWith(AndroidJUnit4::class)
class CollectionPlanJourneyAndroidTest {
    @get:Rule val rule = createComposeRule()
    private val species = PokemonSpecies(
        dex = 25, speciesId = "plan-25", familyId = "plan-family",
        name = "Pikachu Plano", baseAttack = 105,
        baseDefense = 115, baseStamina = 120
    )
    private val iv = IvSpread(0, 15, 15)
    private val saved = OwnedPokemon(
        id = "plan-owned", speciesId = species.speciesId, iv = iv,
        cp = PokemonMath.cp(species, iv, 20.0), level = 20.0,
        createdAtEpochMs = 100L
    )
    private lateinit var db: PvpUserDatabase
    private lateinit var repository: CollectionRepository
    private var launched: Pair<String, League>? = null

    @Before fun setup() {
        launched = null
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PvpUserDatabase::class.java).build()
        repository = CollectionRepository.forTesting(db)
        runBlocking { repository.upsertOwned(saved) }
    }
    @After fun teardown() { db.close() }

    private fun openStep() {
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-guide-plan"))
        rule.onNodeWithTag("collection-guide-plan").performClick()
    }

    private fun openCandidate() {
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-plan-start-plan-owned"))
        rule.onNodeWithTag("collection-plan-start-plan-owned").performClick()
    }

    private fun standalone(plans: List<PvpBuildPlan> = emptyList()) {
        rule.setContent {
            MaterialTheme {
                CollectionOverviewScreen(
                    catalog = listOf(species), collection = listOf(saved),
                    buildPlans = plans, pendingCount = 0,
                    onAdd = {}, onInbox = {}, onSelectOwned = {},
                    onOpenIvTargets = {},
                    onStartPlan = { id, league -> launched = id to league }
                )
            }
        }
    }

    @Test fun emptyPlanStageOffersActualPokemonWithoutCreatingAnyPlan() {
        standalone()
        openStep()
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains("0 exemplares", substring = true)
        rule.onNodeWithText("COMEÇAR UM PLANO PvP").assertExists()
        openCandidate()
        rule.runOnIdle {
            assertEquals("plan-owned" to League.GREAT, launched)
        }
    }

    @Test fun planChoicePreservesSelectedMasterLeagueAndDoesNotAlterSpecimen() {
        standalone()
        openStep()
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("pvp-compare-league-master"))
        rule.onNodeWithTag("pvp-compare-league-master").performClick()
        openCandidate()
        rule.runOnIdle {
            assertEquals("plan-owned" to League.MASTER, launched)
            assertEquals(20.0, saved.level!!, 0.0)
        }
    }

    @Test fun savedPlanIsListedAsRealAndNoLongerOfferedAsUnplanned() {
        standalone(listOf(PvpBuildPlan(
            id = "plan:plan-owned:GREAT", ownedPokemonId = saved.id,
            league = League.GREAT, priority = 50, createdAtEpochMs = 100L
        )))
        openStep()
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains("1 exemplar", substring = true)
        rule.onNodeWithTag("collection-plan-start-plan-owned").assertDoesNotExist()
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("owned-row-plan-owned"))
        rule.onNodeWithTag("owned-row-plan-owned").assertExists()
    }

    @Test fun actualAppOpensExactOwned360AndSavesOnlyAfterExplicitTap() {
        rule.setContent {
            PvPGoApp(
                catalogOverride = listOf(species),
                collectionRepositoryOverride = repository
            )
        }
        rule.onNodeWithTag("app-tab-collection").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("collection-guide-plan"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        openStep()
        openCandidate()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("pokemon-360-back"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("pokemon-360-scroll")
            .performScrollToNode(hasTestTag("pokemon-360-save-plan"))
        rule.runOnIdle {
            assertTrue(runBlocking { repository.buildPlans.first() }.isEmpty())
        }
        rule.onNodeWithTag("pokemon-360-save-plan").performClick()
        rule.waitUntil(15000) {
            runBlocking { repository.buildPlans.first() }.size == 1
        }
        rule.runOnIdle {
            val plan = runBlocking { repository.buildPlans.first() }.single()
            assertEquals(saved.id, plan.ownedPokemonId)
            assertEquals(League.GREAT, plan.league)
            assertEquals(20.0, runBlocking { repository.currentCollection() }.single().level!!, 0.0)
        }
        rule.onNodeWithTag("pokemon-360-back").performClick()
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-guide-count"))
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains("1 exemplar", substring = true)
        rule.onNodeWithTag("collection-plan-start-plan-owned").assertDoesNotExist()
    }
}
