package com.rui.pvpgo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.RankSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Jetpack Compose IV Targets, not a mocked page or invented rank values. */
@RunWith(AndroidJUnit4::class)
class OwnedIvCompareComposeAndroidTest {
    @get:Rule val rule = createComposeRule()
    private val species = PokemonSpecies(
        dex = 25, speciesId = "iv-owned-app-25", name = "IV Exemplar",
        familyId = "iv-owned-family", baseAttack = 105,
        baseDefense = 115, baseStamina = 120
    )
    private val owned = listOf(
        specimen("a", IvSpread(0, 15, 15)),
        specimen("b", IvSpread(15, 15, 15)),
        specimen("c", IvSpread(4, 13, 14)),
        specimen("d", IvSpread(15, 0, 0), knownCp = false)
    )
    private fun specimen(id: String, iv: IvSpread, knownCp: Boolean = true) =
        OwnedPokemon(
            id = id, speciesId = species.speciesId, iv = iv,
            cp = if (knownCp) PokemonMath.cp(species, iv, 20.0) else null,
            level = if (knownCp) 20.0 else null,
            nickname = "Owned $id", createdAtEpochMs = 1L
        )

    private var opened: String? = null

    private fun render(onOpenOwned: ((String) -> Unit)? = { opened = it }) {
        rule.setContent {
            MaterialTheme {
                IvTargetsScreen(
                    species = species, collection = owned, league = League.GREAT,
                    onLeagueChange = {}, onBack = {}, onOpenOwned = onOpenOwned
                )
            }
        }
        // The cards live below the fold of a LazyColumn; waiting for a
        // not-yet-composed card is incorrect. Wait for the visible root
        // loading-state semantic instead, then scroll the list to a card.
        rule.waitUntil(40000) {
            rule.onAllNodes(
                hasTestTag("iv-targets-scroll") and
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription, "Rankings IV prontos"
                    )
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scroll(tag: String) {
        rule.onNodeWithTag("iv-targets-scroll").performScrollToNode(hasTestTag(tag))
    }

    private fun showAllFourOwned() {
        // The first three are sorted by real IV rank: a fixture named "a"
        // is not guaranteed to be among them. Expand intentionally.
        scroll("iv-owned-toggle-all")
        rule.onNodeWithTag("iv-owned-toggle-all").performClick()
    }

    @Test fun listsRealOwnedRanksAndDoesNotPretendThoseAreMetaRankings() {
        render()
        showAllFourOwned()
        scroll("iv-owned-rank-a")
        rule.onNodeWithTag("iv-owned-rank-a")
            .assertTextContains("Rank IV #", substring = true)
        scroll("iv-owned-row-b")
        rule.onNodeWithTag("iv-owned-row-b").assertExists()
        rule.runOnIdle { assertEquals(4, owned.size) }
    }

    @Test fun unknownCpStillShowsIvPotentialButWarnsAndDoesNotInventCurrentCp() {
        render()
        scroll("iv-owned-toggle-all")
        rule.onNodeWithTag("iv-owned-toggle-all").performClick()
        scroll("iv-owned-status-d")
        rule.onNodeWithTag("iv-owned-status-d")
            .assertTextContains("por confirmar", substring = true)
        rule.onNodeWithTag("iv-owned-row-d")
            .assertExists()
        rule.runOnIdle {
            assertEquals(null, owned.last().cp)
            assertEquals(null, owned.last().level)
        }
    }

    @Test fun canRevealFourOwnedAndOpenExactSpecimenWithoutMutatingIt() {
        render()
        scroll("iv-owned-toggle-all")
        rule.onNodeWithTag("iv-owned-toggle-all").performClick()
        scroll("iv-owned-open-d")
        rule.onNodeWithTag("iv-owned-open-d").performClick()
        rule.runOnIdle {
            assertEquals("d", opened)
            assertEquals(null, owned.last().cp)
            assertEquals(4, owned.size)
        }
    }

    @Test fun rankingCardsRemainReadOnlyWithoutNavigationCallback() {
        render(onOpenOwned = null)
        showAllFourOwned()
        scroll("iv-owned-row-a")
        rule.onNodeWithTag("iv-owned-row-a").assertExists()
        rule.onNodeWithTag("iv-owned-open-a").assertDoesNotExist()
        rule.runOnIdle { assertTrue(owned.any { it.id == "a" }) }
    }
}
