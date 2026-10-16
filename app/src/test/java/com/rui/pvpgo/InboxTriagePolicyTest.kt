package com.rui.pvpgo

import com.rui.pvpgo.domain.CandidateInboxItem
import com.rui.pvpgo.domain.CandidateInboxStatus
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Triage is not a verdict about PvP eligibility, catches, ownership or transfer. */
class InboxTriagePolicyTest {
    private fun owned(
        id: String, species: String = "pikachu",
        iv: IvSpread = IvSpread(0, 15, 15),
        cp: Int? = 500, level: Double? = 20.0
    ) = OwnedPokemon(
        id = id, speciesId = species, iv = iv, cp = cp, level = level,
        createdAtEpochMs = 100L
    )

    private fun candidate(
        id: String, specimen: OwnedPokemon, status: CandidateInboxStatus = CandidateInboxStatus.PENDING
    ) = CandidateInboxItem(
        id = id, candidate = specimen.copy(id = "$id-owned"),
        createdAtEpochMs = 100L, status = status
    )

    private val collection = listOf(owned("already-saved"))
    private val matching = candidate("match", owned("from-go"))
    private val unrelated = candidate("different", owned("from-go-2", species = "bulbasaur"))
    private val missing = candidate(
        "uncertain", owned("unknown-fields", species = "squirtle", cp = null, level = null)
    )
    private val names = mapOf("pikachu" to "Pikachu", "bulbasaur" to "Bulbasaur", "squirtle" to "Squirtle")

    @Test fun allPendingAreVisibleAndCountsAreIndependentOfSearch() {
        val snapshot = InboxTriagePolicy.evaluate(
            listOf(matching, unrelated, missing), collection, names
        )
        assertEquals(3, snapshot.totalPending)
        assertEquals(listOf("match", "different", "uncertain"), snapshot.visible.map { it.id })
        assertEquals(1, snapshot.possibleMatches)
        assertEquals(2, snapshot.noMatches)
        assertEquals(1, snapshot.missingData)
    }

    @Test fun possibleMatchFilterUsesActualSameIndividualReconciliationNotIvRank() {
        val result = InboxTriagePolicy.evaluate(
            listOf(matching, unrelated, missing), collection, names,
            filter = InboxTriageFilter.POSSIBLE_MATCHES
        )
        assertEquals(listOf("match"), result.visible.map { it.id })
        assertTrue(result.reconciliationById["match"]!!.requiresHumanConfirmation)
        assertTrue(result.reconciliationById["match"]!!.matches.isNotEmpty())
    }

    @Test fun noMatchesFilterDoesNotHideOriginalPendingCandidates() {
        val result = InboxTriagePolicy.evaluate(
            listOf(matching, unrelated, missing), collection, names,
            filter = InboxTriageFilter.NO_MATCHES
        )
        assertEquals(setOf("different", "uncertain"), result.visible.map { it.id }.toSet())
        assertEquals(3, result.totalPending)
    }

    @Test fun incompleteFieldsIncludeUnknownCpOrLevel() {
        val result = InboxTriagePolicy.evaluate(
            listOf(matching, unrelated, missing), collection, names,
            filter = InboxTriageFilter.MISSING_DATA
        )
        assertEquals(listOf("uncertain"), result.visible.map { it.id })
        assertTrue(InboxTriagePolicy.isIncomplete(missing.candidate))
        assertFalse(InboxTriagePolicy.isIncomplete(matching.candidate))
    }

    @Test fun queryFindsCaseInsensitiveSpeciesNameNicknameAndIvSpread() {
        val renamed = candidate("named", owned("named-go", species = "eevee")
            .copy(nickname = "Para Ultra", iv = IvSpread(1, 2, 3)))
        val rows = listOf(matching, unrelated, missing, renamed)
        assertEquals(listOf("match"), InboxTriagePolicy.evaluate(
            rows, collection, names, search = "PIKACHU"
        ).visible.map { it.id })
        assertEquals(listOf("named"), InboxTriagePolicy.evaluate(
            rows, collection, names, search = "ultra"
        ).visible.map { it.id })
        assertEquals(listOf("named"), InboxTriagePolicy.evaluate(
            rows, collection, names, search = "1/2/3"
        ).visible.map { it.id })
    }

    @Test fun searchAndMatchFilterIntersectWithoutAlteringCounts() {
        val result = InboxTriagePolicy.evaluate(
            listOf(matching, unrelated, missing), collection, names,
            search = "squirtle", filter = InboxTriageFilter.POSSIBLE_MATCHES
        )
        assertTrue(result.visible.isEmpty())
        assertEquals(3, result.totalPending)
        assertEquals(1, result.possibleMatches)
    }

    @Test fun alreadyReviewedCandidatesAreNeverShownOrCounted() {
        val discarded = candidate("discarded", owned("pvp-reviewed"), CandidateInboxStatus.DISCARDED)
        val confirmed = candidate("confirmed", owned("pvp-confirmed"), CandidateInboxStatus.CONFIRMED_NEW)
        val result = InboxTriagePolicy.evaluate(
            listOf(discarded, matching, confirmed), collection, names
        )
        assertEquals(listOf("match"), result.visible.map { it.id })
        assertEquals(1, result.totalPending)
        assertEquals(1, result.possibleMatches)
    }

    @Test fun emptyInboxNeverSuggestsAnyAutomaticAction() {
        val result = InboxTriagePolicy.evaluate(emptyList(), collection, names)
        assertEquals(0, result.totalPending)
        assertTrue(result.visible.isEmpty())
        assertTrue(result.reconciliationById.isEmpty())
        assertTrue(InboxTriageFilter.entries.none { it.label.contains("transfer", ignoreCase = true) })
    }
}
