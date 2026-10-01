import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.League

private var assertions = 0
private fun checkThat(value: Boolean, message: String) {
    assertions++
    check(value) { message }
}

private fun battle(
    id: String,
    league: League,
    outcome: BattleOutcome,
    t: Long,
    team: String,
    lead: String,
    oppLead: String,
    rating: Int = 2200,
    tags: Set<String> = setOf("cup:open")
): BattleRecord = BattleRecord(
    id = id,
    league = league,
    outcome = outcome,
    playedAtEpochMs = t,
    ownTeamId = team,
    ownPokemon = listOf(
        OwnBattlePokemon(lead, BattleSlot.LEAD),
        OwnBattlePokemon("${team}-switch", BattleSlot.SWITCH),
        OwnBattlePokemon("${team}-closer", BattleSlot.CLOSER)
    ),
    opponentPokemon = listOf(
        OpponentBattlePokemon(oppLead, BattleSlot.LEAD),
        OpponentBattlePokemon("opp-switch", BattleSlot.SWITCH),
        OpponentBattlePokemon("opp-closer", BattleSlot.CLOSER)
    ),
    ratingBefore = rating,
    ratingAfter = when (outcome) {
        BattleOutcome.WIN -> rating + 14
        BattleOutcome.LOSS -> rating - 14
        BattleOutcome.DRAW -> rating
    },
    tags = tags
)

