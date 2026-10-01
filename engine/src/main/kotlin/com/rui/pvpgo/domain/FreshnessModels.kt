package com.rui.pvpgo.domain

enum class FreshnessStatus { FRESH, AGING, STALE, UNKNOWN }

data class FreshnessPolicy(
    val freshForMs: Long,
    val staleAfterMs: Long
) {
    init {
        require(freshForMs >= 0)
        require(staleAfterMs >= freshForMs)
    }
}

data class SourceFreshness(
    val status: FreshnessStatus,
    val generatedAtEpochMs: Long?,
    val checkedAtEpochMs: Long?,
    val ageMs: Long?,
    val updateAvailable: Boolean,
    val activeRevision: String?,
    val latestRevision: String?,
    val message: String
)

data class IntelligenceFreshnessSnapshot(
    val data: SourceFreshness,
    val meta: SourceFreshness?
)

enum class CollectionVerificationSource { MANUAL, IMPORT, SCAN, BACKUP_RESTORE, EXTERNAL }

enum class OwnedPokemonField {
    SPECIES, IV, CP, LEVEL, FAST_MOVE, CHARGED_MOVES, SHADOW_STATE, PURIFIED_STATE, TAGS
}

enum class PokemonFreshnessStatus { VERIFIED, CHECK_SOON, STALE, UNKNOWN }

data class PokemonFreshness(
    val ownedPokemonId: String,
    val status: PokemonFreshnessStatus,
    val lastVerifiedAtEpochMs: Long?,
    val ageMs: Long?,
    val uncertainFields: Set<OwnedPokemonField>
)
