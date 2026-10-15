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
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real on-device UI semantics and taps, using a fake input collection only. */
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

    private fun render() {
        rule.setContent {
            MaterialTheme {
                CollectionOverviewScreen(
                    catalog = listOf(species),
                    collection = saved, buildPlans = emptyList(),
                    pendingCount = 0, onAdd = {}, onInbox = {},
                    onSelectOwned = { opened = it },
                    onOpenIvTargets = {}
                )
            }
        }
    }

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag("collection-root")
            .performScrollToNode(hasTestTag(tag))
    }
    private fun tap(tag: String) {
        scrollTo(tag)
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun count(message: String) {
        scrollTo("pvp-guide-result-count")
        rule.onNodeWithTag("pvp-guide-result-count")
            .assertTextContains(message, substring = true)
    }

    @Test fun guideIsOptInAndDefaultStepExposesMissingData() {
        render()
        rule.onNodeWithTag("pvp-guide-panel").assertDoesNotExist()
        tap("pvp-guide-toggle")
        count("1 exemplares nesta fase")
        scrollTo("pvp-guide-explanation")
        rule.onNodeWithTag("pvp-guide-explanation")
            .assertTextContains("confirma CP", substring = true)
        scrollTo("owned-row-guide-missing")
        rule.onNodeWithTag("owned-row-guide-missing").assertExists()
    }

    @Test fun changingLeagueAndPhaseUpdatesRealResultsAndKeepsCollectionUnchanged() {
        render()
        tap("pvp-guide-toggle")
        tap("pvp-guide-stage-within_limit")
        count("2 exemplares nesta fase")
        tap("pvp-guide-league-little")
        count("1 exemplares nesta fase")
        tap("pvp-guide-stage-over_limit")
        count("1 exemplares nesta fase")
        scrollTo("owned-row-guide-high")
        rule.onNodeWithTag("owned-row-guide-high").performClick()
        rule.runOnIdle {
            assertEquals("guide-high", opened)
            assertEquals(3, saved.size)
        }
    }

    @Test fun profileStepWarnsItIsNotARankingAndClosingRestoresOriginalList() {
        render()
        tap("pvp-guide-toggle")
        tap("pvp-guide-stage-iv_pattern")
        scrollTo("pvp-guide-explanation")
        rule.onNodeWithTag("pvp-guide-explanation")
            .assertTextContains("Não é ranking IV", substring = true)
        count("2 exemplares nesta fase")
        tap("pvp-guide-toggle")
        rule.onNodeWithTag("pvp-guide-panel").assertDoesNotExist()
        scrollTo("owned-row-guide-missing")
        rule.onNodeWithTag("owned-row-guide-missing").assertExists()
    }
}
