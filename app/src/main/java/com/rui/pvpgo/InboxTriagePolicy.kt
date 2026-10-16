package com.rui.pvpgo

import com.rui.pvpgo.domain.CandidateInboxItem
import com.rui.pvpgo.domain.CandidateInboxStatus
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.ReconciliationReport
import com.rui.pvpgo.engine.CollectionReconciler
import java.util.Locale

/** Triage changes only visibility; confirming/discarding always requires the user's own tap. */
internal enum class InboxTriageFilter(val label: String) {
    ALL("Todos"),
    POSSIBLE_MATCHES("Possíveis correspondências"),
    NO_MATCHES("Sem correspondências"),
    MISSING_DATA("Dados por completar")
}

internal data class InboxTriageSnapshot(
    val visible: List<CandidateInboxItem>,
    val totalPending: Int,
    val possibleMatches: Int,
    val noMatches: Int,
    val missingData: Int,
    val reconciliationById: Map<String, ReconciliationReport>
)

internal object InboxTriagePolicy {
    fun evaluate(
        pending: List<CandidateInboxItem>,
        collection: List<OwnedPokemon>,
        speciesNamesById: Map<String, String>,
        search: String = "",
        filter: InboxTriageFilter = InboxTriageFilter.ALL
    ): InboxTriageSnapshot {
        // Never show reviewed candidates as pending, even when supplied by an
        // out-of-date or unexpectedly broad repository query.
        val candidates = pending.filter { it.status == CandidateInboxStatus.PENDING }
        val reports = candidates.associate { item ->
            item.id to CollectionReconciler.reconcile(item.candidate, collection)
        }
        val possibleMatches = candidates.count { reports[it.id]?.matches?.isNotEmpty() == true }
        val incomplete = candidates.count { isIncomplete(it.candidate) }
        val normalizedSearch = search.trim().lowercase(Locale.ROOT)
        val visible = candidates.filter { item ->
            val c = item.candidate
            val name = speciesNamesById[c.speciesId].orEmpty()
            val matchesText = normalizedSearch.isBlank() ||
                name.lowercase(Locale.ROOT).contains(normalizedSearch) ||
                c.speciesId.lowercase(Locale.ROOT).contains(normalizedSearch) ||
                c.nickname.orEmpty().lowercase(Locale.ROOT).contains(normalizedSearch) ||
                "${c.iv.attack}/${c.iv.defense}/${c.iv.stamina}".contains(normalizedSearch)
            val matchesCategory = when (filter) {
                InboxTriageFilter.ALL -> true
                InboxTriageFilter.POSSIBLE_MATCHES -> reports[item.id]?.matches?.isNotEmpty() == true
                InboxTriageFilter.NO_MATCHES -> reports[item.id]?.matches?.isEmpty() == true
                InboxTriageFilter.MISSING_DATA -> isIncomplete(c)
            }
            matchesText && matchesCategory
        }
        return InboxTriageSnapshot(
            visible = visible,
            totalPending = candidates.size,
            possibleMatches = possibleMatches,
            noMatches = candidates.size - possibleMatches,
            missingData = incomplete,
            reconciliationById = reports
        )
    }

    fun isIncomplete(owned: OwnedPokemon): Boolean =
        owned.cp == null || owned.level == null || owned.uncertainFields.isNotEmpty()
}
