package com.rui.pvpgo.domain

import com.rui.pvpgo.engine.League
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

enum class SampleConfidence { VERY_LOW, LOW, MODERATE, HIGH }
enum class DeltaDirection { IMPROVED, DECLINED, INCONCLUSIVE }
enum class PersonalMetaSignalType { HIGH_EXPOSURE, LOSS_PRESSURE, TEAM_STRENGTH, SAMPLE_TOO_SMALL }

data class PersonalMetaQuery(
    val league: League,
    val fromEpochMsInclusive: Long? = null,
    val toEpochMsExclusive: Long? = null,
    val ownTeamId: String? = null,
    val minRatingInclusive: Int? = null,
    val maxRatingExclusive: Int? = null,
    val requiredTags: Set<String> = emptySet()
) {
    init {
        require(fromEpochMsInclusive == null || toEpochMsExclusive == null || fromEpochMsInclusive < toEpochMsExclusive)
        require(minRatingInclusive == null || minRatingInclusive >= 0)
        require(maxRatingExclusive == null || maxRatingExclusive > 0)
        require(minRatingInclusive == null || maxRatingExclusive == null || minRatingInclusive < maxRatingExclusive)
        require(requiredTags.none { it.isBlank() })
    }
}

data class WilsonInterval(
    val proportion: Double,
    val lower: Double,
    val upper: Double,
    val sampleSize: Int,
    val confidence: SampleConfidence
) {
    init {
        require(proportion in 0.0..1.0)
        require(lower in 0.0..1.0)
        require(upper in 0.0..1.0)
        require(lower <= upper)
        require(sampleSize >= 0)
    }
}

data class PersonalMetaSummary(
    val query: PersonalMetaQuery,
    val total: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: WilsonInterval,
    val averageRatingBefore: Double?,
    val averageRatingAfter: Double?
)

data class BattleSessionStats(
    val sessionIndex: Int,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val battles: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val teamKeys: Set<String>
)

data class TeamPerformanceStats(
    val teamKey: String,
    val battles: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: WilsonInterval
)

data class SlotPerformanceStats(
    val ownedPokemonId: String,
    val slot: BattleSlot,
    val battles: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: WilsonInterval
)

data class OpponentLeadStats(
    val speciesId: String,
    val encounters: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: WilsonInterval
)

data class PersonalMetaSignal(
    val type: PersonalMetaSignalType,
    val subjectId: String,
    val evidenceCount: Int,
    val message: String
)

data class PersonalMetaSnapshot(
    val summary: PersonalMetaSummary,
    val sessions: List<BattleSessionStats>,
    val teams: List<TeamPerformanceStats>,
    val slots: List<SlotPerformanceStats>,
    val opponentLeads: List<OpponentLeadStats>,
    val signals: List<PersonalMetaSignal>
)

data class RecommendationDelta(
    val before: PersonalMetaSummary,
    val after: PersonalMetaSummary,
    val winRateDeltaPoints: Double,
    val direction: DeltaDirection,
    val causalClaimAllowed: Boolean = false,
    val explanation: String
)

object PersonalMetaAnalytics {
    /**
     * Filters BattleRecord with an explicit league boundary first. This deliberately makes it
     * impossible for Great/Ultra/Master records to be mixed in one Personal Meta snapshot.
     */
    fun filter(records: List<BattleRecord>, query: PersonalMetaQuery): List<BattleRecord> = records
        .asSequence()
        .filter { it.league == query.league }
        .filter { query.fromEpochMsInclusive == null || it.playedAtEpochMs >= query.fromEpochMsInclusive }
        .filter { query.toEpochMsExclusive == null || it.playedAtEpochMs < query.toEpochMsExclusive }
        .filter { query.ownTeamId == null || teamKey(it) == query.ownTeamId }
        .filter { battle ->
            query.minRatingInclusive == null || (battle.ratingBefore != null && battle.ratingBefore >= query.minRatingInclusive)
        }
        .filter { battle ->
            query.maxRatingExclusive == null || (battle.ratingBefore != null && battle.ratingBefore < query.maxRatingExclusive)
        }
        .filter { battle -> query.requiredTags.all { it in battle.tags } }
        .sortedBy { it.playedAtEpochMs }
        .toList()

    fun summarize(records: List<BattleRecord>, query: PersonalMetaQuery): PersonalMetaSummary {
        val filtered = filter(records, query)
        val wins = filtered.count { it.outcome == BattleOutcome.WIN }
        val losses = filtered.count { it.outcome == BattleOutcome.LOSS }
        val draws = filtered.count { it.outcome == BattleOutcome.DRAW }
        return PersonalMetaSummary(
            query = query,
            total = filtered.size,
            wins = wins,
            losses = losses,
            draws = draws,
            winRate = wilson(successes = wins, total = filtered.size),
            averageRatingBefore = filtered.mapNotNull { it.ratingBefore }.takeIf { it.isNotEmpty() }?.average(),
            averageRatingAfter = filtered.mapNotNull { it.ratingAfter }.takeIf { it.isNotEmpty() }?.average()
        )
    }

