package com.rui.pvpgo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateEvolutionExplanationTest {
    private fun row(rank: Int?, count: Int = 1, best: Int? = 40) =
        CandidateEvolutionIvRow("evolved", "evolved-form", rank, count, best)

    @Test fun unknownRankCannotLookLikeAnOpportunity() {
        assertTrue(CandidateEvolutionAdvisor.explain(row(null)).contains("indisponível"))
    }

    @Test fun absentCollectionComparisonIsNeutral() {
        val message = CandidateEvolutionAdvisor.explain(row(2, 0, null))
        assertTrue(message.contains("Não tens um exemplar"))
        assertFalse(message.contains("melhor do que"))
    }

    @Test fun savedComparisonMissingRankDoesNotClaimImprovement() {
        val message = CandidateEvolutionAdvisor.explain(row(1, 2, null))
        assertTrue(message.contains("falta o rank"))
        assertFalse(message.contains("melhor do que"))
    }

    @Test fun candidateBetterUsesTargetRankOnly() {
        val message = CandidateEvolutionAdvisor.explain(row(3, 3, 18))
        assertTrue(message.contains("#3"))
        assertTrue(message.contains("#18"))
        assertTrue(message.contains("nesta evolução"))
    }

    @Test fun equalRanksAreNotDescribedAsSuperior() {
        assertTrue(CandidateEvolutionAdvisor.explain(row(18, 1, 18)).contains("igual"))
    }

    @Test fun worseCandidateNeverRecommendsTransfer() {
        val message = CandidateEvolutionAdvisor.explain(row(500, 1, 3))
        assertTrue(message.contains("não implica transferência"))
        assertFalse(message.contains("deves transferir"))
    }

    @Test fun evolutionPreviewLimitationsRemainVisible() {
        val limitation = CandidateEvolutionAdvisor.LIMITATIONS
        for (part in listOf("Candy", "itens", "CP", "elegibilidade", "custos", "meta", "transferências"))
            assertTrue(part, limitation.contains(part))
    }

    @Test fun maxTargetPreviewIsBounded() {
        assertTrue(CandidateEvolutionAdvisor.MAX_TARGETS_PER_PREVIEW in 1..12)
    }
}
