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

    @Test fun deletingUnknownTeamDoesNotAffectOtherRows() = runBlocking {
        dao.upsertSavedTeam(team("kept"))
        dao.deleteSavedTeam("unknown")
        assertEquals(listOf("kept"), dao.observeSavedTeams().first().map { it.id })
    }
}