    fun sessions(
        records: List<BattleRecord>,
        query: PersonalMetaQuery,
        maxGapMs: Long = 90L * 60L * 1000L
    ): List<BattleSessionStats> {
        require(maxGapMs > 0)
        val filtered = filter(records, query)
        if (filtered.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<BattleRecord>>()
        var current = mutableListOf(filtered.first())
        groups += current
        for (battle in filtered.drop(1)) {
            val previous = current.last()
            if (battle.playedAtEpochMs - previous.playedAtEpochMs > maxGapMs) {
                current = mutableListOf()
                groups += current
            }
            current += battle
        }

        return groups.mapIndexed { index, battles ->
            BattleSessionStats(
                sessionIndex = index + 1,
                startedAtEpochMs = battles.first().playedAtEpochMs,
                endedAtEpochMs = battles.last().playedAtEpochMs,
                battles = battles.size,
                wins = battles.count { it.outcome == BattleOutcome.WIN },
                losses = battles.count { it.outcome == BattleOutcome.LOSS },
                draws = battles.count { it.outcome == BattleOutcome.DRAW },
                teamKeys = battles.map(::teamKey).toSet()
            )
        }
    }

    fun teamPerformance(records: List<BattleRecord>, query: PersonalMetaQuery): List<TeamPerformanceStats> =
        filter(records, query)
            .groupBy(::teamKey)
            .map { (key, battles) ->
                val wins = battles.count { it.outcome == BattleOutcome.WIN }
                TeamPerformanceStats(
                    teamKey = key,
                    battles = battles.size,
                    wins = wins,
                    losses = battles.count { it.outcome == BattleOutcome.LOSS },
                    draws = battles.count { it.outcome == BattleOutcome.DRAW },
                    winRate = wilson(wins, battles.size)
                )
            }
            .sortedWith(compareByDescending<TeamPerformanceStats> { it.battles }.thenByDescending { it.winRate.proportion }.thenBy { it.teamKey })

    fun slotPerformance(records: List<BattleRecord>, query: PersonalMetaQuery): List<SlotPerformanceStats> {
        data class Key(val id: String, val slot: BattleSlot)
        return filter(records, query)
            .flatMap { battle -> battle.ownPokemon.map { Key(it.ownedPokemonId, it.slot) to battle.outcome } }
            .groupBy({ it.first }, { it.second })
            .map { (key, outcomes) ->
                val wins = outcomes.count { it == BattleOutcome.WIN }
                SlotPerformanceStats(
                    ownedPokemonId = key.id,
                    slot = key.slot,
                    battles = outcomes.size,
                    wins = wins,
                    losses = outcomes.count { it == BattleOutcome.LOSS },
                    draws = outcomes.count { it == BattleOutcome.DRAW },
                    winRate = wilson(wins, outcomes.size)
                )
            }
            .sortedWith(compareByDescending<SlotPerformanceStats> { it.battles }.thenBy { it.slot }.thenBy { it.ownedPokemonId })
    }

    fun opponentLeadPerformance(records: List<BattleRecord>, query: PersonalMetaQuery): List<OpponentLeadStats> =
        filter(records, query)
            .mapNotNull { battle -> battle.opponentPokemon.firstOrNull { it.slot == BattleSlot.LEAD }?.speciesId?.let { it to battle.outcome } }
            .groupBy({ it.first }, { it.second })
            .map { (speciesId, outcomes) ->
                val wins = outcomes.count { it == BattleOutcome.WIN }
                OpponentLeadStats(
                    speciesId = speciesId,
                    encounters = outcomes.size,
                    wins = wins,
                    losses = outcomes.count { it == BattleOutcome.LOSS },
                    draws = outcomes.count { it == BattleOutcome.DRAW },
                    winRate = wilson(wins, outcomes.size)
                )
            }
            .sortedWith(compareByDescending<OpponentLeadStats> { it.encounters }.thenBy { it.winRate.proportion }.thenBy { it.speciesId })

    fun snapshot(records: List<BattleRecord>, query: PersonalMetaQuery): PersonalMetaSnapshot {
        val summary = summarize(records, query)
        val teams = teamPerformance(records, query)
        val leads = opponentLeadPerformance(records, query)
        val filtered = filter(records, query)
        val signals = mutableListOf<PersonalMetaSignal>()

        if (summary.winRate.confidence == SampleConfidence.VERY_LOW) {
            signals += PersonalMetaSignal(
                type = PersonalMetaSignalType.SAMPLE_TOO_SMALL,
                subjectId = query.league.name,
                evidenceCount = summary.total,
                message = "Amostra pequena; trata tendências como hipótese, não conclusão."
            )
        }

        val exposureFloor = max(3, (filtered.size * 0.20).toInt())
        val opponentStats = linkedMapOf<String, MutableList<BattleOutcome>>()
        for (battle in filtered) {
            for (species in battle.opponentPokemon.map { it.speciesId }.distinct()) {
                opponentStats.getOrPut(species) { mutableListOf() } += battle.outcome
            }
        }
        for ((species, outcomes) in opponentStats) {
            if (outcomes.size >= exposureFloor) {
                signals += PersonalMetaSignal(
                    PersonalMetaSignalType.HIGH_EXPOSURE,
                    species,
                    outcomes.size,
                    "${species} apareceu em ${outcomes.size}/${filtered.size} batalhas deste segmento."
                )
            }
            val losses = outcomes.count { it == BattleOutcome.LOSS }
            val wins = outcomes.count { it == BattleOutcome.WIN }
            if (outcomes.size >= 5 && losses >= wins + 2) {
                signals += PersonalMetaSignal(
                    PersonalMetaSignalType.LOSS_PRESSURE,
                    species,
                    outcomes.size,
                    "Perdas contra equipas com ${species} superam vitórias neste segmento; investigar matchups antes de recomendar troca."
                )
            }
        }

        teams.firstOrNull { it.battles >= 10 && it.winRate.lower > 0.50 }?.let { team ->
            signals += PersonalMetaSignal(
                PersonalMetaSignalType.TEAM_STRENGTH,
                team.teamKey,
                team.battles,
                "Esta equipa tem evidência local positiva neste segmento; o intervalo ainda deve acompanhar a taxa observada."
            )
        }

        return PersonalMetaSnapshot(
            summary = summary,
            sessions = sessions(records, query),
            teams = teams,
            slots = slotPerformance(records, query),
            opponentLeads = leads,
            signals = signals.sortedWith(compareByDescending<PersonalMetaSignal> { it.evidenceCount }.thenBy { it.type }.thenBy { it.subjectId })
        )
    }

    /**
     * Compares two explicit segments. This reports association only. It never claims that a
     * recommendation caused a change in results.
     */
    fun recommendationDelta(
        records: List<BattleRecord>,
        beforeQuery: PersonalMetaQuery,
        afterQuery: PersonalMetaQuery
    ): RecommendationDelta {
        require(beforeQuery.league == afterQuery.league) { "Recommendation delta must compare the same league" }
        val before = summarize(records, beforeQuery)
        val after = summarize(records, afterQuery)
        val delta = (after.winRate.proportion - before.winRate.proportion) * 100.0
        val enough = before.total >= 20 && after.total >= 20
        val intervalsSeparate = after.winRate.lower > before.winRate.upper || before.winRate.lower > after.winRate.upper
        val direction = when {
            !enough || !intervalsSeparate -> DeltaDirection.INCONCLUSIVE
            delta > 0 -> DeltaDirection.IMPROVED
            delta < 0 -> DeltaDirection.DECLINED
            else -> DeltaDirection.INCONCLUSIVE
        }
        val explanation = when (direction) {
            DeltaDirection.IMPROVED -> "A taxa observada melhorou e os intervalos de 95% não se sobrepõem; isto é associação, não prova causal."
            DeltaDirection.DECLINED -> "A taxa observada piorou e os intervalos de 95% não se sobrepõem; isto é associação, não prova causal."
            DeltaDirection.INCONCLUSIVE -> "A amostra e/ou os intervalos de confiança não sustentam uma conclusão forte sobre a mudança."
        }
        return RecommendationDelta(before, after, delta, direction, false, explanation)
    }

    fun wilson(successes: Int, total: Int, z: Double = 1.959963984540054): WilsonInterval {
        require(total >= 0)
        require(successes in 0..total)
        require(z > 0)
        if (total == 0) return WilsonInterval(0.0, 0.0, 1.0, 0, SampleConfidence.VERY_LOW)
        val n = total.toDouble()
        val p = successes / n
        val z2 = z.pow(2)
        val denominator = 1.0 + z2 / n
        val center = (p + z2 / (2.0 * n)) / denominator
        val margin = z * sqrt((p * (1.0 - p) + z2 / (4.0 * n)) / n) / denominator
        return WilsonInterval(
            proportion = p,
            lower = (center - margin).coerceIn(0.0, 1.0),
            upper = (center + margin).coerceIn(0.0, 1.0),
            sampleSize = total,
            confidence = confidenceFor(total)
        )
    }

    fun confidenceFor(sampleSize: Int): SampleConfidence = when {
        sampleSize < 10 -> SampleConfidence.VERY_LOW
        sampleSize < 25 -> SampleConfidence.LOW
        sampleSize < 50 -> SampleConfidence.MODERATE
        else -> SampleConfidence.HIGH
    }

    fun teamKey(record: BattleRecord): String = record.ownTeamId ?: "members:" +
        record.ownPokemon.map { it.ownedPokemonId }.sorted().joinToString("+")
}

/**
 * F54 foundation. It consumes only local BattleRecord history and returns evidence-bearing
 * signals/snapshots. Turning those signals into a concrete replacement team remains delegated
 * to the trust-gated Team Lab rather than being inferred here.
 */
object PersonalMetaAdvisor {
    fun analyze(records: List<BattleRecord>, query: PersonalMetaQuery): PersonalMetaSnapshot =
        PersonalMetaAnalytics.snapshot(records, query)
}