fun main() {
    val hour = 60L * 60L * 1000L
    val records = mutableListOf<BattleRecord>()

    // Great League, first period: deliberately mixed results/team/opponents.
    repeat(24) { i ->
        records += battle(
            id = "g-before-$i",
            league = League.GREAT,
            outcome = if (i < 10) BattleOutcome.WIN else BattleOutcome.LOSS,
            t = i * 20L * 60L * 1000L,
            team = if (i % 2 == 0) "team-a" else "team-b",
            lead = if (i % 2 == 0) "azu-owned" else "lanti-owned",
            oppLead = if (i < 8) "clodsire" else if (i < 16) "feraligatr" else "mandibuzz",
            tags = if (i < 20) setOf("cup:open") else setOf("cup:remix")
        )
    }

    // Gap to force a new session and second period with a strong improvement signal.
    val afterStart = 100L * hour
    repeat(30) { i ->
        records += battle(
            id = "g-after-$i",
            league = League.GREAT,
            outcome = if (i < 26) BattleOutcome.WIN else BattleOutcome.LOSS,
            t = afterStart + i * 15L * 60L * 1000L,
            team = "team-c",
            lead = "dunsparce-owned",
            oppLead = if (i < 12) "clodsire" else if (i < 22) "feraligatr" else "mandibuzz",
            rating = 2300,
            tags = setOf("cup:open")
        )
    }

    // Ultra records must never leak into the Great snapshot.
    repeat(7) { i ->
        records += battle("u-$i", League.ULTRA, BattleOutcome.WIN, afterStart + i, "ultra-team", "gira", "registeel")
    }

    val great = PersonalMetaQuery(league = League.GREAT)
    val snapshot = PersonalMetaAdvisor.analyze(records, great)
    checkThat(snapshot.summary.total == 54, "Great snapshot must exclude Ultra records")
    checkThat(snapshot.summary.wins == 36, "Great wins expected 36")
    checkThat(snapshot.summary.losses == 18, "Great losses expected 18")
    checkThat(snapshot.summary.winRate.confidence == SampleConfidence.HIGH, "54 battles should be HIGH confidence tier")
    checkThat(snapshot.summary.winRate.lower < snapshot.summary.winRate.proportion, "Wilson lower must be below observed rate")
    checkThat(snapshot.summary.winRate.upper > snapshot.summary.winRate.proportion, "Wilson upper must be above observed rate")
    checkThat(snapshot.sessions.size >= 2, "Large gap must split sessions")
    checkThat(snapshot.teams.first().teamKey == "team-c", "team-c should have most battles")
    checkThat(snapshot.teams.first().battles == 30, "team-c battle count")
    checkThat(snapshot.slots.any { it.ownedPokemonId == "dunsparce-owned" && it.slot == BattleSlot.LEAD && it.battles == 30 }, "slot analytics")
    checkThat(snapshot.opponentLeads.any { it.speciesId == "clodsire" && it.encounters == 20 }, "lead segmentation")
    checkThat(snapshot.signals.any { it.type == PersonalMetaSignalType.HIGH_EXPOSURE && it.subjectId == "clodsire" }, "high exposure signal")

    val remix = PersonalMetaQuery(league = League.GREAT, requiredTags = setOf("cup:remix"))
    val remixSummary = PersonalMetaAnalytics.summarize(records, remix)
    checkThat(remixSummary.total == 4, "tag/cup segmentation must isolate remix records")
    checkThat(remixSummary.winRate.confidence == SampleConfidence.VERY_LOW, "small cup sample must remain very low confidence")

    val ratingBand = PersonalMetaQuery(league = League.GREAT, minRatingInclusive = 2250, maxRatingExclusive = 2400)
    checkThat(PersonalMetaAnalytics.summarize(records, ratingBand).total == 30, "rating segmentation")

    val before = PersonalMetaQuery(league = League.GREAT, toEpochMsExclusive = afterStart)
    val after = PersonalMetaQuery(league = League.GREAT, fromEpochMsInclusive = afterStart)
    val delta = PersonalMetaAnalytics.recommendationDelta(records, before, after)
    checkThat(delta.before.total == 24 && delta.after.total == 30, "delta window counts")
    checkThat(delta.winRateDeltaPoints > 40.0, "large observed delta expected")
    checkThat(delta.direction == DeltaDirection.IMPROVED, "separated confidence intervals should mark IMPROVED")
    checkThat(!delta.causalClaimAllowed, "recommendation delta must never claim causality")
    checkThat(delta.explanation.contains("não prova causal"), "causality caveat required")

    val tinyBefore = PersonalMetaQuery(league = League.GREAT, fromEpochMsInclusive = 0, toEpochMsExclusive = 2 * hour)
    val tinyAfter = PersonalMetaQuery(league = League.GREAT, fromEpochMsInclusive = 2 * hour, toEpochMsExclusive = 4 * hour)
    val tinyDelta = PersonalMetaAnalytics.recommendationDelta(records, tinyBefore, tinyAfter)
    checkThat(tinyDelta.direction == DeltaDirection.INCONCLUSIVE, "tiny samples must be inconclusive")

    val openOnly = PersonalMetaQuery(league = League.GREAT, requiredTags = setOf("cup:open"))
    val openSnapshot = PersonalMetaAdvisor.analyze(records, openOnly)
    checkThat(openSnapshot.summary.total == 50, "open cup total")
    checkThat(openSnapshot.signals.none { it.message.contains("probabilidade", ignoreCase = true) }, "signals must not claim win probability")
    checkThat(openSnapshot.summary.query.league == League.GREAT, "snapshot provenance retains league")

    val noData = PersonalMetaAdvisor.analyze(emptyList(), PersonalMetaQuery(League.MASTER))
    checkThat(noData.summary.total == 0, "empty snapshot")
    checkThat(noData.summary.winRate.lower == 0.0 && noData.summary.winRate.upper == 1.0, "empty Wilson interval must express total uncertainty")
    checkThat(noData.signals.any { it.type == PersonalMetaSignalType.SAMPLE_TOO_SMALL }, "empty sample warning")

    val wilson10 = PersonalMetaAnalytics.wilson(7, 10)
    checkThat(wilson10.proportion == 0.7, "Wilson observed proportion")
    checkThat(wilson10.lower > 0.0 && wilson10.upper < 1.0, "Wilson finite bounds")
    checkThat(PersonalMetaAnalytics.confidenceFor(9) == SampleConfidence.VERY_LOW, "tier 9")
    checkThat(PersonalMetaAnalytics.confidenceFor(10) == SampleConfidence.LOW, "tier 10")
    checkThat(PersonalMetaAnalytics.confidenceFor(25) == SampleConfidence.MODERATE, "tier 25")
    checkThat(PersonalMetaAnalytics.confidenceFor(50) == SampleConfidence.HIGH, "tier 50")

    println("PERSONAL_META_F54_F100_F106_V1_29_OK assertions=$assertions records=${records.size} great=${snapshot.summary.total} sessions=${snapshot.sessions.size} teams=${snapshot.teams.size}")
}
