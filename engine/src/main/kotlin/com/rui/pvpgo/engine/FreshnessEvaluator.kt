package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.FreshnessPolicy
import com.rui.pvpgo.domain.FreshnessStatus
import com.rui.pvpgo.domain.IntelligenceFreshnessSnapshot
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PokemonFreshness
import com.rui.pvpgo.domain.PokemonFreshnessStatus
import com.rui.pvpgo.domain.SourceFreshness
import com.rui.pvpgo.domain.VersionedGameDataPack
import com.rui.pvpgo.domain.VersionedMetaPack

object FreshnessEvaluator {
    val defaultDataPolicy = FreshnessPolicy(
        freshForMs = 7L * 24 * 60 * 60 * 1000,
        staleAfterMs = 30L * 24 * 60 * 60 * 1000
    )
    val defaultMetaPolicy = FreshnessPolicy(
        freshForMs = 3L * 24 * 60 * 60 * 1000,
        staleAfterMs = 14L * 24 * 60 * 60 * 1000
    )

    fun evaluate(
        generatedAtEpochMs: Long?,
        checkedAtEpochMs: Long?,
        nowEpochMs: Long,
        activeRevision: String?,
        latestRevision: String?,
        policy: FreshnessPolicy
    ): SourceFreshness {
        require(nowEpochMs >= 0)
        val age = generatedAtEpochMs?.let { (nowEpochMs - it).coerceAtLeast(0) }
        val status = when {
            generatedAtEpochMs == null -> FreshnessStatus.UNKNOWN
            age!! <= policy.freshForMs -> FreshnessStatus.FRESH
            age < policy.staleAfterMs -> FreshnessStatus.AGING
            else -> FreshnessStatus.STALE
        }
        val updateAvailable = !activeRevision.isNullOrBlank() && !latestRevision.isNullOrBlank() && activeRevision != latestRevision
        val message = when {
            status == FreshnessStatus.UNKNOWN -> "Data da fonte desconhecida; confirmar antes de advice forte."
            updateAvailable -> "Existe uma revisão mais recente da fonte."
            status == FreshnessStatus.FRESH -> "Fonte dentro da janela de frescura configurada."
            status == FreshnessStatus.AGING -> "Fonte a envelhecer; manter proveniência visível."
            else -> "Fonte stale; recomendações devem ser marcadas como potencialmente desatualizadas."
        }
        return SourceFreshness(status, generatedAtEpochMs, checkedAtEpochMs, age, updateAvailable, activeRevision, latestRevision, message)
    }

    fun intelligence(
        dataPack: VersionedGameDataPack,
        metaPack: VersionedMetaPack?,
        nowEpochMs: Long,
        checkedAtEpochMs: Long?,
        latestRevision: String?,
        dataPolicy: FreshnessPolicy = defaultDataPolicy,
        metaPolicy: FreshnessPolicy = defaultMetaPolicy
    ): IntelligenceFreshnessSnapshot {
        val data = evaluate(
            dataPack.manifest.generatedAtEpochMs,
            checkedAtEpochMs,
            nowEpochMs,
            dataPack.manifest.provenance.sourceRevision,
            latestRevision,
            dataPolicy
        )
        val meta = metaPack?.let {
            evaluate(
                it.manifest.generatedAtEpochMs,
                checkedAtEpochMs,
                nowEpochMs,
                it.manifest.sourceVersion,
                latestRevision,
                metaPolicy
            )
        }
        return IntelligenceFreshnessSnapshot(data, meta)
    }
}

object PokemonFreshnessEvaluator {
    data class Policy(
        val verifiedForMs: Long = 30L * 24 * 60 * 60 * 1000,
        val staleAfterMs: Long = 90L * 24 * 60 * 60 * 1000
    ) {
        init {
            require(verifiedForMs >= 0)
            require(staleAfterMs >= verifiedForMs)
        }
    }

    fun evaluate(owned: OwnedPokemon, nowEpochMs: Long, policy: Policy = Policy()): PokemonFreshness {
        val last = owned.lastVerifiedAtEpochMs
        val age = last?.let { (nowEpochMs - it).coerceAtLeast(0) }
        val status = when {
            owned.uncertainFields.isNotEmpty() -> PokemonFreshnessStatus.CHECK_SOON
            last == null -> PokemonFreshnessStatus.UNKNOWN
            age!! <= policy.verifiedForMs -> PokemonFreshnessStatus.VERIFIED
            age < policy.staleAfterMs -> PokemonFreshnessStatus.CHECK_SOON
            else -> PokemonFreshnessStatus.STALE
        }
        return PokemonFreshness(owned.id, status, last, age, owned.uncertainFields)
    }
}
