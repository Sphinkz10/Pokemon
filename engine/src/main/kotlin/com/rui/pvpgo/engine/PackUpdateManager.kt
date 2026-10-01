package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.IntelligencePackBundle
import com.rui.pvpgo.domain.PackActivationState
import com.rui.pvpgo.domain.PackStageResult
import com.rui.pvpgo.domain.PackStageStatus

/**
 * F68 pure-domain foundation: validate -> stage -> smoke-test -> activate, retaining one known-good
 * bundle for rollback. Android persistence/download orchestration will wrap this later.
 */
class PackUpdateManager(initial: IntelligencePackBundle? = null) {
    private var active: IntelligencePackBundle? = initial
    private var previous: IntelligencePackBundle? = null
    private var staged: IntelligencePackBundle? = null

    fun stage(candidate: IntelligencePackBundle): PackStageResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val dataValidation = GameDataPackValidator.validate(candidate.dataPack)
        errors += dataValidation.errors.map { "data: $it" }
        warnings += dataValidation.warnings.map { "data: $it" }

        candidate.metaPack?.let { meta ->
            val metaValidation = MetaPackValidator.validate(meta, candidate.dataPack)
            errors += metaValidation.errors.map { "meta: $it" }
            warnings += metaValidation.warnings.map { "meta: $it" }
        }

        return if (errors.isEmpty()) {
            staged = candidate
            PackStageResult(PackStageStatus.READY, emptyList(), warnings.distinct())
        } else {
            staged = null
            PackStageResult(PackStageStatus.REJECTED, errors.distinct(), warnings.distinct())
        }
    }

    fun activateStaged(smokeTest: (IntelligencePackBundle) -> Boolean): Boolean {
        val candidate = staged ?: return false
        if (!runCatching { smokeTest(candidate) }.getOrDefault(false)) return false
        previous = active
        active = candidate
        staged = null
        return true
    }

    /** One-level rollback to the most recent previously active, validated bundle. */
    fun rollback(): Boolean {
        val rollbackTarget = previous ?: return false
        val current = active
        active = rollbackTarget
        previous = current
        staged = null
        return true
    }

    fun activeBundle(): IntelligencePackBundle? = active
    fun stagedBundle(): IntelligencePackBundle? = staged

    fun state(): PackActivationState = PackActivationState(
        activeDataVersion = active?.dataPack?.manifest?.version,
        activeMetaVersion = active?.metaPack?.manifest?.version,
        previousDataVersion = previous?.dataPack?.manifest?.version,
        previousMetaVersion = previous?.metaPack?.manifest?.version,
        hasStagedBundle = staged != null
    )
}
