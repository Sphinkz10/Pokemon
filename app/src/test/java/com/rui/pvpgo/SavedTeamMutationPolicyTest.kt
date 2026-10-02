package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
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

    private fun specimen(id: String, cp: Int = 1200, ready: Boolean = true) = OwnedPokemon(
        id = id, speciesId = "pikachu", iv = IvSpread(10, 11, 12),
        cp = cp, level = 20.0, fastMoveId = if (ready) "quick-attack" else null,
        chargedMoveIds = if (ready) listOf("thunderbolt") else emptyList(),
        createdAtEpochMs = 100L
    )

    @Test fun replaceMemberKeepsOtherRolesAndIdentity() {
        val original = original()
        val newPokemon = specimen("four")
        val changed = SavedTeamMutationPolicy.replaceMember(
            original, TeamRole.SAFE_SWITCH, newPokemon, listOf(newPokemon), 300L
        )
        assertEquals("four", changed.members.single { it.role == TeamRole.SAFE_SWITCH }.ownedPokemonId)
        assertEquals("one", changed.members.single { it.role == TeamRole.LEAD }.ownedPokemonId)
        assertEquals("three", changed.members.single { it.role == TeamRole.CLOSER }.ownedPokemonId)
        assertEquals(original.id, changed.id)
        assertEquals(original.notes, changed.notes)
        assertEquals(300L, changed.updatedAtEpochMs)
        assertEquals("two", original.members.single { it.role == TeamRole.SAFE_SWITCH }.ownedPokemonId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun replaceRejectsDuplicateSpecimen() {
        val duplicate = specimen("one")
        SavedTeamMutationPolicy.replaceMember(original(), TeamRole.CLOSER, duplicate, listOf(duplicate), 300L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun replaceRejectsAboveLeagueCap() {
        val aboveCap = specimen("four", cp = 1501)
        SavedTeamMutationPolicy.replaceMember(original(), TeamRole.LEAD, aboveCap, listOf(aboveCap), 300L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun replaceRejectsUnknownCpInCappedLeague() {
        val unknown = specimen("four").copy(cp = null)
        SavedTeamMutationPolicy.replaceMember(original(), TeamRole.LEAD, unknown, listOf(unknown), 300L)
    }

    @Test fun replaceAllowsEligibleSpecimenAtExactCap() {
        val atCap = specimen("four", cp = 1500)
        val updated = SavedTeamMutationPolicy.replaceMember(original(), TeamRole.LEAD, atCap, listOf(atCap), 300L)
        assertEquals("four", updated.members.single { it.role == TeamRole.LEAD }.ownedPokemonId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun littleLeagueRejects501Cp() {
        val candidate = specimen("four", cp = 501)
        SavedTeamMutationPolicy.replaceMember(
            original().copy(league = League.LITTLE), TeamRole.LEAD, candidate, listOf(candidate), 300L
        )
    }

    @Test fun littleLeagueAccepts500Cp() {
        val candidate = specimen("four", cp = 500)
        val result = SavedTeamMutationPolicy.replaceMember(
            original().copy(league = League.LITTLE), TeamRole.LEAD, candidate, listOf(candidate), 300L
        )
        assertEquals("four", result.members.single { it.role == TeamRole.LEAD }.ownedPokemonId)
    }

    @Test fun masterLeagueDoesNotRejectHighCp() {
        val candidate = specimen("four", cp = 4000)
        val result = SavedTeamMutationPolicy.replaceMember(
            original().copy(league = League.MASTER), TeamRole.LEAD, candidate, listOf(candidate), 300L
        )
        assertEquals("four", result.members.single { it.role == TeamRole.LEAD }.ownedPokemonId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun replaceRejectsIncompleteMoves() {
        val incomplete = specimen("four", ready = false)
        SavedTeamMutationPolicy.replaceMember(original(), TeamRole.LEAD, incomplete, listOf(incomplete), 300L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun replaceRejectsPokemonOutsideCollection() {
        val missing = specimen("four")
        SavedTeamMutationPolicy.replaceMember(original(), TeamRole.LEAD, missing, emptyList(), 300L)
    }

    @Test fun pickerAndMutationAgreeOnEligibleSpecimens() {
        val original = original()
        val collection = listOf(
            specimen("one"), specimen("four", cp = 1500),
            specimen("five", cp = 1501), specimen("six", ready = false)
        )
        val candidates = TeamsGoldenPolicy.replacementCandidates(
            original, TeamRole.SAFE_SWITCH, collection
        )
        assertEquals(listOf("four"), candidates.map { it.id })
        val result = SavedTeamMutationPolicy.replaceMember(
            original, TeamRole.SAFE_SWITCH, candidates.single(), collection, 300L
        )
        assertEquals("four", result.members.single { it.role == TeamRole.SAFE_SWITCH }.ownedPokemonId)
    }

    @Test fun replacementSearchSupportsNicknameAndSpecimenId() {
        val source = original()
        val specimen = specimen("rare-owned-id").copy(nickname = "Raio Azul")
        assertEquals(1, TeamsGoldenPolicy.replacementCandidates(
            source, TeamRole.LEAD, listOf(specimen), "raio"
        ).size)
        assertEquals(1, TeamsGoldenPolicy.replacementCandidates(
            source, TeamRole.LEAD, listOf(specimen), "OWNED-ID"
        ).size)
        assertTrue(TeamsGoldenPolicy.replacementCandidates(
            source, TeamRole.LEAD, listOf(specimen), "não existe"
        ).isEmpty())
    }

    @Test fun replacementSearchExcludesAlreadyAssignedRoleEvenIfNameMatches() {
        val source = original()
        val assigned = specimen("three")
        assertTrue(TeamsGoldenPolicy.replacementCandidates(
            source, TeamRole.LEAD, listOf(assigned), "pikachu"
        ).isEmpty())
    }

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
