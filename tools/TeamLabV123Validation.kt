import com.rui.pvpgo.CollectionTeamAdvisor
import com.rui.pvpgo.CollectionTeamLab
import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.CoverageState
import com.rui.pvpgo.domain.MatchupBand
import com.rui.pvpgo.domain.MatchupEvaluation
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.MetaBuildEntry
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PlaystyleProfile
import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.ShadowPolicy
import com.rui.pvpgo.domain.SuggestedTeamRole
import com.rui.pvpgo.domain.TeamAdvisorConstraints
import com.rui.pvpgo.domain.TeamAlternativeKind
import com.rui.pvpgo.domain.TeamCandidateExclusionReason
import com.rui.pvpgo.domain.TeamLabStatus
import com.rui.pvpgo.domain.TeamStyleBias
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies

private fun assertLab(condition: Boolean, message: String) { if (!condition) error(message) }

private fun sp(
    id: String,
    dex: Int,
    attack: Int = 100,
    defense: Int = 100,
    stamina: Int = 100,
    tags: Set<String> = emptySet()
) = PokemonSpecies(
    dex = dex, name = id, speciesId = id, types = listOf("normal"), tags = tags,
    fastMoveIds = listOf("FAST"), chargedMoveIds = listOf("CHARGED"),
    baseAttack = attack, baseDefense = defense, baseStamina = stamina
)

private fun mon(
    id: String,
    speciesId: String,
    level: Double = 20.0,
    shadow: Boolean = false,
    tags: Set<String> = emptySet()
) = OwnedPokemon(
    id = id, speciesId = speciesId, iv = IvSpread(0,0,0), level = level,
    fastMoveId = "FAST", chargedMoveIds = listOf("CHARGED"), isShadow = shadow,
    tags = tags, createdAtEpochMs = 1L
)

private fun opponent(id: String) = OpponentBuild(
    speciesId = id, fastMoveId = "FAST", chargedMoveIds = listOf("CHARGED"),
    iv = IvSpread(0,0,0), level = 20.0, isShadow = false
)

private class LabMatrixEvaluator(private val matrix: Map<Pair<String,String>,Int>) : MoveAwareMatchupEvaluator {
    override fun evaluate(pokemon: OwnedPokemon, opponent: OpponentBuild, scenario: MatchupScenario): MatchupEvaluation? {
        val rating = matrix[pokemon.id to opponent.speciesId] ?: return null
        val outcome = when { rating > 500 -> BattleOutcome.WIN; rating < 500 -> BattleOutcome.LOSS; else -> BattleOutcome.DRAW }
        val band = when {
            rating >= 650 -> MatchupBand.VERY_FAVORABLE
            rating >= 550 -> MatchupBand.FAVORABLE
            rating >= 450 -> MatchupBand.CLOSE
            rating >= 350 -> MatchupBand.UNFAVORABLE
            else -> MatchupBand.VERY_UNFAVORABLE
        }
        return MatchupEvaluation(pokemon.id, opponent.speciesId, scenario, rating, 1000-rating, outcome, band, 20)
    }
}

