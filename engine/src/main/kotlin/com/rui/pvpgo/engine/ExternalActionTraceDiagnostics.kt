package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.BattleOutcome
import kotlin.math.abs

enum class ExternalParityGapSeverity { NEAR, MATERIAL, LARGE }

data class ExternalActionTraceDiagnostic(
    val id: String,
    val opponentName: String,
    val expectedBattleRating: Int,
    val localBattleRating: Int,
    val localOutcome: BattleOutcome,
    val localTurns: Int,
    val localFinalSubjectHp: Int,
    val localFinalOpponentHp: Int,
    val localFinalOpponentMaxHp: Int,
    val localChargedSelections: List<String>,
    val localShieldDecisions: List<String>,
    val firstExternalDivergenceTurn: Int?,
    val externalActionTraceAvailable: Boolean,
    val severity: ExternalParityGapSeverity
) {
    val delta: Int get() = localBattleRating - expectedBattleRating
}

data class ExternalActionTraceReport(
    val upstream: String,
    val snapshotVersion: String,
    val sourceUrl: String,
    val diagnostics: List<ExternalActionTraceDiagnostic>,
    val externalTurnTraceAvailable: Boolean,
    val conclusion: String,
    val nextEvidenceNeeded: String
)

/**
 * Historical-result diagnostic layer retained in V1.9.
 *
 * The indexed PvPoke page is older than our pinned source reference. Its ratings are useful as a
 * regression clue, but version skew means they cannot prove or disprove parity with the current
 * pin. We expose the local trace and keep firstExternalDivergenceTurn null rather than pretending
 * that an old final score gives us a current per-turn timeline.
 */
object ExternalActionTraceDiagnostics {
    const val SNAPSHOT_VERSION = PvPokeReferencePin.HISTORICAL_RESULT_VERSION
    const val SOURCE_URL = PvPokeReferencePin.HISTORICAL_RESULT_URL

