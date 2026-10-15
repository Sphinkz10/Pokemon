package com.rui.pvpgo

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TWO-PHASE cross-APK test, invoked ONLY from the upgrade workflow:
 *
 * 1. Install v58 APK, install latest test-only instrumentation APK signed with
 *    the same debug key, invoke seedPriorVersionAndConfirmDiskWrites().
 * 2. adb force-stop (real process kill), run verifyAfterProcessRestart() while
 *    the installed target APK remains v58.
 * 3. adb install -r v59 APK, run verifyAfterApkUpgrade() with identical test APK.
 *
 * The Gradle -PupgradeSuite switch ensures these order-dependent methods are
 * never silently included in the ordinary 51 independent Android tests.
 */
@RunWith(AndroidJUnit4::class)
class UpgradePersistenceAndroidTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val packageId = "com.rui.pvpgo.installtestv190"
    private val family = "upgrade-family"
    private val original = OwnedPokemonEntity(
        id = "upgrade-one", speciesId = "pikachu",
        ivAttack = 0, ivDefense = 15, ivStamina = 15,
        cp = 495, level = 20.0, nickname = "Não perder",
        isShadow = false, isPurified = false, isShiny = true,
        isLucky = false, isBestBuddy = false, isFavorite = true,
        fastMoveId = null, chargedMoveIds = "",
        secondChargedMoveUnlocked = null,
        tags = "pvp", acquisitionMethod = "UNKNOWN",
        caughtAtEpochMs = null, createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L, externalSource = null, externalId = null,
        lastVerifiedAtEpochMs = null, verificationSource = "UNVERIFIED",
        uncertainFields = ""
    )

    private fun versionCode(): Long {
        val info = context.packageManager.getPackageInfo(packageId, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    @Test fun seedPriorVersionAndConfirmDiskWrites() = runBlocking {
        assertEquals("Seeding must happen on original v58 APK", 58L, versionCode())
        assertEquals(packageId, context.packageName)
        val dao = PvpUserDatabase.get(context).collectionDao()
        assertTrue("Seeding must start from an EMPTY prior installation", dao.collection().isEmpty())
        dao.upsertOwned(original)
        dao.upsertOwned(original.copy(
            id = "upgrade-two", ivAttack = 15, ivDefense = 15,
            ivStamina = 15, cp = null, level = null,
            nickname = "Por verificar", isFavorite = false,
            verificationSource = "UNVERIFIED", uncertainFields = "CP\u001FLEVEL"
        ))
        dao.upsertSavedTeam(SavedTeamEntity(
            id = "upgrade-team", name = "Equipa guardada", league = "GREAT",
            members = "upgrade-one:LEAD\u001Fupgrade-two:SAFE_SWITCH",
            isPrimary = true, notes = "Manter nesta instalação",
            createdAtEpochMs = 100L, updatedAtEpochMs = 100L
        ))
        dao.upsertBuildPlan(PvpBuildPlanEntity(
            id = "upgrade-plan", ownedPokemonId = "upgrade-one",
            league = "GREAT", bestBuddyAllowed = false,
            desiredFastMoveId = null, desiredChargedMoveIds = "",
            status = "IDEA", priority = 90, notes = "Stardust",
            createdAtEpochMs = 100L, updatedAtEpochMs = 100L
        ))
        dao.upsertInbox(CandidateInboxEntity(
            id = "upgrade-pending", candidateOwnedId = "upgrade-third",
            speciesId = "pikachu", ivAttack = 12, ivDefense = 12, ivStamina = 12,
            cp = null, level = null, nickname = "Por confirmar",
            isShadow = false, isPurified = false, isShiny = false,
            isLucky = false, isBestBuddy = false, isFavorite = false,
            fastMoveId = null, chargedMoveIds = "",
            secondChargedMoveUnlocked = null, tags = "",
            acquisitionMethod = "UNKNOWN", caughtAtEpochMs = null,
            candidateCreatedAtEpochMs = 100L, candidateUpdatedAtEpochMs = 100L,
            externalSource = null, externalId = null,
            lastVerifiedAtEpochMs = null, verificationSource = "UNVERIFIED",
            uncertainFields = "CP\u001FLEVEL",
            createdAtEpochMs = 100L, status = "PENDING",
            reviewedAtEpochMs = null, linkedOwnedPokemonId = null
        ))
        dao.upsertBattleRecord(BattleRecordEntity(
            id = "upgrade-battle", league = "GREAT", outcome = "WIN",
            playedAtEpochMs = 100L, ownTeamId = "upgrade-team",
            ownPokemon = "upgrade-one\u001DLEAD",
            opponentPokemon = "", ratingBefore = 1200, ratingAfter = 1212,
            notes = "Histórico de batalhas", tags = ""
        ))
        UserResourceStore.get(context).save(
            family, UserResourceBalances(
                stardust = 876543, candy = 0, xlCandy = 14,
                fastTm = 4, chargedTm = 9,
                eliteFastTm = null, eliteChargedTm = 1
            )
        )
        assertTrue(context.getDatabasePath("user.db").exists())
        assertBaselineContents()
    }

    private suspend fun assertBaselineContents() {
        val dao = PvpUserDatabase.get(context).collectionDao()
        val saved = dao.collection()
        assertEquals(2, saved.size)
        assertEquals(setOf("upgrade-one", "upgrade-two"), saved.map { it.id }.toSet())
        val first = saved.single { it.id == original.id }
        assertEquals(original, first)
        val second = saved.single { it.id == "upgrade-two" }
        assertNull(second.cp)
        assertNull(second.level)
        assertFalse(second.isFavorite)
        assertEquals("CP\u001FLEVEL", second.uncertainFields)
        val team = dao.observeSavedTeams().first().single()
        assertEquals("upgrade-team", team.id)
        assertEquals("Equipa guardada", team.name)
        assertEquals("upgrade-one:LEAD\u001Fupgrade-two:SAFE_SWITCH", team.members)
        assertEquals("upgrade-plan", dao.observeBuildPlans().first().single().id)
        assertEquals("Stardust", dao.observeBuildPlans().first().single().notes)
        assertEquals("upgrade-pending", dao.observePendingInbox().first().single().id)
        assertEquals("PENDING", dao.observeInbox().first().single().status)
        assertEquals("upgrade-battle", dao.observeBattleRecords().first().single().id)
        assertEquals("Histórico de batalhas", dao.observeBattleRecords().first().single().notes)

        val resources = UserResourceStore.get(context).load(family)
        assertEquals(876543, resources.stardust)
        assertEquals(0, resources.candy) // explicit zero is NOT unknown
        assertEquals(14, resources.xlCandy)
        assertEquals(4, resources.fastTm)
        assertEquals(9, resources.chargedTm)
        assertNull(resources.eliteFastTm) // unknown is NOT zero
        assertEquals(1, resources.eliteChargedTm)
        assertNull(UserResourceStore.get(context).load("other-family").candy)
    }

    @Test fun verifyAfterRealForceStopOnPreviousVersion() = runBlocking {
        assertEquals("Must still be v58 after force-stop", 58L, versionCode())
        assertTrue(context.getDatabasePath("user.db").exists())
        assertBaselineContents()
    }

    @Test fun verifyAfterSignedApkReplacement() = runBlocking {
        assertEquals("Must really be the upgraded v59 APK", 59L, versionCode())
        assertTrue(context.getDatabasePath("user.db").exists())
        assertBaselineContents()
    }
}
