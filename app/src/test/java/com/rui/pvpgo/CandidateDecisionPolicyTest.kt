package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** IV comparison is not a meta-strength, investment, transfer, or eligibility recommendation. */
class CandidateDecisionPolicyTest {
    private fun explain(
        rank: Int? = 12,
        count: Int = 1,
        best: Int? = 35,
        loaded: Boolean = true,
        computed: Boolean = true,
        species: Boolean = true
    ) = CandidateReviewPolicy.explainIvComparison(
        speciesAvailable = species,
        collectionLoaded = loaded,
        computed = computed,
        evidence = CandidateIvEvidence(rank, count, best)
    )

    @Test fun missingCatalogMustNotMakeRankClaims() {
        val view = explain(species = false, rank = 1)
        assertEquals("Análise indisponível", view.title)
        assertTrue(view.reason.contains("espécie"))
    }

    @Test fun roomCollectionMustLoadBeforeSuggestingNoComparables() {
        val view = explain(loaded = false, count = 0)
        assertEquals("Comparação em preparação", view.title)
        assertFalse(view.title.contains("Sem comparação"))
    }

    @Test fun pendingCalculationDoesNotUsePreviousRank() {
        assertEquals("Comparação em preparação", explain(computed = false).title)
    }

    @Test fun nullEvidenceMustStayUnavailable() {
        val result = CandidateReviewPolicy.explainIvComparison(true, true, true, null)
        assertEquals("Ranking de IVs indisponível", result.title)
    }

    @Test fun unavailableRankNeverInventsVerdict() {
        val result = explain(rank = null, best = 1)
        assertTrue(result.title.contains("indisponível"))
        assertTrue(result.nextStep.contains("não o descartes"))
    }

    @Test fun noOwnedOfSameSpeciesIsNotProofOfMetaUtility() {
        val result = explain(rank = 1, count = 0, best = null)
        assertEquals("Sem comparação na tua coleção", result.title)
        assertTrue(result.nextStep.contains("não confirma utilidade"))
    }

    @Test fun existingPokemonWithoutComparableRankDoesNotMeanNewCandidateIsBetter() {
        val result = explain(count = 3, best = null)
        assertEquals("Comparação incompleta", result.title)
    }

    @Test fun betterIvRankIsExpressedAsSameSpeciesComparisonOnly() {
        val result = explain(rank = 3, count = 2, best = 42)
        assertTrue(result.title.contains("Melhor rank IV"))
        assertTrue(result.reason.contains("#3"))
        assertTrue(result.reason.contains("#42"))
        assertTrue(result.nextStep.contains("investimento"))
    }

    @Test fun equalRanksRequireOtherEvidence() {
        val result = explain(rank = 8, best = 8)
        assertTrue(result.title.contains("igual"))
        assertTrue(result.nextStep.contains("movimentos"))
    }

    @Test fun worseIvRankDoesNotSuggestAutomaticDiscard() {
        val result = explain(rank = 105, best = 12)
        assertTrue(result.title.contains("Já tens um rank IV melhor"))
        assertTrue(result.nextStep.contains("não transfiras"))
        assertFalse(result.nextStep.contains("deves transferir"))
    }

    @Test fun limitationsAlwaysKeepUnknownMetaCostsAndEvolutionVisible() {
        val guard = CandidateReviewPolicy.DECISION_LIMITATIONS
        assertTrue(guard.contains("mesma espécie e forma"))
        assertTrue(guard.contains("Força no meta"))
        assertTrue(guard.contains("custos"))
        assertTrue(guard.contains("evoluções"))
        assertTrue(guard.contains("transferências"))
    }
}
