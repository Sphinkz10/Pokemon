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
