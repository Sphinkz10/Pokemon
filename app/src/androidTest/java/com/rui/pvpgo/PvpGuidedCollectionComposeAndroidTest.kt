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
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Complements the EXISTING three-stage guide rather than installing a
 * competing UI. All clicks and counts use the real CollectionOverviewScreen.
 */
@RunWith(AndroidJUnit4::class)
class PvpGuidedCollectionComposeAndroidTest {
    @get:Rule val rule = createComposeRule()
    private val species = PokemonSpecies(
        dex = 25, speciesId = "guide-ui-species-25", name = "Teste Liga",
        familyId = "guide-ui-family", baseAttack = 105,
        baseDefense = 115, baseStamina = 120
    )
    private val iv = IvSpread(0, 15, 15)
    private fun owned(id: String, level: Double) = OwnedPokemon(
        id = id, speciesId = species.speciesId, iv = iv, level = level,
        cp = PokemonMath.cp(species, iv, level), createdAtEpochMs = 1L
    )
    private val saved = listOf(
        owned("guide-missing", 20.0).copy(cp = null),
        owned("guide-low", 20.0),
        owned("guide-high", 35.0)
    )
    private var opened = ""
    private var compared = ""

    private fun render() {
        rule.setContent {
            MaterialTheme {
                CollectionOverviewScreen(
                    catalog = listOf(species), collection = saved,
                    buildPlans = emptyList(), pendingCount = 0,
                    onAdd = {}, onInbox = {},
                    onSelectOwned = { opened = it },
                    onOpenIvTargets = { compared = it }
                )
            }
        }
    }
    private fun scrollTo(tag: String) {
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag(tag))
    }
    private fun tap(tag: String) {
        scrollTo(tag)
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun count(message: String) {
        scrollTo("collection-guide-count")
        rule.onNodeWithTag("collection-guide-count")
            .assertTextContains(message, substring = true)
    }
    private fun compareStep() { tap("collection-guide-compare") }

    @Test fun enteringComparisonDefaultsToAllAndOptionalReviewExposesUnknowns() {
        render()
        compareStep()
        count("3 exemplares")
        tap("pvp-compare-stage-review")
        count("1 exemplar")
        scrollTo("pvp-compare-explanation")
        rule.onNodeWithTag("pvp-compare-explanation")
            .assertTextContains("confirma CP", substring = true)
        scrollTo("owned-row-guide-missing")
        rule.onNodeWithTag("owned-row-guide-missing").assertExists()
        rule.runOnIdle { assertEquals(3, saved.size) }
    }

    @Test fun capFilteringAcrossLeaguesKeepsRowsActionable() {
        render()
        compareStep()
        tap("pvp-compare-stage-within_limit")
        count("2 exemplares")
        tap("pvp-compare-league-little")
        count("1 exemplar")
        tap("pvp-compare-stage-over_limit")
        count("1 exemplar")
        scrollTo("owned-row-guide-high")
        rule.onNodeWithTag("owned-row-guide-high").performClick()
        rule.runOnIdle {
            assertEquals("guide-high", opened)
            assertEquals(3, saved.size)
        }
    }

    @Test fun exploratoryProfileWarnsAndStillRoutesToActualIvTargets() {
        render()
        compareStep()
        tap("pvp-compare-stage-iv_pattern")
        scrollTo("pvp-compare-explanation")
        rule.onNodeWithTag("pvp-compare-explanation")
            .assertTextContains("Não é ranking IV", substring = true)
        count("2 exemplares")
        scrollTo("collection-guide-open-iv-guide-low")
        rule.onNodeWithTag("collection-guide-open-iv-guide-low").performClick()
        rule.runOnIdle {
            assertEquals(species.speciesId, compared)
            assertEquals(3, saved.size)
        }
    }
}
