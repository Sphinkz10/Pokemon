package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.BattleOutcome
import kotlin.math.ceil

/**
 * F88 deliberately compares concrete builds. It does not claim that one IV rank is universally
 * better; it only reports integer Fast-Move damage / bulk changes in the supplied matchup.
 */
enum class BreakpointBulkpointSignal {
    BREAKPOINT_GAIN,
    BREAKPOINT_LOSS,
    BULKPOINT_GAIN,
    BULKPOINT_LOSS,
    HP_BULK_GAIN,
    HP_BULK_LOSS
}

data class FastDamageProfile(
    val buildKey: String,
    val attack: Double,
    val defense: Double,
    val hp: Int,
    val outgoingFastMoveId: String,
    val outgoingFastDamage: Int,
    val incomingFastMoveId: String,
    val incomingFastDamage: Int,
    val fastMovesToKoOpponent: Int,
    val opponentFastMovesToKoSubject: Int
) {
    init {
        require(hp > 0)
        require(outgoingFastDamage > 0 && incomingFastDamage > 0)
        require(fastMovesToKoOpponent > 0 && opponentFastMovesToKoSubject > 0)
    }
}

data class BreakpointBulkpointComparison(
    val candidateKey: String,
    val candidateIv: IvSpread,
    val candidateLevel: Double,
    val profile: FastDamageProfile,
    val attackDelta: Double,
    val defenseDelta: Double,
    val hpDelta: Int,
    val outgoingFastDamageDelta: Int,
    val incomingFastDamageDelta: Int,
    val fastMovesToKoOpponentDelta: Int,
    val opponentFastMovesToKoSubjectDelta: Int,
    val signals: Set<BreakpointBulkpointSignal>,
    val explanation: String
)

data class BreakpointBulkpointReport(
    val subjectSpeciesId: String,
    val opponentSpeciesId: String,
    val baselineKey: String,
    val baseline: FastDamageProfile,
    val comparisons: List<BreakpointBulkpointComparison>,
    val notes: List<String>
)

object BreakpointBulkpointAnalyzer {
    fun analyze(
        baseline: BattleBuild,
        candidates: List<BattleBuild>,
        opponent: BattleBuild
    ): BreakpointBulkpointReport {
        require(candidates.isNotEmpty())
        candidates.forEach { candidate ->
            require(candidate.species.speciesId == baseline.species.speciesId) {
                "F88 candidates must be the same species/form as the baseline"
            }
            require(candidate.fastMove.moveId == baseline.fastMove.moveId) {
                "F88 compares IV/level bulk and breakpoints with the same Fast Move"
            }
            require(candidate.isShadow == baseline.isShadow) {
                "Shadow/Normal changes belong in F89 Scenario Explorer, not the IV breakpoint comparison"
            }
        }

        val base = profile(baseline, opponent)
        val comparisons = candidates.map { candidate ->
            val p = profile(candidate, opponent)
            val signals = buildSet {
                when {
                    p.outgoingFastDamage > base.outgoingFastDamage -> add(BreakpointBulkpointSignal.BREAKPOINT_GAIN)
                    p.outgoingFastDamage < base.outgoingFastDamage -> add(BreakpointBulkpointSignal.BREAKPOINT_LOSS)
                }
                when {
                    p.incomingFastDamage < base.incomingFastDamage -> add(BreakpointBulkpointSignal.BULKPOINT_GAIN)
                    p.incomingFastDamage > base.incomingFastDamage -> add(BreakpointBulkpointSignal.BULKPOINT_LOSS)
                    p.opponentFastMovesToKoSubject > base.opponentFastMovesToKoSubject -> add(BreakpointBulkpointSignal.HP_BULK_GAIN)
                    p.opponentFastMovesToKoSubject < base.opponentFastMovesToKoSubject -> add(BreakpointBulkpointSignal.HP_BULK_LOSS)
                }
            }
            BreakpointBulkpointComparison(
                candidateKey = candidate.key,
                candidateIv = candidate.iv,
                candidateLevel = candidate.level,
                profile = p,
                attackDelta = p.attack - base.attack,
                defenseDelta = p.defense - base.defense,
                hpDelta = p.hp - base.hp,
                outgoingFastDamageDelta = p.outgoingFastDamage - base.outgoingFastDamage,
                incomingFastDamageDelta = p.incomingFastDamage - base.incomingFastDamage,
                fastMovesToKoOpponentDelta = p.fastMovesToKoOpponent - base.fastMovesToKoOpponent,
                opponentFastMovesToKoSubjectDelta = p.opponentFastMovesToKoSubject - base.opponentFastMovesToKoSubject,
                signals = signals,
                explanation = explain(base, p, signals)
            )
        }

        return BreakpointBulkpointReport(
            subjectSpeciesId = baseline.species.speciesId,
            opponentSpeciesId = opponent.species.speciesId,
            baselineKey = baseline.key,
            baseline = base,
            comparisons = comparisons,
            notes = listOf(
                "F88 reports integer Fast-Move breakpoints/bulkpoints for this exact matchup only.",
                "A breakpoint/bulkpoint is not a global IV ranking and does not by itself prove a better full battle outcome.",
                "Charged Moves, shields, energy leads and alternate moves belong in F89 Scenario Explorer."
            )
        )
    }

