package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No candidate is automatically approved merely because its IV rank is computed. */
class CandidateReviewPolicyTest {
    @Test fun missingSpeciesNeverDisplaysAnInventedRank() {
        assertEquals(
            "Espécie ainda indisponível no catálogo.",
            CandidateReviewPolicy.rankStatus(speciesAvailable = false, computed = false, rank = 1)
        )
    }

    @Test fun loadingNeverPretendsToHaveAResult() {
        assertTrue(
            CandidateReviewPolicy.rankStatus(speciesAvailable = true, computed = false, rank = null)
                .contains("A calcular")
        )
    }

    @Test fun unrankableSpeciesExplainsUnavailableResult() {
        assertTrue(
            CandidateReviewPolicy.rankStatus(speciesAvailable = true, computed = true, rank = null)
                .contains("indisponível")
        )
    }

    @Test fun availableRankIsExplicitlyRestrictedToSpeciesAndLeague() {
        assertEquals(
            "Rank IV #12 nesta espécie e liga",
            CandidateReviewPolicy.rankStatus(speciesAvailable = true, computed = true, rank = 12)
        )
        assertTrue(CandidateReviewPolicy.RANK_LIMITATION.contains("não mede a força no meta"))
        assertTrue(CandidateReviewPolicy.RANK_LIMITATION.contains("não confirma a elegibilidade"))
        assertTrue(CandidateReviewPolicy.RANK_LIMITATION.contains("transferência"))
    }

    @Test fun zeroLocalInboxDoesNotMeanZeroPokemonInGo() {
        assertTrue(CandidateReviewPolicy.EMPTY_GUIDANCE.contains("não envia capturas"))
        assertTrue(CandidateReviewPolicy.EMPTY_GUIDANCE.contains("três IVs"))
    }

    @Test fun possibleDuplicateRequiresDistinctSaveAction() {
        assertEquals("Guardar separado e analisar", CandidateReviewPolicy.saveAction(true))
        assertEquals("Confirmar e analisar", CandidateReviewPolicy.saveAction(false))
    }

    @Test fun confirmedCandidateWaitsForRoomToEmitExactOwnedId() {
        val ids = setOf("owned-1", "owned-3")
        assertFalse(CandidateReviewPolicy.canOpenConfirmed(null, ids))
        assertFalse(CandidateReviewPolicy.canOpenConfirmed("", ids))
        assertFalse(CandidateReviewPolicy.canOpenConfirmed("owned-2", ids))
        assertTrue(CandidateReviewPolicy.canOpenConfirmed("owned-3", ids))
    }

    @Test fun emptyCollectionCanNeverTriggerDetailNavigation() {
        assertFalse(CandidateReviewPolicy.canOpenConfirmed("owned-1", emptySet()))
    }
}
