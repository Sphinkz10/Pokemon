package com.rui.pvpgo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose UI interactions; uses the production CollectionOverviewScreen. */
@RunWith(AndroidJUnit4::class)
class CollectionGuidedIvAndroidTest {
    @get:Rule val rule = createComposeRule()

    private val species = PokemonSpecies(
        dex = 25, speciesId = "guided-fixture-25", familyId = "guided-fixture-family",
        name = "Teste Guiado", baseAttack = 105, baseDefense = 115, baseStamina = 120
    )
    private val ready = OwnedPokemon(
        id = "iv-ready", speciesId = species.speciesId,
        iv = IvSpread(0, 15, 15), cp = 490,
        level = 20.0, nickname = "Confirmado", createdAtEpochMs = 100L
    )
    private val needsReview = ready.copy(
        id = "iv-missing", nickname = "Por confirmar", cp = null, level = null
    )

    private fun render(onCompare: (String) -> Unit = {}) {
        rule.setContent {
            MaterialTheme {
                CollectionOverviewScreen(
                    catalog = listOf(species),
                    collection = listOf(ready, needsReview),
                    buildPlans = emptyList(),
                    pendingCount = 0,
                    onAdd = {},
                    onInbox = {},
                    onSelectOwned = {},
                    onOpenIvTargets = onCompare
                )
            }
        }
    }

    @Test fun guidedReviewFiltersOnlyMissingDataAndCanReturnToAll() {
        render()
        rule.onNodeWithTag("collection-guide-review").performScrollTo().performClick()
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains("1 exemplares", substring = true)
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("owned-row-iv-missing"))
        rule.onNodeWithTag("owned-row-iv-missing").assertExists()
        rule.onNodeWithTag("owned-row-iv-ready").assertDoesNotExist()
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-guide-all"))
        rule.onNodeWithTag("collection-guide-all").performClick()
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains("2 exemplares", substring = true)
    }

    @Test fun comparisonStepCallsExistingLeagueTargetToolWithCorrectSpecies() {
        var openedSpeciesId: String? = null
        render { openedSpeciesId = it }
        rule.onNodeWithTag("collection-guide-compare").performScrollTo().performClick()
        rule.onNodeWithTag("collection-guide-explanation")
            .assertTextContains("não é garantia", substring = true)
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("collection-guide-open-iv-iv-ready"))
        rule.onNodeWithTag("collection-guide-open-iv-iv-ready").performClick()
        rule.runOnIdle { assertEquals(species.speciesId, openedSpeciesId) }
    }

    @Test fun noPlanStageExplainsEmptyResultWithoutInventingPvPPlan() {
        render()
        rule.onNodeWithTag("collection-guide-plan").performScrollTo().performClick()
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains("0 exemplares", substring = true)
        rule.onNodeWithTag("owned-row-iv-ready").assertDoesNotExist()
    }
}