    private fun profile(subject: BattleBuild, opponent: BattleBuild): FastDamageProfile {
        val outgoing = BattleDamage.damage(subject, 0, opponent, 0, subject.fastMove)
        val incoming = BattleDamage.damage(opponent, 0, subject, 0, opponent.fastMove)
        return FastDamageProfile(
            buildKey = subject.key,
            attack = subject.stats.attack,
            defense = subject.stats.defense,
            hp = subject.stats.hp,
            outgoingFastMoveId = subject.fastMove.moveId,
            outgoingFastDamage = outgoing,
            incomingFastMoveId = opponent.fastMove.moveId,
            incomingFastDamage = incoming,
            fastMovesToKoOpponent = ceil(opponent.stats.hp.toDouble() / outgoing).toInt(),
            opponentFastMovesToKoSubject = ceil(subject.stats.hp.toDouble() / incoming).toInt()
        )
    }

    private fun explain(
        base: FastDamageProfile,
        candidate: FastDamageProfile,
        signals: Set<BreakpointBulkpointSignal>
    ): String {
        if (signals.isEmpty()) {
            return "No integer Fast-Move breakpoint/bulkpoint changes versus the baseline; only continuous stats/HP may differ."
        }
        return buildList {
            if (BreakpointBulkpointSignal.BREAKPOINT_GAIN in signals) {
                add("Fast damage increases ${base.outgoingFastDamage}→${candidate.outgoingFastDamage}.")
            }
            if (BreakpointBulkpointSignal.BREAKPOINT_LOSS in signals) {
                add("Fast damage decreases ${base.outgoingFastDamage}→${candidate.outgoingFastDamage}.")
            }
            if (BreakpointBulkpointSignal.BULKPOINT_GAIN in signals) {
                add("Incoming Fast damage decreases ${base.incomingFastDamage}→${candidate.incomingFastDamage}.")
            }
            if (BreakpointBulkpointSignal.BULKPOINT_LOSS in signals) {
                add("Incoming Fast damage increases ${base.incomingFastDamage}→${candidate.incomingFastDamage}.")
            }
            if (BreakpointBulkpointSignal.HP_BULK_GAIN in signals) {
                add("HP survives more equal-damage Fast Moves (${base.opponentFastMovesToKoSubject}→${candidate.opponentFastMovesToKoSubject}).")
            }
            if (BreakpointBulkpointSignal.HP_BULK_LOSS in signals) {
                add("HP survives fewer equal-damage Fast Moves (${base.opponentFastMovesToKoSubject}→${candidate.opponentFastMovesToKoSubject}).")
            }
        }.joinToString(" ")
    }
}

data class ScenarioBuildVariant(
    val id: String,
    val label: String,
    val build: BattleBuild
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
    }
}

data class ScenarioShieldPair(val subject: Int, val opponent: Int) {
    init { require(subject in 0..2 && opponent in 0..2) }
}

data class ScenarioEnergyPair(val subject: Int, val opponent: Int) {
    init { require(subject in 0..100 && opponent in 0..100) }
}

enum class ScenarioExplorerStatus { READY, REFUSED }

data class ScenarioExplorerCase(
    val caseId: String,
    val subjectVariantId: String,
    val opponentVariantId: String,
    val subjectShields: Int,
    val opponentShields: Int,
    val subjectStartingEnergy: Int,
    val opponentStartingEnergy: Int,
    val outcome: BattleOutcome,
    val battleRating: Int,
    val opponentBattleRating: Int,
    val turns: Int,
    val subjectFinalHp: Int,
    val opponentFinalHp: Int,
    val ratingDeltaVsBaseline: Int,
    val outcomeChangedVsBaseline: Boolean,
    val certificationRoute: CertificationRoute?,
    val assumptions: List<String>
)

data class ScenarioExplorerResult(
    val status: ScenarioExplorerStatus,
    val baselineCaseId: String?,
    val cases: List<ScenarioExplorerCase>,
    val refusalReason: String? = null,
    val notes: List<String> = emptyList()
) {
    init {
        if (status == ScenarioExplorerStatus.REFUSED) require(!refusalReason.isNullOrBlank())
        if (status == ScenarioExplorerStatus.READY) require(cases.isNotEmpty() && baselineCaseId != null)
    }
}

