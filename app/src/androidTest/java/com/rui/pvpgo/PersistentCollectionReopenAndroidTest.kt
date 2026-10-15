package com.rui.pvpgo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Named ON-DISK SQLite database on Android, closed and reopened with a NEW
 * Room instance. The previous in-memory tests did not exercise filesystem
 * persistence. This still does NOT simulate Android process death or APK
 * replacement: those require a separate cross-process, same-signature gate.
 */
@RunWith(AndroidJUnit4::class)
class PersistentCollectionReopenAndroidTest {
    private lateinit var context: Context
    private val filename = "pr24-persistence-isolated.db"

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        check(context.deleteDatabase(filename) || !context.getDatabasePath(filename).exists())
    }

    @After fun cleanup() {
        context.deleteDatabase(filename)
    }

    private fun open(): PvpUserDatabase =
        Room.databaseBuilder(context, PvpUserDatabase::class.java, filename)
            .addMigrations(PvpUserDatabase.MIGRATION_1_2)
            .build()

    private fun owned(id: String = "persistent-25") = OwnedPokemonEntity(
        id = id, speciesId = "pikachu", ivAttack = 0, ivDefense = 15,
        ivStamina = 15, cp = 495, level = 20.0, nickname = "Parceiro",
        isShadow = false, isPurified = false, isShiny = true,
        isLucky = false, isBestBuddy = false, isFavorite = true,
        fastMoveId = null, chargedMoveIds = "", secondChargedMoveUnlocked = null,
        tags = "pvp", acquisitionMethod = "UNKNOWN", caughtAtEpochMs = null,
        createdAtEpochMs = 100L, updatedAtEpochMs = 100L,
        externalSource = null, externalId = null, lastVerifiedAtEpochMs = null,
        verificationSource = "UNVERIFIED", uncertainFields = ""
    )

    private fun team() = SavedTeamEntity(
        id = "reopen-team", name = "O trio", league = "GREAT",
        members = "persistent-25:LEAD", isPrimary = true, notes = "Preservar",
        createdAtEpochMs = 100L, updatedAtEpochMs = 100L
    )

    private fun plan() = PvpBuildPlanEntity(
        id = "reopen-plan", ownedPokemonId = "persistent-25",
        league = "GREAT", bestBuddyAllowed = false,
        desiredFastMoveId = null, desiredChargedMoveIds = "",
        status = "IDEA", priority = 90, notes = "Poupar Stardust",
        createdAtEpochMs = 100L, updatedAtEpochMs = 100L
    )

    private fun candidate() = CandidateInboxEntity(
        id = "candidate-one", candidateOwnedId = "pending-25",
        speciesId = "pikachu", ivAttack = 0, ivDefense = 15, ivStamina = 15,
        cp = 400, level = 18.0, nickname = "Pendente", isShadow = false,
        isPurified = false, isShiny = false, isLucky = false,
        isBestBuddy = false, isFavorite = false, fastMoveId = null,
        chargedMoveIds = "", secondChargedMoveUnlocked = null, tags = "",
        acquisitionMethod = "UNKNOWN", caughtAtEpochMs = null,
        candidateCreatedAtEpochMs = 100L, candidateUpdatedAtEpochMs = 100L,
        externalSource = null, externalId = null, lastVerifiedAtEpochMs = null,
        verificationSource = "UNVERIFIED", uncertainFields = "",
        createdAtEpochMs = 100L, status = "PENDING",
        reviewedAtEpochMs = null, linkedOwnedPokemonId = null
    )

    private fun battle() = BattleRecordEntity(
        id = "battle-one", league = "GREAT", outcome = "WIN",
        playedAtEpochMs = 100L, ownTeamId = "reopen-team",
        ownPokemon = "persistent-25\u001DLEAD", opponentPokemon = "",
        ratingBefore = 1200, ratingAfter = 1214,
        notes = "Registo histórico", tags = ""
    )

    @Test fun wholeOwnedSpecimenSurvivesActualFileCloseAndReopen() = runBlocking {
        val original = owned()
        open().use { it.collectionDao().upsertOwned(original) }
        open().use {
            assertEquals(original, it.collectionDao().collection().single())
            assertTrue(context.getDatabasePath(filename).exists())
        }
    }

    @Test fun savedBuildEditsAndExplicitUnknownCpArePreservedAcrossReopen() = runBlocking {
        open().use {
            val dao = it.collectionDao()
            dao.upsertOwned(owned())
            dao.upsertOwned(owned().copy(cp = null, level = null, nickname = "Por verificar", updatedAtEpochMs = 200L))
        }
        open().use {
            val item = it.collectionDao().collection().single()
            assertNull(item.cp) // UNKNOWN is never silently made zero
            assertNull(item.level)
            assertEquals("Por verificar", item.nickname)
            assertEquals(200L, item.updatedAtEpochMs)
        }
    }

    @Test fun savedTeamAndBuildPlanSurviveSeparateRoomInstance() = runBlocking {
        open().use {
            it.collectionDao().upsertOwned(owned())
            it.collectionDao().upsertSavedTeam(team())
            it.collectionDao().upsertBuildPlan(plan())
        }
        open().use {
            val dao = it.collectionDao()
            assertEquals(team(), dao.observeSavedTeams().first().single())
            assertEquals(plan(), dao.observeBuildPlans().first().single())
            assertEquals("persistent-25", dao.collection().single().id)
        }
    }

    @Test fun pendingCandidateDoesNotBecomeOwnedAutomaticallyOnReopen() = runBlocking {
        open().use { it.collectionDao().upsertInbox(candidate()) }
        open().use {
            val dao = it.collectionDao()
            assertTrue(dao.collection().isEmpty())
            assertEquals("PENDING", dao.observePendingInbox().first().single().status)
            assertEquals("candidate-one", dao.observeInbox().first().single().id)
        }
    }

    @Test fun completedBattleRecordSurvivesReopenWithoutChangingTeam() = runBlocking {
        open().use {
            it.collectionDao().upsertSavedTeam(team())
            it.collectionDao().upsertBattleRecord(battle())
        }
        open().use {
            val dao = it.collectionDao()
            assertEquals(battle(), dao.observeBattleRecords().first().single())
            assertEquals(team(), dao.observeSavedTeams().first().single())
        }
    }

    @Test fun explicitDeletionPersistsAndOtherOwnedRecordsStaySafe() = runBlocking {
        open().use {
            val dao = it.collectionDao()
            dao.upsertOwned(owned("delete-me"))
            dao.upsertOwned(owned("keep-me"))
            dao.deleteOwned("delete-me")
        }
        open().use {
            assertEquals("keep-me", it.collectionDao().collection().single().id)
        }
    }

    @Test fun largeCollectionCanBeReopenedWithStableIdsAndNoDataLoss() = runBlocking {
        val count = 240
        open().use { db ->
            for (n in 1..count) {
                db.collectionDao().upsertOwned(owned("batch-$n").copy(
                    cp = 500 + n,
                    nickname = "Exemplar $n",
                    updatedAtEpochMs = n.toLong()
                ))
            }
        }
        open().use {
            val rows = it.collectionDao().collection()
            assertEquals(count, rows.size)
            assertEquals(count, rows.map { item -> item.id }.toSet().size)
            assertTrue(rows.any { item -> item.id == "batch-1" && item.cp == 501 })
            assertTrue(rows.any { item -> item.id == "batch-240" && item.cp == 740 })
        }
    }

    @Test fun reopenDoesNotCreateResourcesOrInventUnverifiedFields() = runBlocking {
        open().use { it.collectionDao().upsertOwned(owned().copy(
            cp = null, level = null, lastVerifiedAtEpochMs = null,
            verificationSource = "UNVERIFIED", uncertainFields = "CP\u001FLEVEL"
        )) }
        open().use {
            val result = it.collectionDao().collection().single()
            assertNull(result.cp)
            assertNull(result.level)
            assertNull(result.lastVerifiedAtEpochMs)
            assertEquals("UNVERIFIED", result.verificationSource)
            assertEquals("CP\u001FLEVEL", result.uncertainFields)
        }
    }
}
