package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.RulesetEligibility
import com.rui.pvpgo.domain.RulesetFilter
import com.rui.pvpgo.domain.RulesetFilterKind
import com.rui.pvpgo.domain.RulesetIneligibilityReason
import com.rui.pvpgo.domain.RulesetRef
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object RulesetEngine {
    fun evaluate(
        ruleset: PvPRuleset,
        species: PokemonSpecies,
        level: Double? = null,
        cp: Int? = null
    ): RulesetEligibility {
        val reasons = mutableListOf<RulesetIneligibilityReason>()

        if (!species.released && !ruleset.allowUnreleased) {
            reasons += RulesetIneligibilityReason.UNKNOWN_OR_UNRELEASED
        }
        if (level != null && level !in ruleset.minLevel..ruleset.maxLevel) {
            reasons += RulesetIneligibilityReason.LEVEL_OUT_OF_RANGE
        }
        if (cp != null && ruleset.cpCap != null && cp > ruleset.cpCap) {
            reasons += RulesetIneligibilityReason.CP_CAP_EXCEEDED
        }
        if (species.isShadow && !ruleset.allowShadow) {
            reasons += RulesetIneligibilityReason.SHADOW_NOT_ALLOWED
        }
        if (species.isMega && !ruleset.allowMega) {
            reasons += RulesetIneligibilityReason.MEGA_NOT_ALLOWED
        }
        if (species.speciesId in ruleset.explicitlyExcludedSpeciesIds) {
            reasons += RulesetIneligibilityReason.EXPLICITLY_EXCLUDED
        }

        val explicitInclude = species.speciesId in ruleset.explicitlyIncludedSpeciesIds
        if (!explicitInclude && ruleset.includeFilters.any { !matches(it, species) }) {
            reasons += RulesetIneligibilityReason.INCLUDE_FILTER_NOT_MATCHED
        }
        if (ruleset.excludeFilters.any { matches(it, species) }) {
            reasons += RulesetIneligibilityReason.EXCLUDE_FILTER_MATCHED
        }

        return RulesetEligibility(reasons.isEmpty(), reasons.distinct())
    }

    fun ref(ruleset: PvPRuleset): RulesetRef = RulesetRef(
        id = ruleset.id,
        version = ruleset.version,
        contentHashSha256 = contentHash(ruleset)
    )

    fun contentHash(ruleset: PvPRuleset): String = sha256(canonical(ruleset))

    fun canonical(ruleset: PvPRuleset): String = buildString {
        append("id=").append(ruleset.id).append('\n')
        append("version=").append(ruleset.version).append('\n')
        append("title=").append(ruleset.title).append('\n')
        append("baseLeague=").append(ruleset.baseLeague.name).append('\n')
        append("cpCap=").append(ruleset.cpCap ?: "none").append('\n')
        append("levels=").append(ruleset.minLevel).append('-').append(ruleset.maxLevel).append('\n')
        append("rankingIvFloor=").append(ruleset.rankingIvFloor).append('\n')
        append("allowShadow=").append(ruleset.allowShadow).append('\n')
        append("allowMega=").append(ruleset.allowMega).append('\n')
        append("allowUnreleased=").append(ruleset.allowUnreleased).append('\n')
        append("source=").append(ruleset.sourceName).append('@').append(ruleset.sourceVersion).append('\n')
        append("generatedAt=").append(ruleset.generatedAtEpochMs).append('\n')
        append("explicitInclude=").append(ruleset.explicitlyIncludedSpeciesIds.sorted().joinToString(",")).append('\n')
        append("explicitExclude=").append(ruleset.explicitlyExcludedSpeciesIds.sorted().joinToString(",")).append('\n')
        ruleset.includeFilters.map(::canonicalFilter).sorted().forEach { append("include=").append(it).append('\n') }
        ruleset.excludeFilters.map(::canonicalFilter).sorted().forEach { append("exclude=").append(it).append('\n') }
    }

    private fun canonicalFilter(filter: RulesetFilter): String = when (filter.kind) {
        RulesetFilterKind.DEX_RANGE -> "DEX_RANGE:" + filter.dexRanges
            .sortedWith(compareBy({ it.start }, { it.endInclusive }))
            .joinToString(",") { "${it.start}-${it.endInclusive}" }
        else -> filter.kind.name + ":" + filter.values.map { it.lowercase() }.sorted().joinToString(",")
    }

    private fun matches(filter: RulesetFilter, species: PokemonSpecies): Boolean = when (filter.kind) {
        RulesetFilterKind.SPECIES_ID -> species.speciesId.lowercase() in filter.values.map { it.lowercase() }.toSet()
        RulesetFilterKind.TYPE -> species.types.any { type -> filter.values.any { it.equals(type, ignoreCase = true) } }
        RulesetFilterKind.TAG -> species.tags.any { tag -> filter.values.any { it.equals(tag, ignoreCase = true) } }
        RulesetFilterKind.FORM -> filter.values.any { it.equals(species.form, ignoreCase = true) }
        RulesetFilterKind.DEX_RANGE -> filter.dexRanges.any { species.dex in it }
        RulesetFilterKind.MOVE_ID -> {
            val moves = species.fastMoveIds + species.chargedMoveIds + species.eliteMoveIds
            moves.any { move -> filter.values.any { it.equals(move, ignoreCase = true) } }
        }
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

object StandardRulesets {
    fun forLeague(league: League, generatedAtEpochMs: Long = 0L): PvPRuleset = PvPRuleset(
        id = "standard-${league.name.lowercase()}",
        version = "1",
        title = when (league) {
            League.LITTLE -> "Little League"
            League.GREAT -> "Great League"
            League.ULTRA -> "Ultra League"
            League.MASTER -> "Master League"
        },
        baseLeague = league,
        cpCap = league.cpCap,
        allowShadow = true,
        allowMega = false,
        sourceName = "local-standard-rules",
        sourceVersion = "1",
        generatedAtEpochMs = generatedAtEpochMs
    )
}
