package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.League

/** Inclusive Pokedex range used by cup eligibility filters. */
data class DexRangeSpec(val start: Int, val endInclusive: Int) {
    init {
        require(start > 0)
        require(endInclusive >= start)
    }

    operator fun contains(dex: Int): Boolean = dex in start..endInclusive
}

enum class RulesetFilterKind {
    SPECIES_ID,
    TYPE,
    TAG,
    FORM,
    DEX_RANGE,
    MOVE_ID
}

/**
 * One eligibility predicate. Values inside a filter are OR-ed; inclusion filters are AND-ed;
 * exclusion filters are OR-ed. This mirrors the useful semantics of cup builders without tying
 * the app to any one provider's JSON schema.
 */
data class RulesetFilter(
    val kind: RulesetFilterKind,
    val values: Set<String> = emptySet(),
    val dexRanges: List<DexRangeSpec> = emptyList()
) {
    init {
        when (kind) {
            RulesetFilterKind.DEX_RANGE -> require(dexRanges.isNotEmpty()) { "DEX_RANGE requires at least one range" }
            else -> require(values.isNotEmpty()) { "$kind requires at least one value" }
        }
        require(values.none { it.isBlank() })
    }
}

/**
 * F65 ruleset snapshot. A cup is more than a CP cap: its filters and hard build constraints are
 * explicit, versioned input to every downstream recommendation.
 */
data class PvPRuleset(
    val id: String,
    val version: String,
    val title: String,
    val baseLeague: League,
    val cpCap: Int? = baseLeague.cpCap,
    val minLevel: Double = 1.0,
    val maxLevel: Double = 51.0,
    val rankingIvFloor: Int = 0,
    val allowShadow: Boolean = true,
    val allowMega: Boolean = false,
    val allowUnreleased: Boolean = false,
    val includeFilters: List<RulesetFilter> = emptyList(),
    val excludeFilters: List<RulesetFilter> = emptyList(),
    /** Explicit includes bypass includeFilters only; hard limits and excludes still apply. */
    val explicitlyIncludedSpeciesIds: Set<String> = emptySet(),
    val explicitlyExcludedSpeciesIds: Set<String> = emptySet(),
    val sourceName: String,
    val sourceVersion: String,
    val generatedAtEpochMs: Long
) {
    init {
        require(id.isNotBlank())
        require(version.isNotBlank())
        require(title.isNotBlank())
        require(cpCap == null || cpCap > 0)
        require(minLevel in 1.0..51.0 && maxLevel in 1.0..51.0 && minLevel <= maxLevel)
        require(minLevel * 2 == (minLevel * 2).toInt().toDouble())
        require(maxLevel * 2 == (maxLevel * 2).toInt().toDouble())
        require(rankingIvFloor in 0..15)
        require(sourceName.isNotBlank())
        require(sourceVersion.isNotBlank())
        require(generatedAtEpochMs >= 0)
        require(explicitlyIncludedSpeciesIds.none { it.isBlank() })
        require(explicitlyExcludedSpeciesIds.none { it.isBlank() })
        require(explicitlyIncludedSpeciesIds.intersect(explicitlyExcludedSpeciesIds).isEmpty()) {
            "A species cannot be both explicitly included and excluded"
        }
    }
}

data class RulesetRef(
    val id: String,
    val version: String,
    val contentHashSha256: String
) {
    init {
        require(id.isNotBlank())
        require(version.isNotBlank())
        require(contentHashSha256.matches(Regex("[0-9a-f]{64}")))
    }
}

enum class RulesetIneligibilityReason {
    UNKNOWN_OR_UNRELEASED,
    CP_CAP_EXCEEDED,
    LEVEL_OUT_OF_RANGE,
    SHADOW_NOT_ALLOWED,
    MEGA_NOT_ALLOWED,
    EXPLICITLY_EXCLUDED,
    INCLUDE_FILTER_NOT_MATCHED,
    EXCLUDE_FILTER_MATCHED
}

data class RulesetEligibility(
    val eligible: Boolean,
    val reasons: List<RulesetIneligibilityReason>
) {
    init {
        require(eligible == reasons.isEmpty())
    }
}
