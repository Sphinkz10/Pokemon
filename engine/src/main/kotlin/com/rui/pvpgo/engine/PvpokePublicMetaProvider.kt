package com.rui.pvpgo.engine

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** One immutable PvPoke ranking row decoded from the public generated ranking JSON. */
data class PvpokeRankingRecord(
    val speciesId: String,
    val moveset: List<String>,
    val score: Double?
) {
    init {
        require(speciesId.isNotBlank())
        require(moveset.size >= 2)
    }
}

data class PvpokeFormatRecord(
    val title: String,
    val cup: String,
    val cp: Int,
    val showCup: Boolean
)

data class PvpokeFetchedText(
    val body: String,
    val fetchedAtEpochMs: Long,
    val etag: String? = null,
    val lastModified: String? = null
)

interface PvpokeSourceTransport {
    /** Resolve the public branch once; every file in one snapshot must use this exact revision. */
    fun resolveMasterRevision(): String
    fun getPinnedText(revision: String, path: String): PvpokeFetchedText
}

interface PvpokePayloadDecoder {
    fun decodeRankings(json: String): List<PvpokeRankingRecord>
    fun decodeFormats(json: String): List<PvpokeFormatRecord>
}

data class PvpokeProviderSnapshot(
    val revision: String,
    val fetchedAtEpochMs: Long,
    val league: League,
    val cup: String,
    val rankingsPath: String,
    val formatsPath: String,
    val rankingsSha256: String,
    val formatsSha256: String,
    val recommendations: List<RecommendedMoveset>,
    val visibleFormats: List<PvpokeFormatRecord>
) {
    init {
        require(revision.matches(Regex("[0-9a-fA-F]{7,40}")))
        require(cup.isNotBlank())
        require(rankingsSha256.matches(Regex("[0-9a-f]{64}")))
        require(formatsSha256.matches(Regex("[0-9a-f]{64}")))
        require(recommendations.isNotEmpty())
    }

    val sourceName: String get() = "PvPoke public rankings"
    val sourceVersion: String get() = revision.lowercase()
    val sourceUrl: String get() = PvpokePublicPaths.githubTreeUrl(revision)
}

object PvpokePublicPaths {
    const val formatsPath = "src/data/gamemaster/formats.json"

    fun rankingsPath(cup: String, league: League): String {
        require(cup.matches(Regex("[A-Za-z0-9_-]+"))) { "Unsafe cup id" }
        val cap = when (league) {
            League.LITTLE -> 500
            League.GREAT -> 1500
            League.ULTRA -> 2500
            League.MASTER -> 10000
        }
        return "src/data/rankings/$cup/overall/rankings-$cap.json"
    }

    fun rawUrl(revision: String, path: String): String =
        "https://raw.githubusercontent.com/pvpoke/pvpoke/$revision/$path"

    fun githubTreeUrl(revision: String): String = "https://github.com/pvpoke/pvpoke/tree/$revision"
}

/**
 * F67 public provider foundation.
 *
 * It intentionally resolves one revision first and pins both formats + rankings to it. This avoids
 * a mixed-revision snapshot when upstream changes between requests. Ranking score remains metadata,
 * never prevalence or a win probability.
 */
class PvpokePublicMetaProvider(
    private val transport: PvpokeSourceTransport,
    private val decoder: PvpokePayloadDecoder
) {
    fun fetch(league: League, cup: String = "all"): PvpokeProviderSnapshot {
        val revision = transport.resolveMasterRevision().trim().lowercase()
        require(revision.matches(Regex("[0-9a-f]{7,40}"))) { "Invalid upstream revision" }
        val rankingsPath = PvpokePublicPaths.rankingsPath(cup, league)
        val formats = transport.getPinnedText(revision, PvpokePublicPaths.formatsPath)
        val rankings = transport.getPinnedText(revision, rankingsPath)
        val decoded = decoder.decodeRankings(rankings.body)
            .mapNotNull { row ->
                val fast = row.moveset.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val charged = row.moveset.drop(1).filter { it.isNotBlank() }.take(2)
                if (charged.isEmpty()) return@mapNotNull null
                RecommendedMoveset(
                    speciesId = row.speciesId,
                    league = league,
                    fastMoveId = fast,
                    chargedMoveIds = charged,
                    source = "PvPoke Overall @ $revision",
                    metaScore = row.score,
                    sourceUpdatedAtEpochMs = rankings.fetchedAtEpochMs
                )
            }
        require(decoded.isNotEmpty()) { "No usable PvPoke ranking records" }
        val formatRows = decoder.decodeFormats(formats.body)
            .filter { it.showCup }
            .distinctBy { Triple(it.cup, it.cp, it.title) }
        return PvpokeProviderSnapshot(
            revision = revision,
            fetchedAtEpochMs = maxOf(formats.fetchedAtEpochMs, rankings.fetchedAtEpochMs),
            league = league,
            cup = cup,
            rankingsPath = rankingsPath,
            formatsPath = PvpokePublicPaths.formatsPath,
            rankingsSha256 = sha256(rankings.body),
            formatsSha256 = sha256(formats.body),
            recommendations = decoded,
            visibleFormats = formatRows
        )
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
