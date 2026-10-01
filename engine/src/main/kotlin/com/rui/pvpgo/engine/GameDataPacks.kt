package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.GameDataPackManifest
import com.rui.pvpgo.domain.PackProvenance
import com.rui.pvpgo.domain.PackValidationReport
import com.rui.pvpgo.domain.VersionedGameDataPack
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object VersionedGameDataPackFactory {
    val defaultTypes: Set<String> = setOf(
        "bug", "dark", "dragon", "electric", "fairy", "fighting", "fire", "flying", "ghost",
        "grass", "ground", "ice", "normal", "poison", "psychic", "rock", "steel", "water"
    )

    fun create(
        packId: String,
        version: String,
        generatedAtEpochMs: Long,
        provenance: PackProvenance,
        pokemon: List<PokemonSpecies>,
        moves: List<PvpMove>,
        schemaVersion: Int = 1,
        typeChartVersion: String = "pokemon-go-pvp-types-v1",
        supportedTypes: Set<String> = defaultTypes,
        cpmByLevel: Map<Double, Double> = CpMultipliers.supportedLevels.associateWith(CpMultipliers::at)
    ): VersionedGameDataPack {
        val hash = GameDataPackHashing.contentHash(
            pokemon = pokemon,
            moves = moves,
            cpmByLevel = cpmByLevel,
            supportedTypes = supportedTypes,
            typeChartVersion = typeChartVersion
        )
        return VersionedGameDataPack(
            manifest = GameDataPackManifest(
                packId = packId,
                version = version,
                schemaVersion = schemaVersion,
                generatedAtEpochMs = generatedAtEpochMs,
                provenance = provenance,
                contentHashSha256 = hash,
                pokemonCount = pokemon.size,
                moveCount = moves.size,
                cpmCount = cpmByLevel.size,
                typeChartVersion = typeChartVersion
            ),
            pokemon = pokemon,
            moves = moves,
            cpmByLevel = cpmByLevel.toSortedMap(),
            supportedTypes = supportedTypes.map { it.lowercase() }.toSortedSet()
        )
    }
}

object GameDataPackValidator {
    fun validate(pack: VersionedGameDataPack): PackValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val m = pack.manifest

        if (m.pokemonCount != pack.pokemon.size) errors += "pokemonCount manifest mismatch"
        if (m.moveCount != pack.moves.size) errors += "moveCount manifest mismatch"
        if (m.cpmCount != pack.cpmByLevel.size) errors += "cpmCount manifest mismatch"
        if (pack.pokemon.map { it.speciesId }.any { it.isBlank() }) errors += "blank speciesId"
        if (pack.pokemon.map { it.speciesId }.distinct().size != pack.pokemon.size) errors += "duplicate speciesId"
        if (pack.moves.map { it.moveId }.distinct().size != pack.moves.size) errors += "duplicate moveId"

        val moveIds = pack.moves.map { it.moveId }.toSet()
        pack.pokemon.forEach { species ->
            val referenced = species.fastMoveIds + species.chargedMoveIds + species.eliteMoveIds
            val missing = referenced.filter { it !in moveIds }
            if (missing.isNotEmpty()) warnings += "${species.speciesId}: missing move definitions ${missing.sorted().joinToString(",")}" 
            val unknownTypes = species.types.filter { it.lowercase() !in pack.supportedTypes.map(String::lowercase).toSet() }
            if (unknownTypes.isNotEmpty()) errors += "${species.speciesId}: unknown types ${unknownTypes.joinToString(",")}" 
        }
        pack.moves.forEach { move ->
            if (move.type.lowercase() !in pack.supportedTypes.map(String::lowercase).toSet()) {
                errors += "${move.moveId}: unknown type ${move.type}"
            }
        }

        if (pack.cpmByLevel.keys.sorted() != CpMultipliers.supportedLevels) {
            errors += "CPM levels must exactly cover 1.0..51.0 in 0.5 increments"
        }
        if (pack.cpmByLevel.values.any { !it.isFinite() || it <= 0.0 }) errors += "invalid CPM value"

        val actualHash = GameDataPackHashing.contentHash(
            pack.pokemon, pack.moves, pack.cpmByLevel, pack.supportedTypes, m.typeChartVersion
        )
        if (actualHash != m.contentHashSha256) errors += "contentHashSha256 mismatch"

        return PackValidationReport(errors.isEmpty(), errors.distinct(), warnings.distinct())
    }
}

object GameDataPackHashing {
    fun contentHash(
        pokemon: List<PokemonSpecies>,
        moves: List<PvpMove>,
        cpmByLevel: Map<Double, Double>,
        supportedTypes: Set<String>,
        typeChartVersion: String
    ): String = sha256(canonical(pokemon, moves, cpmByLevel, supportedTypes, typeChartVersion))

    fun canonical(
        pokemon: List<PokemonSpecies>,
        moves: List<PvpMove>,
        cpmByLevel: Map<Double, Double>,
        supportedTypes: Set<String>,
        typeChartVersion: String
    ): String = buildString {
        append("typeChart=").append(typeChartVersion).append('\n')
        append("types=").append(supportedTypes.map { it.lowercase() }.sorted().joinToString(",")).append('\n')
        cpmByLevel.toSortedMap().forEach { (level, cpm) ->
            append("cpm|").append(fmt(level)).append('|').append(fmt(cpm)).append('\n')
        }
        pokemon.sortedBy { it.speciesId }.forEach { p ->
            append("pokemon|").append(p.speciesId).append('|').append(p.dex).append('|')
                .append(p.name).append('|').append(p.form).append('|')
                .append(p.types.map { it.lowercase() }.sorted().joinToString(",")).append('|')
                .append(p.tags.map { it.lowercase() }.sorted().joinToString(",")).append('|')
                .append(p.baseAttack).append('|').append(p.baseDefense).append('|').append(p.baseStamina).append('|')
                .append(p.released).append('|')
                .append(p.fastMoveIds.sorted().joinToString(",")).append('|')
                .append(p.chargedMoveIds.sorted().joinToString(",")).append('|')
                .append(p.eliteMoveIds.sorted().joinToString(",")).append('\n')
        }
        moves.sortedBy { it.moveId }.forEach { m ->
            append("move|").append(m.moveId).append('|').append(m.name).append('|').append(m.type.lowercase()).append('|')
                .append(m.power).append('|').append(m.energyCost).append('|').append(m.energyGain).append('|')
                .append(m.turns).append('|').append(m.cooldownMs).append('|')
                .append(m.attackerAttackStageDelta).append('|').append(m.attackerDefenseStageDelta).append('|')
                .append(m.opponentAttackStageDelta).append('|').append(m.opponentDefenseStageDelta).append('|')
                .append(fmt(m.buffApplyChance)).append('|')
                .append(m.tags.sorted().joinToString(",")).append('\n')
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.12f", value)

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
