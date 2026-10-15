package com.rui.pvpgo

/**
 * Safe, explainable language for the candidate-to-collection handoff.
 *
 * A GO search never creates a local Inbox entry. A calculated IV rank
 * compares spreads for a *specific species and league*, not how strong the
 * species is in the meta, and never licenses transfer or automatic import.
 */
/**
 * Evidence about IV rank only: no current meta ranking, resource cost or transfer advice.
 * Comparables must have the exact same speciesId/form, not inferred evolutions.
 */
internal data class CandidateIvEvidence(
    val candidateRank: Int?,
    val sameSpeciesOwnedCount: Int,
    val bestOwnedRank: Int?
)

internal data class CandidateDecisionText(
    val title: String,
    val reason: String,
    val nextStep: String
)

internal object CandidateReviewPolicy {
    fun rankStatus(speciesAvailable: Boolean, computed: Boolean, rank: Int?): String =
        when {
            !speciesAvailable -> "Espécie ainda indisponível no catálogo."
            !computed -> "A calcular o ranking de IVs…"
            rank == null -> "Ranking indisponível para esta espécie e liga."
            else -> "Rank IV #$rank nesta espécie e liga"
        }

    const val RANK_LIMITATION =
        "O rank compara IVs da mesma espécie e liga. Não mede a força no meta, " +
            "não confirma a elegibilidade e não é uma decisão de transferência."


    /**
     * Explain candidate rank relative to locally saved Pokémon of the exact same
     * species/form. This NEVER recommends transferring, investing or battling.
     */
    fun explainIvComparison(
        speciesAvailable: Boolean,
        collectionLoaded: Boolean,
        computed: Boolean,
        evidence: CandidateIvEvidence?
    ): CandidateDecisionText {
        if (!speciesAvailable) return CandidateDecisionText(
            "Análise indisponível",
            "A espécie ou forma ainda não está disponível no catálogo.",
            "Confirma a espécie e os IVs antes de decidir."
        )
        if (!collectionLoaded || !computed) return CandidateDecisionText(
            "Comparação em preparação",
            "Estamos a aguardar os dados locais e o cálculo dos IVs.",
            "Não tires conclusões enquanto a análise não terminar."
        )
        if (evidence?.candidateRank == null) return CandidateDecisionText(
            "Ranking de IVs indisponível",
            "Não foi possível calcular o rank nesta espécie e liga.",
            "Mantém o candidato para análise manual; não o descartes com base nesta ausência."
        )
        if (evidence.sameSpeciesOwnedCount == 0) return CandidateDecisionText(
            "Sem comparação na tua coleção",
            "Ainda não tens um exemplar registado desta espécie e forma para esta comparação.",
            "O rank IV #${evidence.candidateRank} pode ser revisto, mas não confirma utilidade competitiva."
        )
        val best = evidence.bestOwnedRank ?: return CandidateDecisionText(
            "Comparação incompleta",
            "Existem outros exemplares registados, mas os respetivos ranks não estão disponíveis.",
            "Confirma os dados e analisa o exemplar individualmente."
        )
        return when {
            evidence.candidateRank < best -> CandidateDecisionText(
                "Melhor rank IV do que os teus exemplares",
                "Rank IV #${evidence.candidateRank} face ao melhor rank IV #$best registado para esta espécie, forma e liga.",
                "Considera guardar para estudar o build, a elegibilidade e o investimento."
            )
            evidence.candidateRank == best -> CandidateDecisionText(
                "Rank IV igual ao melhor que tens",
                "Já tens um exemplar desta espécie e forma com rank IV #$best nesta liga.",
                "Compara CP, nível, movimentos e características antes de decidir."
            )
            else -> CandidateDecisionText(
                "Já tens um rank IV melhor",
                "O melhor registado é rank IV #$best; este candidato é rank IV #${evidence.candidateRank}.",
                "Pode continuar útil noutra liga ou com outro build; não transfiras com base só neste rank."
            )
        }
    }

    const val DECISION_LIMITATIONS =
        "Comparação apenas com a mesma espécie e forma nesta liga. " +
            "Força no meta, custos, evoluções, disponibilidade e elegibilidade " +
            "ainda não foram avaliados. Não usar para decidir transferências."

    const val EMPTY_GUIDANCE =
        "Uma pesquisa no Pokémon GO não envia capturas para a Companion. " +
            "Regista manualmente a espécie e os três IVs para os avaliar."

    fun saveAction(hasPossibleDuplicate: Boolean): String =
        if (hasPossibleDuplicate) "Guardar separado e analisar"
        else "Confirmar e analisar"

    /** A blank IV is unknown, not an implicit 0/15/15 preset. */
    fun updateIvInput(previous: Int?, raw: String): Int? {
        if (raw.isBlank()) return null
        if (raw.length > 2 || !raw.all(Char::isDigit)) return previous
        val parsed = raw.toIntOrNull()
        return if (parsed != null && parsed in 0..15) parsed else previous
    }

    fun hasExplicitIvs(attack: Int?, defense: Int?, stamina: Int?): Boolean =
        attack != null && attack in 0..15 &&
            defense != null && defense in 0..15 &&
            stamina != null && stamina in 0..15

    /** Room must emit the newly saved record before opening its Detail route. */
    fun canOpenConfirmed(confirmedId: String?, ownedIds: Set<String>): Boolean =
        !confirmedId.isNullOrBlank() && confirmedId in ownedIds
}
