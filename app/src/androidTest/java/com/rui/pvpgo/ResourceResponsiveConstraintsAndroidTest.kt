package com.rui.pvpgo

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deterministic Compose constraints on real Android, not claims about physical
 * hardware, screenshots, TalkBack, dynamic fonts across every app screen or
 * full UI accessibility. Ensures critical inventory form remains operable.
 */
@RunWith(AndroidJUnit4::class)
class ResourceResponsiveConstraintsAndroidTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var context: Context
    private lateinit var store: UserResourceStore
    private val prefsName = "pr24-layout-instrumentation"
    private val family = "pr24-family"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val p = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        p.edit().clear().commit()
        store = UserResourceStore(p)
    }
    @After fun tearDown() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun render(widthDp: Int, fontScale: Float) {
        rule.setContent {
            var balances by remember { mutableStateOf(store.load(family)) }
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MaterialTheme {
                    Column(
                        Modifier.width(widthDp.dp).height(640.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        UserResourcePanel(
                            familyId = family, familyLabel = "Pikachu",
                            saved = balances, store = store,
                            onSaved = { balances = it }
                        )
                    }
                }
            }
        }
    }

    private fun addAndSave() {
        rule.onNodeWithTag("resource-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("resource-input-stardust")
            .performScrollTo().performTextInput("901")
        rule.onNodeWithTag("resource-input-candy")
            .performScrollTo().performTextInput("0")
        rule.onNodeWithTag("resource-save").performScrollTo().performClick()
        rule.onNodeWithTag("resource-toggle").assertTextContains(
            "Registar ou atualizar recursos", substring = true
        )
        rule.runOnIdle {
            assertEquals(901, store.load(family).stardust)
            assertEquals(0, store.load(family).candy)
            assertNull(store.load(family).xlCandy)
        }
    }

    @Test fun narrow320dpAtTwoHundredPercentTextAllowsSaveAndExplicitZero() {
        render(widthDp = 320, fontScale = 2f)
        addAndSave()
    }

    @Test fun phone390dpAtOneHundredFiftyPercentTextAllowsSave() {
        render(widthDp = 390, fontScale = 1.5f)
        addAndSave()
    }

    @Test fun tablet600dpAtDefaultTextAllowsSave() {
        render(widthDp = 600, fontScale = 1f)
        addAndSave()
    }
}
