package com.rui.pvpgo

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real production MainActivity, on a genuine emulator (no injected app root).
 * Tests lifecycle survival AND meaningful screen-reader navigation semantics.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleA11yAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun selectCollection() {
        rule.onNodeWithTag("app-tab-collection").performClick()
        rule.onNodeWithTag("app-tab-collection").assertIsSelected()
        rule.onNodeWithTag("app-tab-home").assertIsNotSelected()
    }

    private fun assertCollectionRestored() {
        rule.waitUntil(20000) {
            rule.onAllNodes(hasTestTag("app-tab-collection") and isSelected())
                .fetchSemanticsNodes().size == 1
        }
        rule.onNodeWithTag("app-tab-collection")
            .assertIsSelected()
            .assertContentDescriptionEquals("Coleção")
    }

    @Test fun navigationTabsHaveSelectedRoleAndReadableLabels() {
        rule.onNodeWithTag("app-tab-home")
            .assertIsSelected()
            .assertContentDescriptionEquals("Hoje")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        selectCollection()
        rule.onNodeWithTag("app-tab-collection")
            .assertContentDescriptionEquals("Coleção")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        rule.onNodeWithTag("app-tab-home").performClick()
        rule.onNodeWithTag("app-tab-home").assertIsSelected()
    }

    @Test fun selectedCollectionTabSurvivesRealAndroidActivityRecreation() {
        selectCollection()
        rule.activityRule.scenario.recreate()
        assertCollectionRestored()
    }

    @Test fun selectedCollectionTabSurvivesLeavingAndResumingApplication() {
        selectCollection()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertCollectionRestored()
        rule.activityRule.scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
        }
    }

    @Test fun selectedCollectionTabSurvivesActualLandscapeConfigurationChange() {
        selectCollection()
        rule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        rule.waitUntil(25000) {
            runCatching {
                rule.activity.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
            }.getOrDefault(false)
        }
        assertCollectionRestored()
    }
}
