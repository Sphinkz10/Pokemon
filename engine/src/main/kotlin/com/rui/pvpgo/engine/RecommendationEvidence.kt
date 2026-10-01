package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.BattleOutcome

/**
 * Immutable input contract for any future matchup/team recommendation.
 * A recommendation is never allowed to float free of the exact battle assumptions that produced it.
 */
data class BattleBuildSnapshot(
    val key: String,
    val speciesId: String,
    val iv: IvSpread,
    val level: Double,
    val fastMoveId: String,
    val chargedMoveIds: List<String>,
    val isShadow: Boolean,
    val initialEnergy: Int,
    val initialAttackStage: Int,
    val initialDefenseStage: Int
)

data class ScenarioSnapshot(
    val subject: BattleBuildSnapshot,
    val opponent: BattleBuildSnapshot,
    val shieldsA: Int,
    val shieldsB: Int,
    val policyA: BattleSidePolicy,
    val policyB: BattleSidePolicy,
    val statEffectPolicy: StatEffectPolicy,
    val randomSeed: Long,
    val maxTurns: Int,
    val engineVersion: String,
    val rulesetId: String,
    val dataVersion: String,
    val upstreamRepository: String,
    val upstreamCommitSha: String,
    val upstreamSiteVersion: String
)

enum class EvidenceStatus {
    /** The engine can calculate the scenario, but the global Matchup Advice parity gate is still closed. */
    EXPERIMENTAL,
    /** A known mechanic/capability prevents authoritative recommendation for this exact scenario. */
    REFUSED,
    /** Allowed only after the global parity gate is open and the scenario has no local capability restriction. */
    VALIDATED
}

data class BattleEvidenceCard(
    val scenario: ScenarioSnapshot,
    val status: EvidenceStatus,
    val battleRating: Int,
    val opponentBattleRating: Int,
    val outcome: BattleOutcome,
    val turns: Int,
    val finalSubject: BattleStateSnapshot,
    val finalOpponent: BattleStateSnapshot,
    val assumptions: List<String>,
    val restrictions: List<String>,
    val trustStatement: String
) {
    init {
        require(battleRating in 0..1000)
        require(opponentBattleRating in 0..1000)
        require(turns >= 0)
    }
}

object RecommendationEvidence {
    const val ENGINE_VERSION = "1.29"
    const val STANDARD_1V1_RULESET = "pokemon-go-pvp-1v1-standard"

    fun scenario(
        subject: BattleBuild,
        opponent: BattleBuild,
        battle: BattleScenario,
        dataVersion: String = "pvpoke-${PvPokeReferencePin.SITE_VERSION}@${PvPokeReferencePin.SHORT_SHA}"
    ): ScenarioSnapshot = ScenarioSnapshot(
        subject = subject.snapshot(),
        opponent = opponent.snapshot(),
        shieldsA = battle.shieldsA,
        shieldsB = battle.shieldsB,
        policyA = battle.policyA,
        policyB = battle.policyB,
        statEffectPolicy = battle.statEffectPolicy,
        randomSeed = battle.randomSeed,
        maxTurns = battle.maxTurns,
        engineVersion = ENGINE_VERSION,
        rulesetId = STANDARD_1V1_RULESET,
        dataVersion = dataVersion,
        upstreamRepository = PvPokeReferencePin.REPOSITORY,
        upstreamCommitSha = PvPokeReferencePin.COMMIT_SHA,
        upstreamSiteVersion = PvPokeReferencePin.SITE_VERSION
    )

    fun card(
        scenario: ScenarioSnapshot,
        result: BattleSimulationResult,
        parity: BattleParityReport = BattleParityGate.current()
    ): BattleEvidenceCard {
        val certification = BattleAdviceCertificationGate.current(parity)
        val restrictions = buildList {
            SpecialMechanicsRegistry.restrictionFor(scenario.subject.speciesId)?.let {
                add("${it.id}: ${it.reason}")
            }
            SpecialMechanicsRegistry.restrictionFor(scenario.opponent.speciesId)?.let {
                add("${it.id}: ${it.reason}")
            }
            if (!parity.externalResultParityConfirmed) {
                add("Exact same-revision external result parity is not yet confirmed.")
            }
            if (!certification.authoritativeStandard1v1AdviceOpen) {
                val fallbackBlockers = certification.independentFallback.requirements
                    .filter { it.critical && !it.passed }
                    .joinToString(", ") { it.id }
                add("Independent fallback certification is ${certification.independentFallback.status}; blockers: $fallbackBlockers.")
            }
            if (!certification.authoritativeStandard1v1AdviceOpen) {
                parity.capabilities.filter { it.criticalForMatchupAdvice && it.status != ParityStatus.VERIFIED }
                    .forEach { add("${it.id}: ${it.status} — ${it.note}") }
            } else if (certification.selectedRoute == CertificationRoute.INDEPENDENT_FALLBACK) {
                add("Certified through INDEPENDENT_FALLBACK; PvPoke exact-pin result parity remains unconfirmed.")
            }
        }.distinct()

        val hasSpeciesRestriction = SpecialMechanicsRegistry.restrictionFor(scenario.subject.speciesId) != null ||
            SpecialMechanicsRegistry.restrictionFor(scenario.opponent.speciesId) != null
        val status = when {
            hasSpeciesRestriction -> EvidenceStatus.REFUSED
            certification.authoritativeStandard1v1AdviceOpen -> EvidenceStatus.VALIDATED
            else -> EvidenceStatus.EXPERIMENTAL
        }

        val trustStatement = when (status) {
            EvidenceStatus.REFUSED -> "Resultado calculado apenas para diagnóstico interno; não deve ser apresentado como advice autoritativo para este cenário."
            EvidenceStatus.EXPERIMENTAL -> "Battle Rating experimental 0–1000; não é probabilidade de vitória. Matchup Advice Gate continua fechado."
            EvidenceStatus.VALIDATED -> "Battle Rating validado para o ruleset e versões declaradas através de ${certification.selectedRoute}; continua a não representar probabilidade de vitória."
        }

        return BattleEvidenceCard(
            scenario = scenario,
            status = status,
            battleRating = result.battleRatingA,
            opponentBattleRating = result.battleRatingB,
            outcome = result.outcomeForA,
            turns = result.turns,
            finalSubject = result.finalA,
            finalOpponent = result.finalB,
            assumptions = result.assumptions,
            restrictions = restrictions,
            trustStatement = trustStatement
        )
    }

    private fun BattleBuild.snapshot() = BattleBuildSnapshot(
        key = key,
        speciesId = species.speciesId,
        iv = iv,
        level = level,
        fastMoveId = fastMove.moveId,
        chargedMoveIds = chargedMoves.map { it.moveId },
        isShadow = isShadow,
        initialEnergy = initialEnergy,
        initialAttackStage = initialAttackStage,
        initialDefenseStage = initialDefenseStage
    )
}
