package com.rui.pvpgo

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Called three times via "am instrument" across two signed APK versions.
 * Also runs once in standard connectedDebugAndroidTest (baseline smoke only).
 * Uses production user.db and production resource SharedPreferences in the
 * diagnostic package's isolated sandbox. No mocks, no migration reset.
 */
@RunWith(AndroidJUnit4::class)
class ActualApkUpgradeAndroidTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val family = "pr25-upgrade-family-25"
    private val ownedId = "pr25-upgrade-owned-25"

    private fun installedVersion(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private suspend fun seed() {
        assertEquals(58L, installedVersion())
        val dao = PvpUserDatabase.get(context).collectionDao()
        dao.upsertOwned(OwnedPokemonEntity(
            id = ownedId, speciesId = "pikachu", ivAttack = 0,
            ivDefense = 15, ivStamina = 15, cp = 499, level = 20.0,
            nickname = "Guardado antes da atualização",
            isShadow = false, isPurified = false, isShiny = true,
            isLucky = false, isBestBuddy = false, isFavorite = true,
            fastMoveId = null, chargedMoveIds = "",
            secondChargedMoveUnlocked = null, tags = "favorito",
            acquisitionMethod = "UNKNOWN", caughtAtEpochMs = null,
            createdAtEpochMs = 100L, updatedAtEpochMs = 100L,
            externalSource = null, externalId = null,
            lastVerifiedAtEpochMs = null,
            verificationSource = "UNVERIFIED", uncertainFields = ""
        ))
        dao.upsertSavedTeam(SavedTeamEntity(
            id = "pr25-upgrade-team", name = "Equipa antes de atualizar",
            league = "GREAT", members = "$ownedId:LEAD",
            isPrimary = true, notes = "Preservar",
            createdAtEpochMs = 100L, updatedAtEpochMs = 100L
        ))
        dao.upsertBuildPlan(PvpBuildPlanEntity(
            id = "pr25-upgrade-plan", ownedPokemonId = ownedId,
            league = "GREAT", bestBuddyAllowed = false,
            desiredFastMoveId = null, desiredChargedMoveIds = "",
            status = "IDEA", priority = 84, notes = "Não perder plano",
            createdAtEpochMs = 100L, updatedAtEpochMs = 100L
        ))
        UserResourceStore.get(context).save(family,
            UserResourceBalances(stardust = 54321, candy = 12, xlCandy = 0)
        )
        check(
            context.getSharedPreferences("pr25-upgrade-marker", Context.MODE_PRIVATE)
                .edit().putString("baseline", "58-initialized").commit()
        )
    }

    private suspend fun verify(expectedVersion: Long) {
        assertEquals(expectedVersion, installedVersion())
        val marker = context.getSharedPreferences(
            "pr25-upgrade-marker", Context.MODE_PRIVATE
        ).getString("baseline", null)
        assertEquals("58-initialized", marker)

        val dao = PvpUserDatabase.get(context).collectionDao()
        val row = dao.ownedById(ownedId)
        assertEquals(ownedId, row?.id)
        assertEquals("pikachu", row?.speciesId)
        assertEquals(0, row?.ivAttack)
        assertEquals(15, row?.ivDefense)
        assertEquals(15, row?.ivStamina)
        assertEquals(499, row?.cp)
        assertEquals(20.0, row?.level)
        assertEquals("Guardado antes da atualização", row?.nickname)
        assertTrue(row?.isShiny == true)
        assertTrue(row?.isFavorite == true)
        assertEquals("UNVERIFIED", row?.verificationSource)
        assertNull(row?.lastVerifiedAtEpochMs)
        assertEquals("Equipa antes de atualizar", dao.observeSavedTeams().first()
            .single { it.id == "pr25-upgrade-team" }.name)
        assertEquals("Não perder plano", dao.observeBuildPlans().first()
            .single { it.id == "pr25-upgrade-plan" }.notes)
        val balances = UserResourceStore.get(context).load(family)
        assertEquals(54321, balances.stardust)
        assertEquals(12, balances.candy)
        assertEquals(0, balances.xlCandy)
        assertNull(balances.fastTm)
        assertNull(UserResourceStore.get(context).load("another-family").candy)
    }

    @Test fun actualSameSignatureApkUpgradeRetainsUserData() = runBlocking {
        when (val stage = InstrumentationRegistry.getArguments()
            .getString("upgrade_stage", "standard")) {
            "seed_baseline" -> { seed(); verify(58L) }
            "after_force_stop" -> verify(58L)
            "after_upgrade" -> verify(59L)
            "standard" -> {
                // Ordinary 52-case emulator CI runs independently of the
                // three-stage upgrade workflow. Never mutate its production DB.
                assertEquals(59L, installedVersion())
                assertFalse(context.packageName.isBlank())
            }
            else -> error("Unknown upgrade stage: $stage")
        }
    }
}
