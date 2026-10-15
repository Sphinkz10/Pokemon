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
    private var openedOwned: String? = null
    private lateinit var resourceStore: UserResourceStore

    @Before fun setup() {
        launched = null
        openedOwned = null
        val context = ApplicationProvider.getApplicationContext<Context>()
        val isolatedPreferences = context.getSharedPreferences("pr34-plan-next-ui", Context.MODE_PRIVATE)
        isolatedPreferences.edit().clear().commit()
        resourceStore = UserResourceStore(isolatedPreferences)
        db = Room.inMemoryDatabaseBuilder(context, PvpUserDatabase::class.java).build()
        repository = CollectionRepository.forTesting(db)
        runBlocking { repository.upsertOwned(saved) }
    }
    @After fun teardown() {
        db.close()
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("pr34-plan-next-ui", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun openStep() {
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-guide-plan"))
        rule.onNodeWithTag("collection-guide-plan").performClick()
    }

    private fun chooseLeague(league: League) {
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("pvp-compare-league-${league.name.lowercase()}"))
        rule.onNodeWithTag("pvp-compare-league-${league.name.lowercase()}").performClick()
    }

    private fun openCandidate() {
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-plan-start-plan-owned"))
        rule.onNodeWithTag("collection-plan-start-plan-owned").performClick()
    }

    private fun standalone(
        plans: List<PvpBuildPlan> = emptyList(),
        record: OwnedPokemon = saved
    ) {
        rule.setContent {
            MaterialTheme {
                CollectionOverviewScreen(
                    catalog = listOf(species), collection = listOf(record),
                    buildPlans = plans, pendingCount = 0,
                    onAdd = {}, onInbox = {}, onSelectOwned = { openedOwned = it },
                    onOpenIvTargets = {},
                    onStartPlan = { id, league -> launched = id to league },
                    resourceStoreOverride = resourceStore
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
    private fun savedPlan() = PvpBuildPlan(
        id = "plan:plan-owned:GREAT", ownedPokemonId = saved.id,
        league = League.GREAT, priority = 50, createdAtEpochMs = 100L
    )

    private fun scrollToNextAction() {
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-plan-next-plan-owned"))
        rule.onNodeWithTag("collection-plan-next-plan-owned").assertExists()
    }

    @Test fun savedPlanShowsNextCpVerificationWithoutInventingData() {
        standalone(plans = listOf(savedPlan()), record = saved.copy(cp = null))
        openStep()
        scrollToNextAction()
        rule.onNodeWithText("Ação seguinte · Confirmar CP, nível e dados reais")
            .assertExists()
        rule.onNodeWithTag("collection-plan-open-next-plan-owned")
            .performClick()
        rule.runOnIdle {
            assertEquals(saved.id, openedOwned)
            assertEquals(saved.cp, PokemonMath.cp(species, iv, 20.0))
        }
    }

    @Test fun savedConsistentSpecimenWithMissingAttacksAsksToConfirmMoves() {
        standalone(plans = listOf(savedPlan()))
        openStep()
        scrollToNextAction()
        rule.onNodeWithText("Ação seguinte · Confirmar ataques reais").assertExists()
        rule.onNodeWithTag("collection-plan-start-plan-owned").assertDoesNotExist()
        rule.runOnIdle { assertTrue(saved.fastMoveId == null) }
    }

    @Test fun recordedMovesAndUnknownCandyDemandUserEnteredResources() {
        standalone(
            plans = listOf(savedPlan()),
            record = saved.copy(fastMoveId = "fast", chargedMoveIds = listOf("charged"))
        )
        openStep()
        scrollToNextAction()
        rule.onNodeWithText("Ação seguinte · Registar recursos em falta").assertExists()
        rule.onNodeWithText("Resolver no Pokémon 360 →").assertExists()
    }

    @Test fun threeKnownBalancesOnlyUnlockTargetReviewNotSpending() {
        resourceStore.save(
            "plan-family",
            UserResourceBalances(stardust = 0, candy = 0, xlCandy = 0)
        )
        standalone(
            plans = listOf(savedPlan()),
            record = saved.copy(fastMoveId = "fast", chargedMoveIds = listOf("charged"))
        )
        openStep()
        scrollToNextAction()
        rule.onNodeWithText("Ação seguinte · Definir nível alvo e validar custos")
            .assertExists()
        rule.onNodeWithTag("collection-plan-open-next-plan-owned").performClick()
        rule.runOnIdle {
            assertEquals(saved.id, openedOwned)
            assertEquals(0, resourceStore.load("plan-family").candy)
            assertEquals(0, resourceStore.load("plan-family").xlCandy)
        }
    }

    @Test fun savedPlanActionHandsExactOwnedLeagueAndTaskToNavigation() {
        var action: Triple<String, League, SavedPlanNextStep>? = null
        rule.setContent {
            MaterialTheme {
                CollectionOverviewScreen(
                    catalog = listOf(species), collection = listOf(saved.copy(cp = null)),
                    buildPlans = listOf(savedPlan().copy(league = League.ULTRA)),
                    pendingCount = 0, onAdd = {}, onInbox = {},
                    onSelectOwned = {}, onOpenIvTargets = {},
                    onOpenSavedPlanAction = { id, league, step ->
                        action = Triple(id, league, step)
                    },
                    resourceStoreOverride = resourceStore
                )
            }
        }
        openStep()
        chooseLeague(League.ULTRA)
        scrollToNextAction()
        rule.onNodeWithTag("collection-plan-open-next-plan-owned").performClick()
        rule.runOnIdle {
            assertEquals(
                Triple("plan-owned", League.ULTRA, SavedPlanNextStep.VERIFY_SPECIMEN),
                action
            )
        }
    }

    @Test fun realAppSavedUltraPlanOpensOwnMoveEditorWithUltraLeagueAndReturnsToGuide() {
        runBlocking { repository.upsertBuildPlan(savedPlan().copy(league = League.ULTRA)) }
        rule.setContent {
            PvPGoApp(
                catalogOverride = listOf(species),
                collectionRepositoryOverride = repository
            )
        }
        rule.onNodeWithTag("app-tab-collection").performClick()
        openStep()
        chooseLeague(League.ULTRA)
        scrollToNextAction()
        rule.onNodeWithTag("collection-plan-open-next-plan-owned").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("pokemon-360-current-task"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("pokemon-360-current-task")
            .assertTextContains("Ultra · Confirmar ataques", substring = true)
        rule.onNodeWithTag("pokemon-360-scroll")
            .performScrollToNode(hasTestTag("pokemon-360-cp"))
        rule.onNodeWithTag("pokemon-360-back").performClick()
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-plan-open-next-plan-owned"))
        rule.onNodeWithTag("collection-plan-open-next-plan-owned").assertExists()
        rule.runOnIdle {
            assertEquals(League.ULTRA, runBlocking { repository.buildPlans.first() }.single().league)
            assertEquals(saved.cp, runBlocking { repository.currentCollection() }.single().cp)
        }
    }

    @Test fun realAppMissingCpJumpsToCpEditorWithoutWritingEstimatedValues() {
        runBlocking {
            repository.upsertOwned(saved.copy(cp = null))
            repository.upsertBuildPlan(savedPlan().copy(league = League.MASTER))
        }
        rule.setContent {
            PvPGoApp(
                catalogOverride = listOf(species),
                collectionRepositoryOverride = repository
            )
        }
        rule.onNodeWithTag("app-tab-collection").performClick()
        openStep()
        chooseLeague(League.MASTER)
        scrollToNextAction()
        rule.onNodeWithTag("collection-plan-open-next-plan-owned").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodes(hasTestTag("pokemon-360-current-task"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("pokemon-360-current-task")
            .assertTextContains("Master · Confirmar CP e nível", substring = true)
        rule.onNodeWithTag("pokemon-360-scroll")
            .performScrollToNode(hasTestTag("pokemon-360-cp"))
        rule.onNodeWithTag("pokemon-360-cp").assertExists()
        rule.runOnIdle {
            assertEquals(null, runBlocking { repository.currentCollection() }.single().cp)
            assertEquals(20.0, runBlocking { repository.currentCollection() }.single().level!!, 0.0)
        }
    }

    @Test fun greatGuideWithOnlyUltraPlanOffersGreatCreationInsteadOfWrongAction() {
        standalone(plans = listOf(savedPlan().copy(league = League.ULTRA)))
        openStep()
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains("0 exemplares", substring = true)
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-plan-start-plan-owned"))
        rule.onNodeWithTag("collection-plan-start-plan-owned").assertExists()
        rule.onNodeWithTag("collection-plan-open-next-plan-owned").assertDoesNotExist()
        openCandidate()
        rule.runOnIdle { assertEquals("plan-owned" to League.GREAT, launched) }
    }

    @Test fun selectingUltraShowsSavedUltraPlanAndHidesCreateButton() {
        standalone(plans = listOf(savedPlan().copy(league = League.ULTRA)))
        openStep()
        chooseLeague(League.ULTRA)
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains("1 exemplar", substring = true)
        rule.onNodeWithTag("collection-plan-start-plan-owned").assertDoesNotExist()
        scrollToNextAction()
        rule.onNodeWithTag("collection-plan-open-next-plan-owned").assertExists()
    }

    @Test fun higherPriorityGreatPlanCannotHijackUltraSavedAction() {
        var action: Triple<String, League, SavedPlanNextStep>? = null
        rule.setContent {
            MaterialTheme {
                CollectionOverviewScreen(
                    catalog = listOf(species), collection = listOf(saved.copy(cp = null)),
                    buildPlans = listOf(
                        savedPlan().copy(priority = 100),
                        savedPlan().copy(id = "plan:plan-owned:ULTRA", league = League.ULTRA, priority = 1)
                    ),
                    pendingCount = 0, onAdd = {}, onInbox = {},
                    onSelectOwned = {}, onOpenIvTargets = {},
                    onOpenSavedPlanAction = { id, league, step ->
                        action = Triple(id, league, step)
                    },
                    resourceStoreOverride = resourceStore
                )
            }
        }
        openStep()
        chooseLeague(League.ULTRA)
        scrollToNextAction()
        rule.onNodeWithTag("collection-plan-open-next-plan-owned").performClick()
        rule.runOnIdle {
            assertEquals(
                Triple("plan-owned", League.ULTRA, SavedPlanNextStep.VERIFY_SPECIMEN),
                action
            )
        }
    }

    @Test fun archivedOnlyPlanRemainsReviewableButAllowsFreshGreatPlan() {
        val archived = savedPlan().copy(
            status = com.rui.pvpgo.domain.BuildStatus.ARCHIVED
        )
        standalone(plans = listOf(archived))
        openStep()
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains("1 exemplar", substring = true)
        // Historical plan stays reviewable while the new plan CTA is not blocked.
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-plan-start-plan-owned"))
        openCandidate()
        rule.runOnIdle {
            assertEquals(saved.id to League.GREAT, launched)
        }
    }

    @Test fun archivedUltraOnlyAllowsNewUltraPlanWithoutBorrowingGreatState() {
        val archived = savedPlan().copy(
            league = League.ULTRA,
            status = com.rui.pvpgo.domain.BuildStatus.ARCHIVED
        )
        standalone(plans = listOf(archived))
        openStep()
        chooseLeague(League.ULTRA)
        openCandidate()
        rule.runOnIdle {
            assertEquals(saved.id to League.ULTRA, launched)
        }
    }

    @Test fun activePlanStillSuppressesDuplicateCreationWhenOldPlanArchived() {
        val old = savedPlan().copy(
            id = "archived-history", status = com.rui.pvpgo.domain.BuildStatus.ARCHIVED
        )
        val live = savedPlan().copy(id = "live-plan")
        standalone(plans = listOf(old, live))
        openStep()
        rule.onNodeWithTag("collection-plan-start-plan-owned").assertDoesNotExist()
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-plan-next-plan-owned"))
        rule.onNodeWithTag("collection-plan-next-plan-owned").assertExists()
    }

    @Test fun creatingFreshPlanInActual360KeepsArchivedRecordAndItsIdentity() {
        val archived = savedPlan().copy(
            id = "plan:${saved.id}:great",
            status = com.rui.pvpgo.domain.BuildStatus.ARCHIVED,
            notes = "Histórico imutável"
        )
        runBlocking { repository.upsertBuildPlan(archived) }
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
        rule.onNodeWithTag("pokemon-360-save-plan")
            .assertTextContains("Criar plano", substring = true)
            .performClick()
        rule.waitUntil(15000) {
            runBlocking { repository.buildPlans.first() }.size == 2
        }
        rule.runOnIdle {
            val rows = runBlocking { repository.buildPlans.first() }
            assertEquals(2, rows.map { it.id }.toSet().size)
            assertEquals(archived, rows.single { it.id == archived.id })
            assertEquals(1, rows.count {
                it.ownedPokemonId == saved.id &&
                    it.league == League.GREAT &&
                    it.status != com.rui.pvpgo.domain.BuildStatus.ARCHIVED
            })
        }
    }

}
