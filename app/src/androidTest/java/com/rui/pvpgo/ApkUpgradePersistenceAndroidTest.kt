package com.rui.pvpgo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run using two DISTINCT instrumentation invocations separated by force-stop
 * and signed APK replacement. NEVER clear app data between seed and verify.
 *
 * Baseline PR24 v58 and upgraded PR25 v59 have identical Room v2 schemas.
 * The workflow builds both app APKs with the SAME runner debug signing key.
 */
@RunWith(AndroidJUnit4::class)
class ApkUpgradePersistenceAndroidTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val family = "apk-upgrade-family-pr25"

    private fun database() =
        Room.databaseBuilder(context, PvpUserDatabase::class.java, "user.db")
            .addMigrations(PvpUserDatabase.MIGRATION_1_2)
            .build()

    private fun owned() = OwnedPokemonEntity(
        id = "apk-upgrade-owned", speciesId = "pikachu",
        ivAttack = 0, ivDefense = 15, ivStamina = 15,
        cp = 500, level = 20.0, nickname = "Não apagar",
        isShadow = false, isPurified = false, isShiny = true,
        isLucky = false, isBestBuddy = false, isFavorite = true,
        fastMoveId = null, chargedMoveIds = "",
        secondChargedMoveUnlocked = null, tags = "PvP",
        acquisitionMethod = "UNKNOWN", caughtAtEpochMs = null,
        createdAtEpochMs = 1234L, updatedAtEpochMs = 1234L,
        externalSource = null, externalId = null, lastVerifiedAtEpochMs = null,
        verificationSource = "UNVERIFIED", uncertainFields = ""
    )

    private fun team() = SavedTeamEntity(
        id = "apk-upgrade-team", name = "Equipa que sobrevive",
        league = "GREAT", members = "apk-upgrade-owned:LEAD",
        isPrimary = true, notes = "Equipa original",
        createdAtEpochMs = 1234L, updatedAtEpochMs = 1234L
    )

    private fun plan() = PvpBuildPlanEntity(
        id = "apk-upgrade-plan", ownedPokemonId = "apk-upgrade-owned",
        league = "GREAT", bestBuddyAllowed = false,
        desiredFastMoveId = null, desiredChargedMoveIds = "",
        status = "IDEA", priority = 99, notes = "Poupar energia",
        createdAtEpochMs = 1234L, updatedAtEpochMs = 1234L
    )

    private fun candidate() = CandidateInboxEntity(
        id = "apk-upgrade-inbox", candidateOwnedId = "apk-upgrade-pending",
        speciesId = "pikachu", ivAttack = 2, ivDefense = 14, ivStamina = 15,
        cp = null, level = null, nickname = "Por confirmar",
        isShadow = false, isPurified = false, isShiny = false,
        isLucky = false, isBestBuddy = false, isFavorite = false,
        fastMoveId = null, chargedMoveIds = "", secondChargedMoveUnlocked = null,
        tags = "", acquisitionMethod = "UNKNOWN", caughtAtEpochMs = null,
        candidateCreatedAtEpochMs = 1234L, candidateUpdatedAtEpochMs = 1234L,
        externalSource = null, externalId = null, lastVerifiedAtEpochMs = null,
        verificationSource = "UNVERIFIED", uncertainFields = "",
        createdAtEpochMs = 1234L, status = "PENDING",
        reviewedAtEpochMs = null, linkedOwnedPokemonId = null
    )

    private fun battle() = BattleRecordEntity(
        id = "apk-upgrade-battle", league = "GREAT", outcome = "WIN",
        playedAtEpochMs = 1234L, ownTeamId = "apk-upgrade-team",
        ownPokemon = "apk-upgrade-owned\u001DLEAD",
        opponentPokemon = "", ratingBefore = 1500, ratingAfter = 1514,
        notes = "Resultado histórico", tags = ""
    )

    @Test fun seedExistingApp() = runBlocking {
        val db = database()
        try {
            val dao = db.collectionDao()
            assertTrue("Baseline installation must start with a clean isolated user.db",
                dao.collection().isEmpty())
            dao.upsertOwned(owned())
            dao.upsertSavedTeam(team())
            dao.upsertBuildPlan(plan())
            dao.upsertInbox(candidate())
            dao.upsertBattleRecord(battle())
            assertEquals(1, dao.collection().size)
        } finally { db.close() }

        val resourceStore = UserResourceStore.get(context)
        resourceStore.save(
            family,
            UserResourceBalances(
                stardust = 432100, candy = 0, xlCandy = 296,
                fastTm = 2, chargedTm = 4,
                eliteFastTm = 0, eliteChargedTm = null
            )
        )
        assertEquals(432100, resourceStore.load(family).stardust)
        assertEquals(0, resourceStore.load(family).candy)
        assertNull(resourceStore.load(family).eliteChargedTm)
        assertTrue(context.getDatabasePath("user.db").exists())
    }

    @Test fun verifyPreservedCollectionResources() = runBlocking {
        val db = database()
        try {
            val dao = db.collectionDao()
            assertEquals(owned(), dao.collection().single())
            assertEquals(team(), dao.observeSavedTeams().first().single())
            assertEquals(plan(), dao.observeBuildPlans().first().single())
            assertEquals(candidate(), dao.observeInbox().first().single())
            assertEquals(candidate(), dao.observePendingInbox().first().single())
            assertEquals(battle(), dao.observeBattleRecords().first().single())
            assertFalse("Pending candidate must not silently become owned",
                dao.collection().any { it.id == candidate().candidateOwnedId })
        } finally { db.close() }

        val balances = UserResourceStore.get(context).load(family)
        assertEquals(432100, balances.stardust)
        assertEquals(0, balances.candy) // explicit zero, not UNKNOWN
        assertEquals(296, balances.xlCandy)
        assertEquals(2, balances.fastTm)
        assertEquals(4, balances.chargedTm)
        assertEquals(0, balances.eliteFastTm)
        assertNull(balances.eliteChargedTm) // UNKNOWN cannot become zero
    }
}
