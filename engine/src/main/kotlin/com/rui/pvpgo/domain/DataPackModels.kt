package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvpMove

data class PackProvenance(
    val sourceName: String,
    val sourceRevision: String,
    val sourceUrl: String? = null
) {
    init {
        require(sourceName.isNotBlank())
        require(sourceRevision.isNotBlank())
    }
}

data class GameDataPackManifest(
    val packId: String,
    val version: String,
    val schemaVersion: Int,
    val generatedAtEpochMs: Long,
    val provenance: PackProvenance,
    val contentHashSha256: String,
    val pokemonCount: Int,
    val moveCount: Int,
    val cpmCount: Int,
    val typeChartVersion: String
) {
    init {
        require(packId.isNotBlank())
        require(version.isNotBlank())
        require(schemaVersion > 0)
        require(generatedAtEpochMs >= 0)
        require(contentHashSha256.matches(Regex("[0-9a-f]{64}")))
        require(pokemonCount >= 0 && moveCount >= 0 && cpmCount > 0)
        require(typeChartVersion.isNotBlank())
    }
}

/** F66 immutable factual game-data snapshot. Meta/rankings never belong in this pack. */
data class VersionedGameDataPack(
    val manifest: GameDataPackManifest,
    val pokemon: List<PokemonSpecies>,
    val moves: List<PvpMove>,
    val cpmByLevel: Map<Double, Double>,
    val supportedTypes: Set<String>
) {
    init {
        require(supportedTypes.isNotEmpty())
        require(supportedTypes.none { it.isBlank() })
    }
}

data class PackValidationReport(
    val valid: Boolean,
    val errors: List<String>,
    val warnings: List<String> = emptyList()
) {
    init { require(valid == errors.isEmpty()) }
}
