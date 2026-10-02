package com.rui.pvpgo

import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.domain.TeamMember
import com.rui.pvpgo.domain.TeamRole
import com.rui.pvpgo.engine.League
import org.junit.Assert.*
import org.junit.Test

class SavedTeamMutationPolicyTest {
    private fun original() = SavedTeam(
        id = "original", name = "Great Core", league = League.GREAT,
        members = listOf(
            TeamMember("one", TeamRole.LEAD),
            TeamMember("two", TeamRole.SAFE_SWITCH),
            TeamMember("three", TeamRole.CLOSER)
        ),
        isPrimary = true, notes = "meta notes",
        createdAtEpochMs = 100L, updatedAtEpochMs = 200L
    )

    @Test fun duplicateKeepsSpecimensRolesLeagueAndNotes() {
        val original = original()
        val copy = SavedTeamMutationPolicy.duplicate(original, "copy", 300L)
        assertEquals(original.members, copy.members)
        assertEquals(original.league, copy.league)
        assertEquals(original.notes, copy.notes)
        assertEquals("Great Core (cópia)", copy.name)
        assertEquals("copy", copy.id)
        assertFalse(copy.isPrimary)
        assertEquals(300L, copy.createdAtEpochMs)
        assertEquals(300L, copy.updatedAtEpochMs)
        assertTrue(original.isPrimary)
        assertEquals("original", original.id)
    }

    @Test fun duplicateLongNameRespectsEditorLimit() {
        val original = original().copy(name = "A".repeat(60))
        val duplicate = SavedTeamMutationPolicy.duplicate(original, "copy", 300L)
        assertEquals(60, duplicate.name.length)
        assertTrue(duplicate.name.endsWith(" (cópia)"))
        assertEquals(60, original.name.length)
    }

    @Test fun swapLeadAndCloserKeepsOwnedIdsAndOtherFields() {
        val source = original()
        val changed = SavedTeamMutationPolicy.swapRoles(source, TeamRole.LEAD, TeamRole.CLOSER, 300L)
        assertEquals("three", changed.members.single { it.role == TeamRole.LEAD }.ownedPokemonId)
        assertEquals("one", changed.members.single { it.role == TeamRole.CLOSER }.ownedPokemonId)
        assertEquals("two", changed.members.single { it.role == TeamRole.SAFE_SWITCH }.ownedPokemonId)
        assertEquals(source.members.map { it.ownedPokemonId }.toSet(), changed.members.map { it.ownedPokemonId }.toSet())
        assertEquals(source.id, changed.id)
        assertEquals(source.name, changed.name)
        assertEquals(source.league, changed.league)
        assertEquals(source.createdAtEpochMs, changed.createdAtEpochMs)
        assertEquals(300L, changed.updatedAtEpochMs)
        assertEquals("one", source.members.single { it.role == TeamRole.LEAD }.ownedPokemonId)
    }

    @Test fun swapSameRoleIsNoOp() {
        val source = original()
        assertSame(source, SavedTeamMutationPolicy.swapRoles(source, TeamRole.LEAD, TeamRole.LEAD, 300L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun swapRejectsBackwardTimestamp() {
        SavedTeamMutationPolicy.swapRoles(original(), TeamRole.LEAD, TeamRole.CLOSER, 199L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOriginalIdentity() {
        SavedTeamMutationPolicy.duplicate(original(), "original", 300L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankIdentity() {
        SavedTeamMutationPolicy.duplicate(original(), " ", 300L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeTimestamp() {
        SavedTeamMutationPolicy.duplicate(original(), "new", -1L)
    }
}
