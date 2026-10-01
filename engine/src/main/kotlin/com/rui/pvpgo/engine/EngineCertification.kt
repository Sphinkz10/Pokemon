package com.rui.pvpgo.engine

/**
 * Product trust must not depend on a single external executable forever.
 *
 * EXACT_PIN is the preferred certification route: execute the pinned PvPoke revision and compare
 * factual outputs/traces. INDEPENDENT_FALLBACK is a deliberately harder alternative: a second,
 * separately implemented validation stack must cover mechanics, outcomes and decision policy.
 *
 * A route being BUILDING is not equivalent to certification and must never open authoritative
 * Matchup Advice.
 */
enum class CertificationRoute { EXACT_PIN, INDEPENDENT_FALLBACK }
enum class CertificationRouteStatus { CERTIFIED, ELIGIBLE, BUILDING, BLOCKED }

data class IndependentCertificationManifest(
    val release: String,
    val standardAssertions: Int,
    val specialAssertions: Int,
    val randomizedAssertions: Int,
    val randomizedCases: Int,
    val mutationSensitivityChecks: Int,
    val fastOnlyOutcomeBattles: Int,
    val chargedOutcomeScenarios: Int,
    val chargedOutcomeAssertions: Int,
    val independentBaselinePolicyScenarios: Int,
    val independentQueuedActionScenarios: Int,
    val unexplainedCriticalDivergences: Int
)

data class CertificationRequirement(
    val id: String,
    val actual: Int,
    val required: Int,
    val critical: Boolean,
    val note: String
) {
    val passed: Boolean get() = actual >= required
}

data class CertificationRouteReport(
    val route: CertificationRoute,
    val status: CertificationRouteStatus,
    val requirements: List<CertificationRequirement>,
    val note: String
) {
    val criticalRequirementsPassed: Boolean
        get() = requirements.filter { it.critical }.all { it.passed }
}

data class BattleAdviceCertificationReport(
    val exactPin: CertificationRouteReport,
    val independentFallback: CertificationRouteReport,
    val authoritativeStandard1v1AdviceOpen: Boolean,
    val selectedRoute: CertificationRoute? = null
)

object IndependentCertificationCurrent {
    /** Counts are release evidence; validations fail the release if these artifacts drift. */
    val manifest = IndependentCertificationManifest(
        release = "1.29",
        standardAssertions = 82,
        specialAssertions = 30,
        randomizedAssertions = 6003,
        randomizedCases = 3000,
        mutationSensitivityChecks = 3,
        fastOnlyOutcomeBattles = 3,
        chargedOutcomeScenarios = 8,
        chargedOutcomeAssertions = 26,
        // V1.29 closes the independent baseline-policy matrix at 24 scenarios across eight
        // behavioral families, without reusing the production ActionLogic implementation.
        independentBaselinePolicyScenarios = 24,
        // V1.19 already closed the independent queued/Floating-Fast ordering matrix.
        independentQueuedActionScenarios = 12,
        unexplainedCriticalDivergences = 0
    )

    fun requirements(manifest: IndependentCertificationManifest = this.manifest): List<CertificationRequirement> = listOf(
        CertificationRequirement("standard-mechanics", manifest.standardAssertions, 80, true,
            "Independent fixed-reference standard mechanics matrix."),
        CertificationRequirement("special-mechanics", manifest.specialAssertions, 30, true,
            "Independent four-family special-mechanics matrix; species guards remain separate."),
        CertificationRequirement("randomized-cases", manifest.randomizedCases, 2500, true,
            "Deterministic CP/stats/damage property cases with fixed seed."),
        CertificationRequirement("mutation-sensitivity", manifest.mutationSensitivityChecks, 3, true,
            "Independent oracle must reject deliberate Shadow/stage/type mutants."),
        CertificationRequirement("fast-outcome-battles", manifest.fastOnlyOutcomeBattles, 3, true,
            "Standalone Fast-only scheduler matches turns/HP/rating/outcome."),
        CertificationRequirement("charged-outcome-scenarios", manifest.chargedOutcomeScenarios, 8, true,
            "Independent micro scenarios cover shields/CMP/KO/tie/stat/strategy/post-lock."),
        CertificationRequirement("baseline-policy-second-implementation", manifest.independentBaselinePolicyScenarios, 24, true,
            "Required before fallback can replace exact-pin certification for Matchup Advice."),
        CertificationRequirement("queued-action-second-implementation", manifest.independentQueuedActionScenarios, 12, true,
            "Requires a broader independent queued/Floating-Fast/Charged ordering matrix."),
        CertificationRequirement("unexplained-critical-divergences", if (manifest.unexplainedCriticalDivergences == 0) 1 else 0, 1, true,
            "No unexplained critical divergence may remain; known intentional differences belong in F92.")
    )
}

object BattleAdviceCertificationGate {
    fun current(parity: BattleParityReport = BattleParityGate.current()): BattleAdviceCertificationReport =
        evaluate(parity, IndependentCertificationCurrent.manifest)

    fun evaluate(
        parity: BattleParityReport,
        manifest: IndependentCertificationManifest
    ): BattleAdviceCertificationReport {
        val exactCertified = parity.externalResultParityConfirmed &&
            parity.capabilities.filter { it.criticalForMatchupAdvice }.all { it.status == ParityStatus.VERIFIED }
        val exact = CertificationRouteReport(
            route = CertificationRoute.EXACT_PIN,
            status = if (exactCertified) CertificationRouteStatus.CERTIFIED else CertificationRouteStatus.BLOCKED,
            requirements = listOf(
                CertificationRequirement(
                    id = "same-revision-external-result-parity",
                    actual = if (parity.externalResultParityConfirmed) 1 else 0,
                    required = 1,
                    critical = true,
                    note = "Three baseline and expanded exact-pin outputs/traces must be captured and diffed."
                )
            ),
            note = if (exactCertified) "Preferred exact-pin certification route passed."
            else "Exact-pin route is not certified; current runtime cannot execute the pinned upstream checkout."
        )

        val fallbackReqs = IndependentCertificationCurrent.requirements(manifest)
        val fallbackReady = fallbackReqs.filter { it.critical }.all { it.passed }
        val fallback = CertificationRouteReport(
            route = CertificationRoute.INDEPENDENT_FALLBACK,
            status = if (fallbackReady) CertificationRouteStatus.CERTIFIED else CertificationRouteStatus.BUILDING,
            requirements = fallbackReqs,
            note = if (fallbackReady)
                "Independent fallback passed all evidence thresholds and certifies standard 1v1 advice without claiming PvPoke exact-pin certification."
            else
                "Independent mechanics/outcome evidence is strong, but one or more independent certification thresholds remain below target."
        )

        val selected = when {
            exactCertified -> CertificationRoute.EXACT_PIN
            fallbackReady -> CertificationRoute.INDEPENDENT_FALLBACK
            else -> null
        }
        return BattleAdviceCertificationReport(
            exactPin = exact,
            independentFallback = fallback,
            authoritativeStandard1v1AdviceOpen = selected != null,
            selectedRoute = selected
        )
    }
}
