package com.rui.pvpgo

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.engine.League
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual Android clipboard and Compose controls. No Pokémon GO account access,
 * no destructive inventory actions and no claim that a profile ranks IVs.
 */
@RunWith(AndroidJUnit4::class)
class PokemonGoSearchComposeAndroidTest {
    @get:Rule val rule = createComposeRule()
    private fun clipboard(): ClipboardManager =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private fun clipText(): String =
        clipboard().primaryClip?.getItemAt(0)?.text?.toString().orEmpty()

    private fun render(league: League = League.GREAT) {
        rule.setContent {
            MaterialTheme { PokemonGoSearchPanel(league = league) }
        }
    }

    @Test fun greatStartsWithBroadNonDestructiveCandidateFilterAndNoCopiedClaim() {
        render()
        rule.onNodeWithTag("go-search-query")
            .assertTextContains("0-2attack&2-4defense&2-4hp")
        rule.onNodeWithTag("go-search-safety")
            .assertTextContains("Não transfiras Pokémon", substring = true)
        rule.onNodeWithTag("go-search-copied").assertDoesNotExist()
    }

    @Test fun actualClipboardMatchesChosenPortugueseClassicProfile() {
        render()
        rule.onNodeWithTag("go-search-preset-low_attack").performClick()
        rule.onNodeWithTag("go-search-language-pt").performClick()
        rule.onNodeWithTag("go-search-query")
            .assertTextContains("0-1ataque&3-4defesa&3-4ps")
        rule.onNodeWithTag("go-search-copy").performClick()
        rule.onNodeWithTag("go-search-copied").assertTextContains("Copiado", substring = true)
        rule.runOnIdle {
            assertEquals("0-1ataque&3-4defesa&3-4ps", clipText())
        }
    }

    @Test fun changingLeagueRecommendsDifferentProfileWithoutCopyingAutomatically() {
        rule.setContent {
            var league by remember { mutableStateOf(League.GREAT) }
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    Button(onClick = { league = League.MASTER }) { Text("Escolher Master") }
                    PokemonGoSearchPanel(league)
                }
            }
        }
        rule.onNodeWithTag("go-search-query")
            .assertTextContains("0-2attack&2-4defense&2-4hp")
        rule.onNodeWithText("Escolher Master").performClick()
        rule.onNodeWithTag("go-search-query")
            .assertTextContains("3-4attack&3-4defense&3-4hp")
        rule.onNodeWithTag("go-search-copied").assertDoesNotExist()
    }

    @Test fun perfectIvUses4StarSearchButMakesNoPvpRankClaim() {
        render(League.MASTER)
        rule.onNodeWithTag("go-search-preset-perfect").performClick()
        rule.onNodeWithTag("go-search-query").assertTextContains("4*")
        rule.onNodeWithTag("go-search-copy").performClick()
        rule.runOnIdle { assertEquals("4*", clipText()) }
        rule.onNodeWithTag("go-search-safety")
            .assertTextContains("não IVs exatos, rank PvP", substring = true)
    }

    @Test fun actualCollectionStepTwoDisplaysSearchEvenWithEmptyOwnedCollection() {
        rule.setContent {
            MaterialTheme {
                CollectionOverviewScreen(
                    catalog = emptyList(), collection = emptyList(),
                    buildPlans = emptyList(), pendingCount = 0,
                    onAdd = {}, onInbox = {}, onSelectOwned = {},
                    onOpenIvTargets = {}
                )
            }
        }
        rule.onNodeWithTag("collection-guide-compare").performClick()
        rule.onNodeWithTag("collection-overview-scroll")
            .performScrollToNode(hasTestTag("go-search-panel"))
        rule.onNodeWithTag("go-search-copy").performClick()
        rule.runOnIdle {
            assertEquals("0-2attack&2-4defense&2-4hp", clipText())
        }
        rule.onNodeWithTag("go-search-safety")
            .assertTextContains("Não transfiras Pokémon", substring = true)
    }
}
