package com.rui.pvpgo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rui.pvpgo.domain.CandidateInboxItem
import com.rui.pvpgo.domain.CandidateInboxStatus
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Transactional undo rules must never confirm automatically or modify a
 * Pokemon already in the user's Collection. Executed on real Android Room.
 */
@RunWith(AndroidJUnit4::class)
class InboxRestoreAndroidTest {
    private lateinit var database: PvpUserDatabase
    private lateinit var repository: CollectionRepository
    private val owned = OwnedPokemon(
        id = "inbox-restore-owned", speciesId = "pikachu", iv = IvSpread(0, 15, 15),
        cp = null, level = null, createdAtEpochMs = 100L
    )
    private val item = CandidateInboxItem(
        id = "inbox-restore-candidate", candidate = owned, createdAtEpochMs = 100L
    )

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PvpUserDatabase::class.java).build()
        repository = CollectionRepository.forTesting(database)
        runBlocking { repository.submitCandidates(listOf(item)) }
    }
    @After fun cleanup() { database.close() }

    @Test fun explicitUndoRestoresDiscardedIdAndUnknownFieldsWithoutSavingPokemon() = runBlocking {
        repository.discardCandidate(item.id)
        assertTrue(repository.pendingInbox.first().isEmpty())
        repository.restoreDiscarded(item.id)
        val pending = repository.pendingInbox.first()
        assertEquals(1, pending.size)
        assertEquals(item.id, pending.single().id)
        assertEquals(CandidateInboxStatus.PENDING, pending.single().status)
        assertEquals(item.candidate, pending.single().candidate)
        assertTrue(repository.currentCollection().isEmpty())
    }

    @Test fun undoBeforeDiscardMustRefuseAndNotModifyPendingCandidate() = runBlocking {
        assertTrue(runCatching { repository.restoreDiscarded(item.id) }.isFailure)
        assertEquals(listOf(item.id), repository.pendingInbox.first().map { it.id })
    }

    @Test fun undoCannotReverseConfirmationNorDuplicateAnOwnedPokemon() = runBlocking {
        repository.confirmNew(item.id)
        assertTrue(runCatching { repository.restoreDiscarded(item.id) }.isFailure)
        assertTrue(repository.pendingInbox.first().isEmpty())
        assertEquals(owned.id, repository.currentCollection().single().id)
        assertEquals(CandidateInboxStatus.CONFIRMED_NEW, repository.inbox.first().single().status)
    }

    @Test fun repeatedUndoIsRefusedAndNeverCreatesDuplicateRows() = runBlocking {
        repository.discardCandidate(item.id)
        repository.restoreDiscarded(item.id)
        assertTrue(runCatching { repository.restoreDiscarded(item.id) }.isFailure)
        assertEquals(1, repository.inbox.first().size)
        assertEquals(1, repository.pendingInbox.first().size)
        assertTrue(repository.currentCollection().isEmpty())
    }

    @Test fun undoWithExistingOwnedIdentityRefusesUnsafeRestoration() = runBlocking {
        repository.discardCandidate(item.id)
        repository.upsertOwned(owned)
        assertTrue(runCatching { repository.restoreDiscarded(item.id) }.isFailure)
        assertEquals(CandidateInboxStatus.DISCARDED, repository.inbox.first().single().status)
        assertTrue(repository.pendingInbox.first().isEmpty())
        assertEquals(owned.id, repository.currentCollection().single().id)
    }
}
