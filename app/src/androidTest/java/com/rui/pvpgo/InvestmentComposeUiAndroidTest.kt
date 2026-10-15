package com.rui.pvpgo

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genuine Compose semantics/interaction tests on an Android emulator.
 * All storage writes go to isolated instrumentation-only preferences.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentComposeUiAndroidTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var context: Context
    private lateinit var store: UserResourceStore
    private val prefsName = "compose-ui-test-resources-v3"
    private val family = "ui-family-25"
    private val species = PokemonSpecies(
        dex = 25, speciesId = "ui-fixture-25", familyId = family,
        name = "Teste Compose", baseAttack = 105,
        baseDefense = 115, baseStamina = 120
    )
    private val iv = IvSpread(0, 15, 15)
    private val owned = OwnedPokemon(
        id = "ui-owned-25", speciesId = species.speciesId,
        iv = iv, cp = PokemonMath.cp(species, iv, 20.0),
        level = 20.0, createdAtEpochMs = 1L
    )

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = UserResourceStore(
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        )
    }

    @After fun cleanup() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun renderResources(familyId: String = family) {
        rule.setContent {
            var balances by remember(familyId) { mutableStateOf(store.load(familyId)) }
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UserResourcePanel(
                        familyId = familyId, familyLabel = "Teste Compose",
                        saved = balances, store = store, onSaved = { balances = it }
                    )
                }
            }
        }
    }

    private fun renderInvestment(
        specimen: OwnedPokemon = owned,
        league: League = League.MASTER,
        pending: Boolean = false
    ) {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InvestmentTargetPanel(
                        owned = specimen, species = species, league = league,
                        hasPendingBuildChanges = pending,
                        resourceStoreOverride = store
                    )
                }
            }
        }
    }

    private fun openEditor() {
        rule.onNodeWithTag("resource-toggle").performScrollTo().performClick()
    }

    private fun enter(field: String, value: String) {
        rule.onNodeWithTag("resource-input-$field")
            .performScrollTo().performTextInput(value)
    }

    private fun saveEditor() {
        rule.onNodeWithTag("resource-save").performScrollTo().performClick()
    }

    @Test fun editorStartsClosedAndSaysUnknownIsNotZero() {
        renderResources()
        rule.onNodeWithTag("resource-save").assertDoesNotExist()
        rule.onNodeWithText("0/7 saldos registados · Candy de Teste Compose")
            .assertTextContains("0/7", substring = true)
        rule.onNodeWithText(
            "Os saldos em falta continuam desconhecidos, não são considerados zero."
        ).assertTextContains("desconhecidos", substring = true)
    }

    @Test fun userCanExpandTypeNumbersSaveAndSeeUpdatedKnownCount() {
        renderResources()
        openEditor()
        enter("stardust", "12345")
        enter("candy", "0")
        saveEditor()
        rule.onNodeWithTag("resource-save").assertDoesNotExist()
        rule.onNodeWithText("2/7 saldos registados · Candy de Teste Compose")
            .assertTextContains("2/7", substring = true)
        rule.runOnIdle {
            assertEquals(12345, store.load(family).stardust)
            assertEquals(0, store.load(family).candy)
            assertNull(store.load(family).xlCandy)
        }
    }

    @Test fun clearingAnExistingBalanceReturnsToUnknownNotZero() {
        store.save(family, UserResourceBalances(stardust = 500, candy = 12))
        renderResources()
        openEditor()
        rule.onNodeWithTag("resource-input-candy").performScrollTo().performTextClearance()
        saveEditor()
        rule.runOnIdle {
            assertNull(store.load(family).candy)
            assertEquals(500, store.load(family).stardust)
            assertEquals(1, store.load(family).knownCount)
        }
    }

    @Test fun nonNumericInputCannotReplaceACorrectRecordedQuantity() {
        store.save(family, UserResourceBalances(stardust = 75))
        renderResources()
        openEditor()
        rule.onNodeWithTag("resource-input-stardust")
            .performScrollTo().performTextInput("xyz")
        saveEditor()
        rule.runOnIdle {
            assertEquals(75, store.load(family).stardust)
        }
    }

    @Test fun closingAnUncommittedEditDoesNotChangePrivatePreferences() {
        renderResources()
        openEditor()
        enter("stardust", "999")
        rule.onNodeWithTag("resource-toggle").performScrollTo().performClick()
        rule.runOnIdle {
            assertNull(store.load(family).stardust)
        }
    }

    @Test fun editingOneFamilyDoesNotPutCandyIntoAnotherFamily() {
        renderResources("alpha")
        openEditor()
        enter("candy", "23")
        enter("xl_candy", "4")
        saveEditor()
        rule.runOnIdle {
            assertEquals(23, store.load("alpha").candy)
            assertEquals(4, store.load("alpha").xlCandy)
            assertNull(store.load("beta").candy)
            assertNull(store.load("beta").xlCandy)
        }
    }

    @Test fun tappingMasterMaximumLevelOnlyUpdatesTheDraftTarget() {
        renderInvestment()
        rule.onNodeWithTag("investment-level-input").assertTextContains("", substring = true)
        rule.onNodeWithTag("cap-target-button").performScrollTo().performClick()
        rule.onNodeWithTag("investment-level-input").assertTextContains("50", substring = true)
        rule.runOnIdle {
            assertEquals(20.0, owned.level!!, 0.0)
            assertEquals(0, store.load(family).knownCount)
        }
    }

    @Test fun pendingUnsavedBuildDisablesLeagueTargetAndCostConsent() {
        renderInvestment(pending = true)
        rule.onNodeWithTag("cap-target-button").assertIsNotEnabled()
        rule.onNodeWithTag("community-estimate-consent").assertDoesNotExist()
        rule.onNodeWithTag("affordable-target-button").assertDoesNotExist()
        rule.onNodeWithText(
            "Guarda as alterações ao Build real antes de sugerir um nível."
        ).assertTextContains("Guarda", substring = true)
    }

    @Test fun mismatchedStoredCpDisablesTheAutomaticTarget() {
        renderInvestment(specimen = owned.copy(cp = owned.cp!! + 1))
        rule.onNodeWithTag("cap-target-button").assertIsNotEnabled()
        rule.onNodeWithTag("community-estimate-consent").assertDoesNotExist()
        rule.onNodeWithTag("affordable-level-result").assertDoesNotExist()
    }

    @Test fun optedInZeroXlShowsAffordableLevelFortyAndCanUseItAsDraft() {
        store.save(family, UserResourceBalances(600000, 500, 0, 0, 0, 0, 0))
        renderInvestment()
        rule.onNodeWithTag("affordable-target-button").assertDoesNotExist()
        rule.onNodeWithTag("community-estimate-consent")
            .performScrollTo().performClick()
        rule.onNodeWithTag("affordable-level-result")
            .assertTextContains("40", substring = true)
        rule.onNodeWithTag("affordable-target-button")
            .performScrollTo().performClick()
        rule.onNodeWithTag("investment-level-input").assertTextContains("40", substring = true)
        rule.runOnIdle {
            assertEquals(20.0, owned.level!!, 0.0)
            assertEquals(0, store.load(family).xlCandy)
        }
    }
}
