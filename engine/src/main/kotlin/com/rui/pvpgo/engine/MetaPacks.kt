package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.MetaPackManifest
import com.rui.pvpgo.domain.PackValidationReport
import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.VersionedGameDataPack
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.domain.VersionedMetaPack
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object VersionedMetaPackFactory {
    fun create(
        packId: String,
        version: String,
        generatedAtEpochMs: Long,
        sourceName: String,
        sourceVersion: String,
        dataPack: VersionedGameDataPack,
        rulesets: List<PvPRuleset>,
        groups: List<VersionedMetaGroup>,
        schemaVersion: Int = 1
    ): VersionedMetaPack {
        val hash = MetaPackHashing.contentHash(rulesets, groups)
        return VersionedMetaPack(
            manifest = MetaPackManifest(
                packId = packId,
                version = version,
                schemaVersion = schemaVersion,
                generatedAtEpochMs = generatedAtEpochMs,
                sourceName = sourceName,
                sourceVersion = sourceVersion,
                dataPackId = dataPack.manifest.packId,
                dataPackVersion = dataPack.manifest.version,
                dataPackHashSha256 = dataPack.manifest.contentHashSha256,
                contentHashSha256 = hash,
                groupCount = groups.size
            ),
            rulesets = rulesets,
            groups = groups
        )
    }
}

object MetaPackValidator {
    fun validate(pack: VersionedMetaPack, dataPack: VersionedGameDataPack): PackValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val manifest = pack.manifest

        if (manifest.dataPackId != dataPack.manifest.packId) errors += "dataPackId mismatch"
        if (manifest.dataPackVersion != dataPack.manifest.version) errors += "dataPackVersion mismatch"
        if (manifest.dataPackHashSha256 != dataPack.manifest.contentHashSha256) errors += "dataPackHashSha256 mismatch"
        if (manifest.groupCount != pack.groups.size) errors += "groupCount manifest mismatch"
        if (pack.groups.map { it.id }.distinct().size != pack.groups.size) errors += "duplicate meta group id"
        if (pack.rulesets.map { it.id to it.version }.distinct().size != pack.rulesets.size) errors += "duplicate ruleset id/version"

        val rulesets = pack.rulesets.associateBy { it.id to it.version }
        val speciesById = dataPack.pokemon.associateBy { it.speciesId }
        val movesById = dataPack.moves.associateBy { it.moveId }

        pack.groups.forEach groupLoop@ { group ->
            val rulesetId = group.rulesetId
            val rulesetVersion = group.rulesetVersion
            if (rulesetId == null || rulesetVersion == null) {
                errors += "${group.id}: missing ruleset reference"
                return@groupLoop
            }
            val ruleset = rulesets[rulesetId to rulesetVersion]
            if (ruleset == null) {
                errors += "${group.id}: unresolved ruleset $rulesetId@$rulesetVersion"
                return@groupLoop
            }
            if (group.league != ruleset.baseLeague) errors += "${group.id}: league/ruleset mismatch"
            if (group.dataPackHashSha256 != manifest.dataPackHashSha256) errors += "${group.id}: data-pack hash mismatch"

            group.entries.forEach { entry ->
                val opponent = entry.opponent
                val species = speciesById[opponent.speciesId]
                if (species == null) {
                    errors += "${group.id}/${entry.key}: unknown species ${opponent.speciesId}"
                    return@forEach
                }
                val fast = opponent.fastMoveId
                if (fast == null || fast !in movesById) errors += "${group.id}/${entry.key}: unknown Fast Move $fast"
                opponent.chargedMoveIds.forEach { if (it !in movesById) errors += "${group.id}/${entry.key}: unknown Charged Move $it" }
                val iv = opponent.iv
                val level = opponent.level
                if (iv == null || level == null) {
                    errors += "${group.id}/${entry.key}: incomplete opponent build"
                } else {
                    val cp = runCatching { PokemonMath.cp(species, iv, level) }.getOrNull()
                    if (cp == null) {
                        errors += "${group.id}/${entry.key}: invalid level/IV"
                    } else {
                        val eligibility = RulesetEngine.evaluate(ruleset, species, level, cp)
                        if (!eligibility.eligible) {
                            errors += "${group.id}/${entry.key}: build violates ruleset (${eligibility.reasons.joinToString()})"
                        }
                    }
                }
            }
        }

        val actualHash = MetaPackHashing.contentHash(pack.rulesets, pack.groups)
        if (actualHash != manifest.contentHashSha256) errors += "contentHashSha256 mismatch"
        if (pack.groups.any { it.totalWeight <= 0.0 }) warnings += "meta group with non-positive total weight"

        return PackValidationReport(errors.isEmpty(), errors.distinct(), warnings.distinct())
    }
}

object MetaPackHashing {
    fun contentHash(rulesets: List<PvPRuleset>, groups: List<VersionedMetaGroup>): String = sha256(canonical(rulesets, groups))

    fun canonical(rulesets: List<PvPRuleset>, groups: List<VersionedMetaGroup>): String = buildString {
        rulesets.sortedWith(compareBy<PvPRuleset> { it.id }.thenBy { it.version }).forEach { ruleset ->
            append("rulesetHash|").append(ruleset.id).append('|').append(ruleset.version).append('|')
                .append(RulesetEngine.contentHash(ruleset)).append('\n')
        }
        groups.sortedBy { it.id }.forEach { group ->
            append("group|").append(group.id).append('|').append(group.league.name).append('|')
                .append(group.seasonId).append('|').append(group.cupId ?: "").append('|')
                .append(group.sourceName).append('|').append(group.sourceVersion).append('|')
                .append(group.generatedAtEpochMs).append('|')
                .append(group.rulesetId ?: "").append('|').append(group.rulesetVersion ?: "").append('|')
                .append(group.dataPackHashSha256 ?: "").append('\n')
            group.entries.sortedBy { it.key }.forEach { entry ->
                val o = entry.opponent
                append("entry|").append(entry.key).append('|').append(o.speciesId).append('|')
                    .append(o.fastMoveId ?: "").append('|').append(o.chargedMoveIds.joinToString(",")).append('|')
                    .append(o.iv?.toString() ?: "").append('|').append(o.level ?: "").append('|')
                    .append(o.isShadow ?: "").append('|').append(fmt(entry.weight)).append('|')
                    .append(entry.labels.sorted().joinToString(",")).append('\n')
            }
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.12f", value)
    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
