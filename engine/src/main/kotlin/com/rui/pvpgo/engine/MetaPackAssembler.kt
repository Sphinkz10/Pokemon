package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.MetaBuildEntry
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.VersionedGameDataPack
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.domain.VersionedMetaPack

/**
 * F67 adapter from a source-labelled ranking/moveset snapshot into the strict Meta Pack contract.
 * Ranking score/order is never interpreted as prevalence or win probability; every entry receives
 * neutral weight 1.0 unless a future provider supplies a separately justified prevalence measure.
 */
object MetaPackAssembler {

    fun fromProviderSnapshot(
        packId: String,
        packVersion: String,
        dataPack: VersionedGameDataPack,
        ruleset: PvPRuleset,
        snapshot: PvpokeProviderSnapshot,
        seasonId: String,
        generatedAtEpochMs: Long = snapshot.fetchedAtEpochMs,
        topN: Int = 50
    ): VersionedMetaPack {
        require(snapshot.league == ruleset.baseLeague) { "Provider snapshot league/ruleset mismatch" }
        return fromRecommendedMovesets(
            packId = packId,
            packVersion = packVersion,
            dataPack = dataPack,
            ruleset = ruleset,
            recommendations = snapshot.recommendations,
            seasonId = seasonId,
            cupId = snapshot.cup,
            sourceName = snapshot.sourceName,
            sourceVersion = snapshot.sourceVersion,
            generatedAtEpochMs = generatedAtEpochMs,
            topN = topN
        )
    }
    fun fromRecommendedMovesets(
        packId: String,
        packVersion: String,
        dataPack: VersionedGameDataPack,
        ruleset: PvPRuleset,
        recommendations: Collection<RecommendedMoveset>,
        seasonId: String,
        cupId: String? = null,
        sourceName: String,
        sourceVersion: String,
        generatedAtEpochMs: Long,
        topN: Int = 50
    ): VersionedMetaPack {
        require(topN in 1..500)
        require(seasonId.isNotBlank())
        require(sourceName.isNotBlank() && sourceVersion.isNotBlank())
        require(ruleset.cpCap == ruleset.baseLeague.cpCap) {
            "Current assembler supports standard league CP caps; custom-cap ranking needs a generic-cap ranker"
        }

        val speciesById = dataPack.pokemon.associateBy { it.speciesId }
        val moveIds = dataPack.moves.map { it.moveId }.toSet()

        val ranked = recommendations
            .filter { it.league == ruleset.baseLeague }
            .sortedWith(compareByDescending<RecommendedMoveset> { it.metaScore ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.speciesId })

        val entries = mutableListOf<MetaBuildEntry>()
        for (recommendation in ranked) {
            if (entries.size >= topN) break
            val species = speciesById[recommendation.speciesId] ?: continue
            if (recommendation.fastMoveId !in moveIds || recommendation.chargedMoveIds.any { it !in moveIds }) continue
            val rank1 = PvPRanker.rank(
                species,
                ruleset.baseLeague,
                RankSettings(
                    minLevel = ruleset.minLevel,
                    maxLevel = ruleset.maxLevel,
                    ivFloor = ruleset.rankingIvFloor
                )
            ).firstOrNull() ?: continue
            val eligibility = RulesetEngine.evaluate(ruleset, species, rank1.level, rank1.cp)
            if (!eligibility.eligible) continue

            val labels = buildSet {
                add("source-ranking")
                add("rank:${entries.size + 1}")
                recommendation.metaScore?.let { add("source-score:${"%.3f".format(java.util.Locale.US, it)}") }
            }
            entries += MetaBuildEntry(
                key = "${species.speciesId}-overall",
                opponent = OpponentBuild(
                    speciesId = species.speciesId,
                    fastMoveId = recommendation.fastMoveId,
                    chargedMoveIds = recommendation.chargedMoveIds.take(2),
                    iv = rank1.iv,
                    level = rank1.level,
                    isShadow = species.isShadow
                ),
                weight = 1.0,
                labels = labels
            )
        }
        require(entries.isNotEmpty()) { "No ranking entries could be assembled into an eligible meta group" }

        val group = VersionedMetaGroup(
            id = "${ruleset.id}-overall-$sourceVersion",
            league = ruleset.baseLeague,
            seasonId = seasonId,
            cupId = cupId,
            sourceName = sourceName,
            sourceVersion = sourceVersion,
            generatedAtEpochMs = generatedAtEpochMs,
            entries = entries,
            rulesetId = ruleset.id,
            rulesetVersion = ruleset.version,
            dataPackHashSha256 = dataPack.manifest.contentHashSha256
        )
        return VersionedMetaPackFactory.create(
            packId = packId,
            version = packVersion,
            generatedAtEpochMs = generatedAtEpochMs,
            sourceName = sourceName,
            sourceVersion = sourceVersion,
            dataPack = dataPack,
            rulesets = listOf(ruleset),
            groups = listOf(group)
        )
    }
}
