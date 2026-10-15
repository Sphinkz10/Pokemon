package com.rui.pvpgo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
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
 * Real Android test: read/write actual SharedPreferences + SQLite Room,
 * then evaluate the exact owned specimen through the production CP and
 * investment engines. Does not assert that Pokémon GO itself was checked.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentJourneyAndroidTest {
    private lateinit var context: Context
    private lateinit var db: PvpUserDatabase
    private lateinit var store: UserResourceStore
    private val family = "journey-family"
    private val species = PokemonSpecies(
        dex = 25, speciesId = "journey-normal", name = "Journey fixture",
        familyId = "journey-family",
        baseAttack = 105, baseDefense = 115, baseStamina = 120
    )
    private val iv = IvSpread(0, 15, 15)
    private val fixture = OwnedPokemon(
        id = "journey-owned", speciesId = species.speciesId, iv = iv,
        level = 20.0, cp = PokemonMath.cp(species, iv, 20.0),
        createdAtEpochMs = 100L
    )

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("journey-android-test-v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = UserResourceStore(
            context.getSharedPreferences("journey-android-test-v1", Context.MODE_PRIVATE)
        )
        db = Room.inMemoryDatabaseBuilder(context, PvpUserDatabase::class.java).build()
    }

    @After fun cleanup() {
        db.close()
        context.getSharedPreferences("journey-android-test-v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private suspend fun writeSavedPokemon() {
        db.collectionDao().upsertOwned(OwnedPokemonEntity(
            id = fixture.id, speciesId = fixture.speciesId,
            ivAttack = iv.attack, ivDefense = iv.defense, ivStamina = iv.stamina,
            cp = fixture.cp, level = fixture.level, nickname = null,
            isShadow = false, isPurified = false, isShiny = false,
            isLucky = false, isBestBuddy = false, isFavorite = false,
            fastMoveId = null, chargedMoveIds = "", secondChargedMoveUnlocked = null,
            tags = "", acquisitionMethod = "UNKNOWN", caughtAtEpochMs = null,
            createdAtEpochMs = 100L, updatedAtEpochMs = 100L,
            externalSource = null, externalId = null,
            lastVerifiedAtEpochMs = null, verificationSource = "UNVERIFIED",
            uncertainFields = ""
        ))
    }

    private fun ledger() =
        UserResourcePolicy.completeLedgerOrNull(store.load(family), family)

    private fun recommendation(owned: OwnedPokemon = fixture, league: League = League.MASTER) =
        AffordableLevelPolicy.advise(
            owned = owned, species = species, league = league,
            consented = true, pack = CommunityNormalPowerUpPack.pack,
            ledger = ledger()
        )

    @Test fun roomSpecimenAndPrivateResourcesProduceAnActuallyAffordableLevel() = runBlocking {
        writeSavedPokemon()
        store.save(family, UserResourceBalances(600000, 500, 500, 0, 0, 0, 0))
        val saved = db.collectionDao().collection().single()
        assertEquals(fixture.id, saved.id)
        assertEquals(fixture.cp, saved.cp)
        assertEquals(fixture.level, saved.level)
        val result = recommendation()
        assertTrue(result.explanation, result.canApply)
        assertEquals(50.0, result.affordableLevel!!, 0.0)
        assertEquals(475000, result.affordablePlan?.costs?.stardust)
        assertEquals(248, result.affordablePlan?.costs?.candyByFamily?.get(family))
        assertEquals(296, result.affordablePlan?.costs?.xlCandyByFamily?.get(family))
        val after = db.collectionDao().collection().single()
        assertEquals(saved, after) // never perform an automatic power-up
        assertEquals(600000, store.load(family).stardust)
    }

    @Test fun recordedZeroCandyLeavesPokemonAtCurrentLevel() = runBlocking {
        writeSavedPokemon()
        store.save(family, UserResourceBalances(600000, 0, 500, 0, 0, 0, 0))
        val result = recommendation()
        assertTrue(result.explanation, result.canApply)
        assertEquals(20.0, result.affordableLevel!!, 0.0)
        assertEquals(0, result.affordableSteps)
        assertTrue(result.nextStepShortages.any { it.kind.name == "CANDY" })
        assertEquals(0, store.load(family).candy)
        assertEquals(fixture.level, db.collectionDao().collection().single().level)
    }

    @Test fun blankCandyInStoredPreferencesIsUnknownNotAnAvailableBalance() {
        store.save(family, UserResourceBalances(600000, null, 500, 0, 0, 0, 0))
        assertEquals(6, store.load(family).knownCount)
        assertNull(ledger())
        assertEquals(AffordableLevelStatus.UNKNOWN_BALANCES, recommendation().status)
    }

    @Test fun familyIsolationPersistsAndPreventsCandyBorrowing() {
        store.save("other-line", UserResourceBalances(600000, 500, 500, 0, 0, 0, 0))
        assertNull(store.load(family).candy)
        assertNull(store.load(family).xlCandy)
        assertEquals(500, store.load("other-line").candy)
        assertEquals(AffordableLevelStatus.UNKNOWN_BALANCES, recommendation().status)
    }

    @Test fun zeroXlGivesBoundaryFortyAndNoFakeResourceMutation() = runBlocking {
        writeSavedPokemon()
        store.save(family, UserResourceBalances(600000, 500, 0, 0, 0, 0, 0))
        val result = recommendation()
        assertTrue(result.explanation, result.canApply)
        assertEquals(40.0, result.affordableLevel!!, 0.0)
        assertTrue(result.nextStepShortages.any { it.kind.name == "XL_CANDY" })
        assertEquals(0, store.load(family).xlCandy)
        assertEquals(1, db.collectionDao().collection().size)
    }

    @Test fun realStoredBalancesCannotBypassExplicitConsent() {
        store.save(family, UserResourceBalances(600000, 500, 500, 0, 0, 0, 0))
        val result = AffordableLevelPolicy.advise(
            fixture, species, League.MASTER, false,
            CommunityNormalPowerUpPack.pack, ledger()
        )
        assertEquals(AffordableLevelStatus.CONSENT_REQUIRED, result.status)
        assertFalse(result.canApply)
    }

    @Test fun specimenCpMismatchInMemoryNeverReturnsFinancialAdvice() = runBlocking {
        writeSavedPokemon()
        store.save(family, UserResourceBalances(600000, 500, 500, 0, 0, 0, 0))
        val old = db.collectionDao().collection().single()
        val altered = fixture.copy(cp = fixture.cp!! + 1)
        val result = recommendation(owned = altered)
        assertEquals(AffordableLevelStatus.NO_ELIGIBLE_TARGET, result.status)
        assertFalse(result.canApply)
        assertEquals(old, db.collectionDao().collection().single())
    }
}
