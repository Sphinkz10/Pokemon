package com.rui.pvpgo

/**
 * Safe, explainable language for the candidate-to-collection handoff.
 *
 * A GO search never creates a local Inbox entry. A calculated IV rank
 * compares spreads for a *specific species and league*, not how strong the
 * species is in the meta, and never licenses transfer or automatic import.
 */
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

    const val EMPTY_GUIDANCE =
        "Uma pesquisa no Pokémon GO não envia capturas para a Companion. " +
            "Regista manualmente a espécie e os três IVs para os avaliar."

    fun saveAction(hasPossibleDuplicate: Boolean): String =
        if (hasPossibleDuplicate) "Guardar separado e analisar"
        else "Confirmar e analisar"

    /** Room must emit the newly saved record before opening its Detail route. */
    fun canOpenConfirmed(confirmedId: String?, ownedIds: Set<String>): Boolean =
        !confirmedId.isNullOrBlank() && confirmedId in ownedIds
}
