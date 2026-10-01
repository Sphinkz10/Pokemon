package com.rui.pvpgo.domain

data class MetaPackManifest(
    val packId: String,
    val version: String,
    val schemaVersion: Int,
    val generatedAtEpochMs: Long,
    val sourceName: String,
    val sourceVersion: String,
    val dataPackId: String,
    val dataPackVersion: String,
    val dataPackHashSha256: String,
    val contentHashSha256: String,
    val groupCount: Int
) {
    init {
        require(packId.isNotBlank() && version.isNotBlank())
        require(schemaVersion > 0)
        require(generatedAtEpochMs >= 0)
        require(sourceName.isNotBlank() && sourceVersion.isNotBlank())
        require(dataPackId.isNotBlank() && dataPackVersion.isNotBlank())
        require(dataPackHashSha256.matches(Regex("[0-9a-f]{64}")))
        require(contentHashSha256.matches(Regex("[0-9a-f]{64}")))
        require(groupCount > 0)
    }
}

/** F67 meta is derived/versioned opinion; it is never merged into factual Game Data. */
data class VersionedMetaPack(
    val manifest: MetaPackManifest,
    val rulesets: List<PvPRuleset>,
    val groups: List<VersionedMetaGroup>
) {
    init {
        require(rulesets.isNotEmpty())
        require(groups.isNotEmpty())
    }
}
