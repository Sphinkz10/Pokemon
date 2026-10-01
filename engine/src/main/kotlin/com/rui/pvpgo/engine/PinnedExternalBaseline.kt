package com.rui.pvpgo.engine

/**
 * Same-revision PvPoke outputs imported only after the upstream checkout has been verified against
 * [PvPokeReferencePin]. Empty by default: absence of evidence must remain BLOCKED, never guessed.
 *
 * `tools/import_pinned_pvpoke_results.py` is the only supported generator for captured entries.
 */
data class PinnedExternalBattleResult(
    val fixtureId: String,
    val battleRatingA: Int,
    val battleRatingB: Int,
    val winner: Int?,
    val turns: Int,
    val sourceCommitSha: String,
    val sourceSiteVersion: String,
    val traceSha256: String
)

object PinnedExternalBaseline {
    const val SCHEMA_VERSION: Int = 2

    /** Generated entries are inserted between the markers below. */
    val results: Map<String, PinnedExternalBattleResult> = listOf<PinnedExternalBattleResult>(
        // GENERATED_PINNED_RESULTS_START
        // GENERATED_PINNED_RESULTS_END
    ).associateBy { it.fixtureId }

    val isCaptured: Boolean get() = results.isNotEmpty()

    fun get(fixtureId: String): PinnedExternalBattleResult? = results[fixtureId]

    fun provenanceIsCurrent(): Boolean = results.values.all {
        it.sourceCommitSha == PvPokeReferencePin.COMMIT_SHA &&
            it.sourceSiteVersion == PvPokeReferencePin.SITE_VERSION
    }
}