data class ScenarioExplorerRequest(
    val subjectVariants: List<ScenarioBuildVariant>,
    val opponentVariants: List<ScenarioBuildVariant>,
    val shieldPairs: List<ScenarioShieldPair> = listOf(ScenarioShieldPair(1, 1)),
    val energyPairs: List<ScenarioEnergyPair> = listOf(ScenarioEnergyPair(0, 0)),
    val policyA: BattleSidePolicy = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
    val policyB: BattleSidePolicy = BattleSidePolicy(ChargeStrategy.PVPOKE_BASELINE, ShieldStrategy.PVPOKE_DEFAULT),
    val statEffectPolicy: StatEffectPolicy = StatEffectPolicy.PVPOKE_DETERMINISTIC_METER,
    val randomSeed: Long = 1L,
    val maxCases: Int = 256
) {
    init {
        require(subjectVariants.isNotEmpty() && opponentVariants.isNotEmpty())
        require(shieldPairs.isNotEmpty() && energyPairs.isNotEmpty())
        require(maxCases in 1..1024)
        require(subjectVariants.map { it.id }.distinct().size == subjectVariants.size)
        require(opponentVariants.map { it.id }.distinct().size == opponentVariants.size)
    }
}

object ScenarioExplorer {
    fun explore(request: ScenarioExplorerRequest, engine: BattleEngine = BattleEngine()): ScenarioExplorerResult {
        val certification = BattleAdviceCertificationGate.current()
        if (!certification.authoritativeStandard1v1AdviceOpen) {
            return ScenarioExplorerResult(
                status = ScenarioExplorerStatus.REFUSED,
                baselineCaseId = null,
                cases = emptyList(),
                refusalReason = "F48 standard 1v1 advice gate is closed."
            )
        }

        val allBuilds = request.subjectVariants.map { it.build } + request.opponentVariants.map { it.build }
        val guarded = allBuilds.firstOrNull { !SpecialMechanicsRegistry.isStandardAdviceSafe(it.species.speciesId) }
        if (guarded != null) {
            return ScenarioExplorerResult(
                status = ScenarioExplorerStatus.REFUSED,
                baselineCaseId = null,
                cases = emptyList(),
                refusalReason = "${guarded.species.speciesId} is guarded by F47 special-mechanics policy."
            )
        }

        val count = request.subjectVariants.size * request.opponentVariants.size * request.shieldPairs.size * request.energyPairs.size
        require(count <= request.maxCases) { "Scenario matrix $count exceeds maxCases=${request.maxCases}" }

        data class Raw(
            val id: String,
            val sv: ScenarioBuildVariant,
            val ov: ScenarioBuildVariant,
            val shields: ScenarioShieldPair,
            val energy: ScenarioEnergyPair,
            val result: BattleSimulationResult
        )

        val rawCases = buildList {
            request.subjectVariants.forEach { sv ->
                request.opponentVariants.forEach { ov ->
                    request.shieldPairs.forEach { shields ->
                        request.energyPairs.forEach { energy ->
                            val a = sv.build.copy(initialEnergy = energy.subject)
                            val b = ov.build.copy(initialEnergy = energy.opponent)
                            val id = "${sv.id}__${ov.id}__s${shields.subject}-${shields.opponent}__e${energy.subject}-${energy.opponent}"
                            val result = engine.simulate(
                                a,
                                b,
                                BattleScenario(
                                    shieldsA = shields.subject,
                                    shieldsB = shields.opponent,
                                    policyA = request.policyA,
                                    policyB = request.policyB,
                                    statEffectPolicy = request.statEffectPolicy,
                                    randomSeed = request.randomSeed
                                )
                            )
                            add(Raw(id, sv, ov, shields, energy, result))
                        }
                    }
                }
            }
        }

        val baseline = rawCases.first()
        val cases = rawCases.map { raw ->
            ScenarioExplorerCase(
                caseId = raw.id,
                subjectVariantId = raw.sv.id,
                opponentVariantId = raw.ov.id,
                subjectShields = raw.shields.subject,
                opponentShields = raw.shields.opponent,
                subjectStartingEnergy = raw.energy.subject,
                opponentStartingEnergy = raw.energy.opponent,
                outcome = raw.result.outcomeForA,
                battleRating = raw.result.battleRatingA,
                opponentBattleRating = raw.result.battleRatingB,
                turns = raw.result.turns,
                subjectFinalHp = raw.result.finalA.hp,
                opponentFinalHp = raw.result.finalB.hp,
                ratingDeltaVsBaseline = raw.result.battleRatingA - baseline.result.battleRatingA,
                outcomeChangedVsBaseline = raw.result.outcomeForA != baseline.result.outcomeForA,
                certificationRoute = certification.selectedRoute,
                assumptions = raw.result.assumptions + listOf(
                    "subjectVariant=${raw.sv.label}",
                    "opponentVariant=${raw.ov.label}",
                    "shields=${raw.shields.subject}-${raw.shields.opponent}",
                    "startingEnergy=${raw.energy.subject}-${raw.energy.opponent}"
                )
            )
        }

        return ScenarioExplorerResult(
            status = ScenarioExplorerStatus.READY,
            baselineCaseId = baseline.id,
            cases = cases,
            notes = listOf(
                "F89 cases are deterministic standard-1v1 simulations and inherit the active F48 certification route.",
                "Move alternatives and Shadow/Normal are explicit build variants; the explorer never invents a moveset.",
                "Battle Rating 0–1000 is not a win probability."
            )
        )
    }
}
