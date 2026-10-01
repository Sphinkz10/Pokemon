package com.rui.pvpgo.domain

enum class CollectionImportFormat { CSV, JSON, MANUAL }

enum class CollectionImportIssueCode {
    MISSING_REQUIRED_FIELD,
    INVALID_VALUE,
    INVALID_ROW,
    DUPLICATE_EXTERNAL_ID_IN_BATCH,
    UNSUPPORTED_FIELD
}

data class CollectionImportIssue(
    val rowNumber: Int,
    val code: CollectionImportIssueCode,
    val field: String? = null,
    val message: String
) {
    init {
        require(rowNumber >= 1)
        require(message.isNotBlank())
    }
}

data class CollectionImportCandidate(
    val rowNumber: Int,
    val candidate: OwnedPokemon,
    val sourceLabel: String,
    val sourceExternalId: String? = null,
    val rawFields: Map<String, String> = emptyMap()
) {
    init {
        require(rowNumber >= 1)
        require(sourceLabel.isNotBlank())
    }
}

data class CollectionImportBatch(
    val format: CollectionImportFormat,
    val sourceLabel: String,
    val importedAtEpochMs: Long,
    val candidates: List<CollectionImportCandidate>,
    val issues: List<CollectionImportIssue>,
    val inputRowCount: Int
) {
    init {
        require(sourceLabel.isNotBlank())
        require(importedAtEpochMs >= 0)
        require(inputRowCount >= 0)
    }

    val acceptedCount: Int get() = candidates.size
    val rejectedCount: Int get() = issues.map { it.rowNumber }.distinct().count { row -> candidates.none { it.rowNumber == row } }
}

data class ReconciledImportCandidate(
    val importCandidate: CollectionImportCandidate,
    val reconciliation: ReconciliationReport
) {
    init {
        require(reconciliation.candidateId == importCandidate.candidate.id)
        require(reconciliation.requiresHumanConfirmation)
    }
}

data class ReconciledImportBatch(
    val importBatch: CollectionImportBatch,
    val reconciled: List<ReconciledImportCandidate>
) {
    init {
        require(reconciled.size == importBatch.candidates.size)
    }
}
