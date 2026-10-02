package com.rui.pvpgo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Executes against a real SQLite-backed in-memory Room database on Android. */
@RunWith(AndroidJUnit4::class)
class SavedTeamRoomIntegrationTest {
    private lateinit var database: PvpUserDatabase
    private lateinit var dao: CollectionDao

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PvpUserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.collectionDao()
    }

    @After fun tearDown() {
        database.close()
    }

    private fun team(id: String, name: String = "Great Core") = SavedTeamEntity(
        id = id,
        name = name,
        league = "GREAT",
        members = "lead-id",
        isPrimary = false,
        notes = "Original notes",
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L
    )

    @Test fun duplicatePersistsAsSeparateRecordAndDeleteLeavesOriginal() = runBlocking {
        dao.upsertSavedTeam(team("original"))
        dao.upsertSavedTeam(team("copy", "Great Core (cópia)"))
        val both = dao.observeSavedTeams().first()
        assertEquals(2, both.size)
        assertEquals(setOf("original", "copy"), both.map { it.id }.toSet())

        dao.deleteSavedTeam("copy")
        val remaining = dao.observeSavedTeams().first()
        assertEquals(1, remaining.size)
        assertEquals("original", remaining.single().id)
        assertEquals("Original notes", remaining.single().notes)
    }

    @Test fun renamingKeepsTeamIdentityAndMembers() = runBlocking {
        val original = team("original")
        dao.upsertSavedTeam(original)
        dao.upsertSavedTeam(original.copy(name = "Novo nome", updatedAtEpochMs = 200L))
        val saved = dao.observeSavedTeams().first()
        assertEquals(1, saved.size)
        assertEquals("original", saved.single().id)
        assertEquals("Novo nome", saved.single().name)
        assertEquals(original.members, saved.single().members)
    }

    @Test fun deletingTeamPreservesOwnedPokemonCollection() = runBlocking {
        val pokemon = OwnedPokemonEntity(
            id = "pokemon-one",
            speciesId = "pikachu",
            ivAttack = 10,
            ivDefense = 11,
            ivStamina = 12,
            cp = 500,
            level = 20.0,
            nickname = null,
            isShadow = false,
            isPurified = false,
            isShiny = false,
            isLucky = false,
            isBestBuddy = false,
            isFavorite = true,
            fastMoveId = null,
            chargedMoveIds = "",
            secondChargedMoveUnlocked = null,
            tags = "",
            acquisitionMethod = "UNKNOWN",
            caughtAtEpochMs = null,
            createdAtEpochMs = 100L,
            updatedAtEpochMs = 100L,
            externalSource = null,
            externalId = null,
            lastVerifiedAtEpochMs = null,
            verificationSource = "UNVERIFIED",
            uncertainFields = ""
        )
        dao.upsertOwned(pokemon)
        dao.upsertSavedTeam(team("deleted"))
        dao.deleteSavedTeam("deleted")
        assertTrue(dao.observeSavedTeams().first().isEmpty())
        val preserved = dao.collection()
        assertEquals(1, preserved.size)
        assertEquals(pokemon.id, preserved.single().id)
        assertEquals(pokemon.speciesId, preserved.single().speciesId)
        assertTrue(preserved.single().isFavorite)
    }

    @Test fun replacingTeamMemberPersistsThreeUniqueRoles() = runBlocking {
        val separator = "\u001F"
        val original = team("editable").copy(
            members = listOf("first:LEAD", "second:SAFE_SWITCH", "third:CLOSER").joinToString(separator)
        )
        dao.upsertSavedTeam(original)
        val replacement = original.copy(
            members = listOf("first:LEAD", "fourth:SAFE_SWITCH", "third:CLOSER").joinToString(separator),
            updatedAtEpochMs = 200L
        )
        dao.upsertSavedTeam(replacement)
        val stored = dao.observeSavedTeams().first().single()
        assertEquals("editable", stored.id)
        assertEquals(200L, stored.updatedAtEpochMs)
        val roles = stored.members.split(separator).associate { encoded ->
            val (id, role) = encoded.split(':', limit = 2)
            role to id
        }
        assertEquals(3, roles.size)
        assertEquals("first", roles["LEAD"])
        assertEquals("fourth", roles["SAFE_SWITCH"])
        assertEquals("third", roles["CLOSER"])
        assertEquals(3, roles.values.toSet().size)
        assertEquals("Original notes", stored.notes)
    }

    @Test fun teamsSurviveDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "team-reopen-test.db"
        context.deleteDatabase(filename)
        try {
            val first = Room.databaseBuilder(context, PvpUserDatabase::class.java, filename).build()
            try {
                first.collectionDao().upsertSavedTeam(team("persisted", "Equipa persistente"))
            } finally {
                first.close()
            }
            val second = Room.databaseBuilder(context, PvpUserDatabase::class.java, filename).build()
            try {
                val saved = second.collectionDao().observeSavedTeams().first()
                assertEquals(1, saved.size)
                assertEquals("persisted", saved.single().id)
                assertEquals("Equipa persistente", saved.single().name)
                assertEquals("Original notes", saved.single().notes)
            } finally {
                second.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
    }

    @Test fun deletingUnknownTeamDoesNotAffectOtherRows() = runBlocking {
        dao.upsertSavedTeam(team("kept"))
        dao.deleteSavedTeam("unknown")
        assertEquals(listOf("kept"), dao.observeSavedTeams().first().map { it.id })
    }
}