fun main() {
    val catalog = listOf(
        sp("a", 1, attack = 145, defense = 80, stamina = 85),
        sp("b", 2, attack = 100, defense = 100, stamina = 100),
        sp("c", 3, attack = 90, defense = 120, stamina = 120),
        sp("d", 4, attack = 115, defense = 95, stamina = 95),
        sp("e", 5, attack = 75, defense = 145, stamina = 145),
        sp("shadowx", 6),
        sp("mimikyu", 778)
    )
    val collection = listOf(
        mon("A", "a", tags = setOf("cheap")),
        mon("B", "b", tags = setOf("cheap")),
        mon("C", "c", tags = setOf("cheap")),
        mon("D", "d"),
        mon("E", "e"),
        mon("SH", "shadowx", shadow = true),
        mon("SPECIAL", "mimikyu")
    )
    val matrix = mapOf(
        ("A" to "alpha") to 800, ("A" to "beta") to 300, ("A" to "gamma") to 600, ("A" to "delta") to 400,
        ("B" to "alpha") to 400, ("B" to "beta") to 800, ("B" to "gamma") to 550, ("B" to "delta") to 450,
        ("C" to "alpha") to 550, ("C" to "beta") to 450, ("C" to "gamma") to 800, ("C" to "delta") to 300,
        ("D" to "alpha") to 650, ("D" to "beta") to 650, ("D" to "gamma") to 200, ("D" to "delta") to 700,
        ("E" to "alpha") to 300, ("E" to "beta") to 300, ("E" to "gamma") to 300, ("E" to "delta") to 550,
        ("SH" to "alpha") to 500, ("SH" to "beta") to 500, ("SH" to "gamma") to 500, ("SH" to "delta") to 500
    )
    val evaluator = LabMatrixEvaluator(matrix)
    val meta = VersionedMetaGroup(
        id = "meta-lab-v123", league = League.GREAT, seasonId = "s1", sourceName = "fixture", sourceVersion = "1", generatedAtEpochMs = 1L,
        entries = listOf(
            MetaBuildEntry("alpha", opponent("alpha"), 3.0),
            MetaBuildEntry("beta", opponent("beta"), 3.0),
            MetaBuildEntry("gamma", opponent("gamma"), 2.0),
            MetaBuildEntry("delta", opponent("delta"), 2.0)
        ),
        rulesetId = "great", rulesetVersion = "1", dataPackHashSha256 = "a".repeat(64)
    )
    val ruleset = PvPRuleset(
        id = "great", version = "1", title = "Great", baseLeague = League.GREAT,
        sourceName = "fixture", sourceVersion = "1", generatedAtEpochMs = 1L
    )
    val scenario = MatchupScenario(League.GREAT)

    // F93 + F96: a soft preference may choose a slightly lower-coverage team, without changing factual coverage.
    val lab = CollectionTeamLab(catalog, evaluator).analyze(
        collection, emptyList(), meta, ruleset, scenario,
        constraints = TeamAdvisorConstraints(maxCandidatePool = 6),
        playstyle = PlaystyleProfile(
            styleBias = TeamStyleBias.BALANCED,
            preferredOwnedPokemonIds = setOf("C"),
            safeSwitchComfortOwnedPokemonIds = setOf("C")
        )
    )
    assertLab(lab.status == TeamLabStatus.READY, "F93 Team Lab should be READY")
    assertLab(lab.certificationRoute == "INDEPENDENT_FALLBACK", "Team Lab must preserve certification route")
    val primary = lab.primary ?: error("Missing primary")
    assertLab("C" in primary.memberOwnedPokemonIds, "F96 preference should be able to affect soft ordering")
    assertLab(lab.primaryLabScore!! > primary.coverage.coverageScorePercent, "Lab score should include transparent soft preference adjustment")

    // F94: lock remains a hard constraint, not a soft preference.
    val locked = CollectionTeamLab(catalog, evaluator).analyze(
        collection, emptyList(), meta, ruleset, scenario,
        constraints = TeamAdvisorConstraints(lockedOwnedPokemonIds = setOf("E"), maxCandidatePool = 6)
    )
    assertLab(locked.status == TeamLabStatus.READY, "Locked lab should still be READY")
    assertLab("E" in locked.primary!!.memberOwnedPokemonIds, "F94 lock must be present in primary")
    assertLab(locked.alternatives.all { "E" in it.memberOwnedPokemonIds }, "F94 lock must survive alternatives generated from same search")

    // F95: species/tag/shadow constraints are hard filters with explicit reasons.
    val constrainedAdvisor = CollectionTeamAdvisor(catalog, evaluator).recommend(
        collection, emptyList(), meta, ruleset, scenario,
        constraints = TeamAdvisorConstraints(
            allowedSpeciesIds = setOf("a", "b", "c", "d", "e", "shadowx"),
            requiredAnyTags = setOf("cheap"),
            shadowPolicy = ShadowPolicy.NORMAL_ONLY,
            maxCandidatePool = 6
        ),
        limit = 5
    )
    assertLab(constrainedAdvisor.recommendations.first().memberOwnedPokemonIds.toSet() == setOf("A","B","C"), "Tag allow-list should leave A/B/C as only trio")
    assertLab(constrainedAdvisor.exclusions.any { it.ownedPokemonId == "D" && it.reason == TeamCandidateExclusionReason.TAG_CONSTRAINT }, "Tag exclusion reason missing")
    assertLab(constrainedAdvisor.exclusions.any { it.ownedPokemonId == "SH" && it.reason == TeamCandidateExclusionReason.TAG_CONSTRAINT }, "Tag-constrained Shadow candidate should be explicit")
    assertLab(constrainedAdvisor.exclusions.any { it.ownedPokemonId == "SPECIAL" && it.reason == TeamCandidateExclusionReason.SPECIES_CONSTRAINT }, "Species constraint reason missing")

    val normalOnly = CollectionTeamAdvisor(catalog, evaluator).recommend(
        collection, emptyList(), meta, ruleset, scenario,
        constraints = TeamAdvisorConstraints(shadowPolicy = ShadowPolicy.NORMAL_ONLY, maxCandidatePool = 6),
        limit = 3
    )
    assertLab(normalOnly.exclusions.any { it.ownedPokemonId == "SH" && it.reason == TeamCandidateExclusionReason.SHADOW_POLICY }, "Shadow policy exclusion missing")

    // F97: matrix must expose all 3 member ratings and the factual best response for every meta row.
    val coverageMatrix = lab.coverageMatrix ?: error("Missing matrix")
    assertLab(coverageMatrix.rows.size == 4, "F97 matrix row count mismatch")
    assertLab(coverageMatrix.rows.all { it.memberRatings.keys == primary.memberOwnedPokemonIds.toSet() }, "F97 matrix must include all three members")
    val gamma = coverageMatrix.rows.single { it.metaEntryKey == "gamma" }
    assertLab(gamma.bestOwnedPokemonId == "C" && gamma.bestBattleRating == 800 && gamma.state == CoverageState.STRONG, "F97 best-response row mismatch")

    // F98: alternatives are distinct from primary and explain the optimization dimension.
    assertLab(lab.alternatives.isNotEmpty(), "F98 should expose alternatives")
    assertLab(lab.alternatives.all { it.memberOwnedPokemonIds.toSet() != primary.memberOwnedPokemonIds.toSet() }, "Alternatives must be distinct from primary")
    assertLab(lab.alternatives.map { it.memberOwnedPokemonIds.toSet() }.distinct().size == lab.alternatives.size, "Alternatives must be deduplicated")
    assertLab(lab.alternatives.any { it.kind == TeamAlternativeKind.BEST_COVERAGE }, "Expected a pure best-coverage alternative when preference changes primary")

    // F99 + role transparency.
    val roleSet = lab.roleSuggestions.map { it.role }.toSet()
    assertLab(roleSet == SuggestedTeamRole.entries.toSet(), "Expected Lead/Safe Switch/Closer suggestions")
    assertLab(lab.roleSuggestions.all { it.basis.contains("Heurística") }, "Role suggestions must state heuristic basis")
    val why = lab.why ?: error("Missing WhyThisTeam")
    assertLab(why.summary.contains("coverage", ignoreCase = true), "F99 summary should name coverage")
    assertLab(why.caveats.any { it.contains("não representam probabilidade") }, "F99 must reject probability interpretation")
    assertLab(why.caveats.any { it.contains("3v3") }, "F99 must disclose 1v1-vs-3v3 limitation")
    assertLab(why.selectionReasons.isNotEmpty(), "F99 selection reasons missing")
    assertLab(why.selectionReasons.any { it.contains("Papéis sugeridos") }, "F99 should explain heuristic role assignment")

    println("TEAM_LAB_F93_F99_V1_29_OK")
    println("primary=${primary.memberOwnedPokemonIds.joinToString("+")} coverage=${primary.coverage.coverageScorePercent} labScore=${"%.1f".format(lab.primaryLabScore)}")
    println("roles=${lab.roleSuggestions.joinToString { "${it.role}:${it.ownedPokemonId}" }} alternatives=${lab.alternatives.map { it.kind }}")
    println("matrixRows=${coverageMatrix.rows.size} exclusions=${lab.exclusions.size}")
}
