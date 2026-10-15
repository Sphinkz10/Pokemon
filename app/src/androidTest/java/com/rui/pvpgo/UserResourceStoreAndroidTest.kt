package com.rui.pvpgo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Requires an Android emulator/device: not counted by the JVM CI suite. */
@RunWith(AndroidJUnit4::class)
class UserResourceStoreAndroidTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("resources_instrumentation_test", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After fun tearDown() {
        context.getSharedPreferences("resources_instrumentation_test", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun freshStore(): UserResourceStore = UserResourceStore(
        context.getSharedPreferences("resources_instrumentation_test", Context.MODE_PRIVATE)
    )

    @Test fun unsetResourcesStayUnknownAcrossNewStoreInstance() {
        assertEquals(0, freshStore().load("line-a").knownCount)
        assertNull(UserResourcePolicy.completeLedgerOrNull(freshStore().load("line-a"), "line-a"))
    }

    @Test fun explicitZeroSurvivesRestartAndDoesNotLeakFamilies() {
        freshStore().save("line-a", UserResourceBalances(
            stardust = 0, candy = 0, xlCandy = 5,
            fastTm = 0, chargedTm = 2, eliteFastTm = 0, eliteChargedTm = 0
        ))
        val a = freshStore().load("line-a")
        val b = freshStore().load("line-b")
        assertEquals(0, a.stardust)
        assertEquals(0, a.candy)
        assertEquals(5, a.xlCandy)
        assertNull(b.candy)
        assertNull(b.xlCandy)
        assertEquals(0, b.stardust)
        assertEquals(2, b.chargedTm)
    }

    @Test fun clearingFamilyCandyDoesNotDeleteTheGlobalOrOtherFamilyData() {
        val saved = UserResourceBalances(1000, 8, 4, 1, 2, 0, 0)
        freshStore().save("line-a", saved)
        freshStore().save("line-b", saved.copy(candy = 40, xlCandy = 7))
        freshStore().save("line-a", saved.copy(candy = null, xlCandy = null))
        assertNull(freshStore().load("line-a").candy)
        assertNull(freshStore().load("line-a").xlCandy)
        assertEquals(40, freshStore().load("line-b").candy)
        assertEquals(7, freshStore().load("line-b").xlCandy)
        assertEquals(1000, freshStore().load("line-a").stardust)
    }

    @Test fun invalidBalanceIsNeverSaved() {
        var rejected = false
        try {
            freshStore().save("line-a", UserResourceBalances(stardust = -3))
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertNull(freshStore().load("line-a").stardust)
    }
}