    fun run(engine: BattleEngine = BattleEngine()): ExternalActionTraceReport {
        val azumarill = PokemonSpecies(dex = 184, name = "Azumarill", speciesId = "azumarill", types = listOf("water", "fairy"), baseAttack = 112, baseDefense = 152, baseStamina = 225)
        val feraligatr = PokemonSpecies(dex = 160, name = "Feraligatr", speciesId = "feraligatr", types = listOf("water"), baseAttack = 205, baseDefense = 188, baseStamina = 198)
        val walrein = PokemonSpecies(dex = 365, name = "Walrein", speciesId = "walrein", types = listOf("ice", "water"), baseAttack = 182, baseDefense = 176, baseStamina = 242)

        val bubble = PvpMove("BUBBLE", "Bubble", type = "water", power = 8, energyGain = 11, turns = 3)
        val shadowClaw = PvpMove("SHADOW_CLAW", "Shadow Claw", type = "ghost", power = 6, energyGain = 8, turns = 2)
        val powderSnow = PvpMove("POWDER_SNOW", "Powder Snow", type = "ice", power = 5, energyGain = 8, turns = 2)
        val iceBeam = PvpMove("ICE_BEAM", "Ice Beam", type = "ice", power = 90, energyCost = 55)
        val hydroPump = PvpMove("HYDRO_PUMP", "Hydro Pump", type = "water", power = 130, energyCost = 75)
        val hydroCannon = PvpMove("HYDRO_CANNON", "Hydro Cannon", type = "water", power = 80, energyCost = 40)
        val icicleSpear = PvpMove("ICICLE_SPEAR", "Icicle Spear", type = "ice", power = 65, energyCost = 40)
        val earthquake = PvpMove("EARTHQUAKE", "Earthquake", type = "ground", power = 110, energyCost = 65)

        val subject = BattleBuild("external-azu", azumarill, IvSpread(4, 15, 13), 43.0, bubble, listOf(iceBeam, hydroPump))
        fun default64(species: PokemonSpecies): PvPRankEntry = PvPRanker.rank(species, League.GREAT, RankSettings(ivFloor = 5))[63]
        val fera64 = default64(feraligatr)
        val wal64 = default64(walrein)
        val fera = BattleBuild("external-fera", feraligatr, fera64.iv, fera64.level, shadowClaw, listOf(hydroCannon, iceBeam))
        val shadowFera = fera.copy(key = "external-fera-shadow", isShadow = true)
        val wal = BattleBuild("external-wal", walrein, wal64.iv, wal64.level, powderSnow, listOf(icicleSpear, earthquake))

        val scenario = BattleScenario(
            shieldsA = 1,
            shieldsB = 1,
            policyA = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
            policyB = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
            statEffectPolicy = StatEffectPolicy.PVPOKE_DETERMINISTIC_METER
        )

        fun diagnostic(id: String, name: String, expected: Int, opponent: BattleBuild): ExternalActionTraceDiagnostic {
            val result = engine.simulate(subject, opponent, scenario)
            val charged = result.decisionTrace
                .filter { it.type == BattleDecisionType.CHARGED_SELECTED }
                .map { "T${it.turn}:${it.actorKey}:${it.moveId}:${it.reason}" }
            val shields = result.decisionTrace
                .filter { it.type == BattleDecisionType.SHIELD_DECISION }
                .map { "T${it.turn}:${it.actorKey}:${it.moveId}:${it.reason}" }
            val severity = when (abs(result.battleRatingA - expected)) {
                in 0..10 -> ExternalParityGapSeverity.NEAR
                in 11..75 -> ExternalParityGapSeverity.MATERIAL
                else -> ExternalParityGapSeverity.LARGE
            }
            return ExternalActionTraceDiagnostic(
                id = id,
                opponentName = name,
                expectedBattleRating = expected,
                localBattleRating = result.battleRatingA,
                localOutcome = result.outcomeForA,
                localTurns = result.turns,
                localFinalSubjectHp = result.finalA.hp,
                localFinalOpponentHp = result.finalB.hp,
                localFinalOpponentMaxHp = result.finalB.maxHp,
                localChargedSelections = charged,
                localShieldDecisions = shields,
                firstExternalDivergenceTurn = null,
                externalActionTraceAvailable = false,
                severity = severity
            )
        }

        val rows = listOf(
            diagnostic("EXTERNAL-AZU-FERALIGATR", "Feraligatr", 348, fera),
            diagnostic("EXTERNAL-AZU-FERALIGATR-SHADOW", "Feraligatr (Shadow)", 368, shadowFera),
            diagnostic("EXTERNAL-AZU-WALREIN", "Walrein", 366, wal)
        )

        return ExternalActionTraceReport(
            upstream = "Historical PvPoke ${PvPokeReferencePin.HISTORICAL_RESULT_VERSION} result snapshot + pinned ${PvPokeReferencePin.SITE_VERSION}@${PvPokeReferencePin.SHORT_SHA} source rules",
            snapshotVersion = SNAPSHOT_VERSION,
            sourceUrl = SOURCE_URL,
            diagnostics = rows,
            externalTurnTraceAvailable = false,
            conclusion = "V1.9 adds the fixture-covered standard-form post-plan layer on top of the pre-DP and DP planner branches. Against the stale historical snapshot, Feraligatr and Walrein are near while Shadow Feraligatr remains a large diagnostic gap. These deltas are non-gating because the result page is ${PvPokeReferencePin.HISTORICAL_RESULT_VERSION} while the source pin is ${PvPokeReferencePin.SITE_VERSION}.",
            nextEvidenceNeeded = "Generate the same three battles from PvPoke ${PvPokeReferencePin.SITE_VERSION} at commit ${PvPokeReferencePin.SHORT_SHA} (or an equivalently pinned runner/export). Then compare final results and, if needed, per-turn action traces event-by-event."
        )
    }
}
