package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.League

enum class ReconciliationMatchKind { EXACT_EXTERNAL_ID, STRONG_SAME_POKEMON, POSSIBLE_SAME_POKEMON }
enum class ReconciliationAction { CREATE_NEW, REVIEW_EXISTING, MERGE_RECOMMENDED }

data class ReconciliationMatch(
    val existingOwnedPokemonId: String,
    val kind: ReconciliationMatchKind,
    val confidence: Double,
    val reasons: List<String>
) {
    init { require(confidence in 0.0..1.0) }
}

data class ReconciliationReport(
    val candidateId: String,
    val action: ReconciliationAction,
    val matches: List<ReconciliationMatch>,
    val requiresHumanConfirmation: Boolean = true
) {
    init {
        require(candidateId.isNotBlank())
        require(requiresHumanConfirmation) { "Collection reconciliation must never silently mutate the collection" }
    }
}

enum class PvpBoxCategory { READY, CANDIDATE, IMPORTANT, NEEDS_CONFIRMATION }

data class PvpBoxEntry(
    val ownedPokemonId: String,
    val categories: Set<PvpBoxCategory>,
    val reasons: List<String>
)

enum class CandidateInboxStatus { PENDING, CONFIRMED_NEW, MERGED, DISCARDED }

data class CandidateInboxItem(
    val id: String,
    val candidate: OwnedPokemon,
    val createdAtEpochMs: Long,
    val status: CandidateInboxStatus = CandidateInboxStatus.PENDING,
    val reviewedAtEpochMs: Long? = null,
    val linkedOwnedPokemonId: String? = null
) {
    init {
        require(id.isNotBlank())
        require(createdAtEpochMs >= 0)
        require(reviewedAtEpochMs == null || reviewedAtEpochMs >= createdAtEpochMs)
        if (status == CandidateInboxStatus.MERGED) require(!linkedOwnedPokemonId.isNullOrBlank())
    }
}

data class DuplicateUseSuggestion(
    val ownedPokemonId: String,
    val targetSpeciesId: String,
    val league: League,
    val rank: Int,
    val cp: Int,
    val level: Double
)

data class DuplicateFamilyReport(
    val familyKey: String,
    val ownedPokemonIds: List<String>,
    val bestUses: List<DuplicateUseSuggestion>,
    val notes: List<String>
)

enum class TransferPriority { PROTECTED, REVIEW, LOW_PRIORITY }
enum class TransferSafetyReason {
    FAVORITE, SHINY, LUCKY, BEST_BUDDY, UNIQUE_SPECIES, UNIQUE_SHADOW_STATE,
    LEGACY_OR_ELITE_MOVE, PVP_TAG, ACTIVE_BUILD_PLAN, HIGH_PVP_RANK, UNCERTAIN_DATA
}

data class TransferSafetyAssessment(
    val ownedPokemonId: String,
    val priority: TransferPriority,
    val reasons: Set<TransferSafetyReason>,
    val message: String
)

enum class SearchStringPrecision { INDIVIDUAL_HINT, SPECIES_LEVEL }

data class PokemonGoSearchString(
    val query: String,
    val precision: SearchStringPrecision,
    val coveredOwnedPokemonIds: List<String>,
    val note: String
)

data class CollectionBackupSnapshot(
    val collection: List<OwnedPokemon>,
    val buildPlans: List<PvpBuildPlan>,
    val savedTeams: List<SavedTeam>,
    val battles: List<BattleRecord>,
    val exportedAtEpochMs: Long,
    val sourceAppVersion: String,
    val schemaVersion: Int = 1
) {
    init {
        require(exportedAtEpochMs >= 0)
        require(sourceAppVersion.isNotBlank())
        require(schemaVersion > 0)
    }
}

data class CollectionBackupEnvelope(
    val schemaVersion: Int,
    val exportedAtEpochMs: Long,
    val sourceAppVersion: String,
    val payloadSha256: String,
    val payload: String
) {
    init {
        require(schemaVersion > 0)
        require(exportedAtEpochMs >= 0)
        require(sourceAppVersion.isNotBlank())
        require(payloadSha256.matches(Regex("[0-9a-f]{64}")))
    }
}
