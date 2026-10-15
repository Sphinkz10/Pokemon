package com.rui.pvpgo

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual Android lifecycle smoke test. The real launcher activity must
 * create and reach foreground without throwing, not just compile Kotlin.
 * Does not imply the app's screens and complete journeys have passed QA.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchAndroidTest {
    @Test fun mainActivityCanLaunchAndResumeOnEmulator() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
                assertTrue(activity.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED
                ))
            }
        } finally {
            scenario.close()
        }
    }
}
