package com.rui.pvpgo

import com.rui.pvpgo.domain.BattleRecord
import com.rui.pvpgo.domain.MetaBuildEntry
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.engine.League
import kotlin.math.pow

enum class MetaBlendMode { GLOBAL, HYBRID, PERSONAL }

data class MetaBlendResult(
    val meta: VersionedMetaGroup?,
    val mode: MetaBlendMode,
    val rawBattleCount: Int,
    val matchedOpponentObservations: Int,
    val effectivePersonalObservations: Double,
    val personalBlendWeight: Double,
    val warning: String? = null,
    val refusalReason: String? = null
)

/**
 * Derived, non-persistent weighting bridge between the immutable global Meta Pack and local Battle Log.
 * It never writes back into reference.db and never creates an opponent build that was absent from the
 * validated global Meta Pack.
 */
object PersonalMetaTeamBridge {
    fun blend(
        global: VersionedMetaGroup,
        battles: List<BattleRecord>,
        mode: MetaBlendMode,
        nowEpochMs: Long = System.currentTimeMillis(),
        recencyHalfLifeDays: Double = 21.0,
        shrinkageK: Double = 40.0
    ): MetaBlendResult {
        require(recencyHalfLifeDays > 0.0)
        require(shrinkageK > 0.0)

        val leagueBattles = battles.filter { it.league == global.league }
        if (mode == MetaBlendMode.GLOBAL) {
            return MetaBlendResult(
                meta = global,
                mode = mode,
                rawBattleCount = leagueBattles.size,
                matchedOpponentObservations = 0,
                effectivePersonalObservations = 0.0,
                personalBlendWeight = 0.0
            )
        }

        val globalSpecies = global.entries.map { it.opponent.speciesId }.toSet()
        val personalBySpecies = linkedMapOf<String, Double>()
        var rawMatchedObservations = 0
        for (battle in leagueBattles) {
            val ageMs = (nowEpochMs - battle.playedAtEpochMs).coerceAtLeast(0L)
            val ageDays = ageMs / 86_400_000.0
            val recencyWeight = 0.5.pow(ageDays / recencyHalfLifeDays)
            for (speciesId in battle.opponentPokemon.map { it.speciesId }.distinct()) {
                if (speciesId !in globalSpecies) continue
                rawMatchedObservations++
                personalBySpecies[speciesId] = (personalBySpecies[speciesId] ?: 0.0) + recencyWeight
            }
        }

        val effectiveN = personalBySpecies.values.sum()
        if (mode == MetaBlendMode.PERSONAL && effectiveN <= 0.0) {
            return MetaBlendResult(
                meta = null,
                mode = mode,
                rawBattleCount = leagueBattles.size,
                matchedOpponentObservations = rawMatchedObservations,
                effectivePersonalObservations = effectiveN,
                personalBlendWeight = 1.0,
                refusalReason = "Ainda não existem adversários do Battle Log desta liga que possam ser ligados ao Meta Pack ativo."
            )
        }

        val globalTotal = global.entries.sumOf { it.weight }
        val personalTotal = personalBySpecies.values.sum()
        val lambda = if (mode == MetaBlendMode.PERSONAL) 1.0 else effectiveN / (effectiveN + shrinkageK)

        val derivedEntries: List<MetaBuildEntry> = when (mode) {
            MetaBlendMode.GLOBAL -> global.entries
            MetaBlendMode.HYBRID -> global.entries.map { entry ->
                val globalShare = entry.weight / globalTotal
                val personalShare = if (personalTotal > 0.0) (personalBySpecies[entry.opponent.speciesId] ?: 0.0) / personalTotal else 0.0
                val blendedShare = (1.0 - lambda) * globalShare + lambda * personalShare
                entry.copy(
                    weight = (blendedShare * global.entries.size).coerceAtLeast(1e-9),
                    labels = entry.labels + setOf("hybrid-meta", "personal-lambda:${"%.4f".format(java.util.Locale.US, lambda)}")
                )
            }
            MetaBlendMode.PERSONAL -> global.entries.mapNotNull { entry ->
                val personal = personalBySpecies[entry.opponent.speciesId] ?: return@mapNotNull null
                val share = personal / personalTotal
                entry.copy(
                    weight = (share * personalBySpecies.size.coerceAtLeast(1)).coerceAtLeast(1e-9),
                    labels = entry.labels + "personal-meta"
                )
            }
        }

        if (derivedEntries.isEmpty()) {
            return MetaBlendResult(
                meta = null,
                mode = mode,
                rawBattleCount = leagueBattles.size,
                matchedOpponentObservations = rawMatchedObservations,
                effectivePersonalObservations = effectiveN,
                personalBlendWeight = lambda,
                refusalReason = "A mistura produziu zero entradas avaliáveis; análise recusada."
            )
        }

        val suffix = when (mode) {
            MetaBlendMode.GLOBAL -> "global"
            MetaBlendMode.HYBRID -> "hybrid"
            MetaBlendMode.PERSONAL -> "personal"
        }
        val source = when (mode) {
            MetaBlendMode.GLOBAL -> global.sourceName
            MetaBlendMode.HYBRID -> "${global.sourceName} + local Battle Log"
            MetaBlendMode.PERSONAL -> "local Battle Log constrained by ${global.sourceName}"
        }
        val derived = global.copy(
            id = "${global.id}-$suffix",
            sourceName = source,
            sourceVersion = "${global.sourceVersion}-$suffix",
            entries = derivedEntries
        )
        val warning = when {
            mode == MetaBlendMode.HYBRID && effectiveN < 5.0 -> "A componente pessoal é muito pequena; o resultado continua dominado pelo Meta Pack global."
            mode == MetaBlendMode.PERSONAL && effectiveN < 10.0 -> "Amostra pessoal pequena; usa este modo como exploração, não como conclusão."
            else -> null
        }
        return MetaBlendResult(
            meta = derived,
            mode = mode,
            rawBattleCount = leagueBattles.size,
            matchedOpponentObservations = rawMatchedObservations,
            effectivePersonalObservations = effectiveN,
            personalBlendWeight = lambda,
            warning = warning
        )
    }
}
