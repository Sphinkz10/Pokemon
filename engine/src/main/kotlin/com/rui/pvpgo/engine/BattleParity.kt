package com.rui.pvpgo.engine

enum class ParityStatus { VERIFIED, PARTIAL, PENDING }

data class ParityCapability(
    val id: String,
    val label: String,
    val status: ParityStatus,
    val criticalForMatchupAdvice: Boolean,
    val note: String,
    val referenceUrl: String? = null
)

data class BattleParityReport(
    val upstream: String,
    val checkedAt: String,
    val capabilities: List<ParityCapability>,
    val externalResultParityConfirmed: Boolean = false
) {
    val verifiedCount: Int get() = capabilities.count { it.status == ParityStatus.VERIFIED }
    val partialCount: Int get() = capabilities.count { it.status == ParityStatus.PARTIAL }
    val pendingCount: Int get() = capabilities.count { it.status == ParityStatus.PENDING }
    val exactPinMatchupAdviceGateOpen: Boolean
        get() = externalResultParityConfirmed && capabilities.filter { it.criticalForMatchupAdvice }.all { it.status == ParityStatus.VERIFIED }
    /** Product gate: preferred exact-pin certification OR the stricter independent fallback route. */
    val matchupAdviceGateOpen: Boolean
        get() = BattleAdviceCertificationGate.current(this).authoritativeStandard1v1AdviceOpen
}

/**
 * Product-facing trust gate. This is intentionally explicit: Matchup/Team advice should not be
 * presented as validated while any critical simulator mechanic remains PARTIAL or PENDING.
 */
object BattleParityGate {
    const val PVPOKE_BATTLE_URL = PvPokeReferencePin.BATTLE_URL
    const val PVPOKE_DAMAGE_URL = "https://github.com/pvpoke/pvpoke/blob/${PvPokeReferencePin.COMMIT_SHA}/src/js/battle/DamageCalculator.js"
    const val PVPOKE_ACTION_LOGIC_URL = PvPokeReferencePin.ACTION_LOGIC_URL
    const val PVPOKE_BATTLE_UI_URL = "https://pvpoke.com/battle/"

    fun current(): BattleParityReport = BattleParityReport(
        upstream = "PvPoke ${PvPokeReferencePin.SITE_VERSION} @ ${PvPokeReferencePin.SHORT_SHA}",
        checkedAt = "2026-09-14",
        capabilities = listOf(
            ParityCapability("turns", "0.5 s turn model", ParityStatus.VERIFIED, true,
                "Upstream simulator advances in 500 ms turns.", PVPOKE_BATTLE_URL),
            ParityCapability("damage", "Damage formula + floor/+1", ParityStatus.VERIFIED, true,
                "Formula and multipliers cross-checked against DamageCalculator.", PVPOKE_DAMAGE_URL),
            ParityCapability("types", "Type effectiveness", ParityStatus.VERIFIED, true,
                "1.6 / 0.625 / 0.390625 stacking covered by tests.", PVPOKE_DAMAGE_URL),
            ParityCapability("shadow", "Shadow attack/defense multipliers", ParityStatus.VERIFIED, true,
                "Damage path applies shadow attack and defense multipliers.", PVPOKE_DAMAGE_URL),
            ParityCapability("cmp", "Charged Move Priority", ParityStatus.VERIFIED, true,
                "Higher unmodified Attack resolves Charged Move priority.", PVPOKE_BATTLE_UI_URL),
            ParityCapability("rating", "Battle Rating 0–1000", ParityStatus.VERIFIED, true,
                "500 damage fraction + 500 surviving HP fraction.", PVPOKE_BATTLE_UI_URL),
            ParityCapability("simultaneous", "Simultaneous Fast Move KO", ParityStatus.VERIFIED, true,
                "Engine resolves same-turn fast hits without list-order bias.", PVPOKE_BATTLE_UI_URL),
            ParityCapability("floating-fast", "Floating Fast Move timing", ParityStatus.VERIFIED, true,
                "Queued Fast Moves float on the first turn after a Charged sequence; KO invalidation and post-Charged no-input fixtures pass.", PVPOKE_BATTLE_URL),
            ParityCapability("action-trace", "Turn-by-turn local decision trace", ParityStatus.VERIFIED, false,
                "V1.4 records turn state, Fast selection/start/resolve, Charged selection/resolve, shield decisions, CMP and post-Charged locks for deterministic parity debugging.", PVPOKE_ACTION_LOGIC_URL),
            ParityCapability("shield-policy", "Pinned default shield policy", ParityStatus.VERIFIED, true,
                "V1.6 corrects V1.5: pinned Battle.js defaults ordinary Charged Moves to shield=true and consults wouldShield() only in delegated stat-effect/special cases.", PVPOKE_BATTLE_URL),
            ParityCapability("move-timing", "Optimize Charged Move timing", ParityStatus.PARTIAL, true,
                "V1.9 retains the pinned cadence target, energy-overflow, survival, KO and opponent-pressure guards and adds the standard-form post-plan layer. Exact parity still depends on queued-action interactions and same-revision external outputs.", PVPOKE_ACTION_LOGIC_URL),
            ParityCapability("action-pre-dp", "Pinned pre-DP ActionLogic branches", ParityStatus.VERIFIED, true,
                "V1.7 fixture-tests survival urgency, immediate lethal Charged selection, timing guards and long-cycle simple-move selection; V1.8 adds the downstream DP core as a separate verified capability.", PVPOKE_ACTION_LOGIC_URL),
            ParityCapability("action-dp-core", "Dynamic-programming KO planner mechanics", ParityStatus.PARTIAL, true,
                "V1.8 implements and fixture-tests the ~400-735 DP mechanics. V1.29 retains an intentional divergence from a93147b: local dominance pruning uses the intended opponent HP/shield fields, while the pinned source has documented dead pruning (#380). Exact external parity is therefore not claimed for this capability.", PVPOKE_ACTION_LOGIC_URL),
            ParityCapability("action-post-plan", "Pinned post-plan bait/reorder/defer policy", ParityStatus.VERIFIED, true,
                "V1.9 ports and fixture-tests the standard-form ~736-920 policy: bait build-up, no-bait reorder, shield-state bandaids, self-debuff defer/stack and farm-down boost forcing.", PVPOKE_ACTION_LOGIC_URL),
            ParityCapability("action-policy", "Default action/charged decision policy", ParityStatus.PARTIAL, true,
                "Pre-DP, DP core and standard-form post-plan logic are source-aligned. Exact move-timing/queued-action interactions, special-form branches and same-revision external outputs still gate full policy parity.", PVPOKE_ACTION_LOGIC_URL),
            ParityCapability("chance-effects", "Chance buffs/debuffs", ParityStatus.VERIFIED, false,
                "PVPOKE_DETERMINISTIC_METER reproduces the current non-sandbox cumulative apply-meter rule; seeded random remains available separately.", PVPOKE_BATTLE_URL),
            ParityCapability("special-forms", "Form-change and bespoke damage mechanics", ParityStatus.PARTIAL, true,
                "V1.29 retains Mimikyu Disguise, Aegislash stance plus form-specific Fast Move records, intended Morpeko toggling, and Cramorant core (HP-based load, Gulp Missile retaliation and pinned weak-hit shield delegation). The expanded independent matrix caught and now guards the Aegislash Shield 0-power/+6 versus Blade normal Fast-Move transition. Exact-pin external traces, switch-reset/3v3 context and broader special action-policy remain parity-gated; all bespoke mechanics remain authoritative-blocked.", PVPOKE_DAMAGE_URL),
            ParityCapability("known-divergences", "Versioned known-divergence registry", ParityStatus.VERIFIED, false,
                "V1.29 retains intentional local-vs-reference differences with commit, issue, disposition and advice-gate policy; currently Morpeko toggle #379 and ActionLogic dominance pruning #380.", PVPOKE_ACTION_LOGIC_URL),
            ParityCapability("independent-reference", "Independent mechanics/reference validation", ParityStatus.VERIFIED, true,
                "V1.29 formalizes F44B: 82 fixed standard assertions + 30 special assertions + 6,003 deterministic randomized assertions + 8 Charged/CMP/shield micro-scenarios. F91/F44B is VERIFIED for standard 1v1 fallback certification: the independent baseline-policy matrix reached 24/24 across 8 families and queued-action coverage remains 12/12. This does not certify PvPoke exact-pin parity or bespoke special-species advice.", "https://pokemongohub.net/post/meta/cramorant-form-change-mechanic-in-pokemon-go-explained/"),
            ParityCapability("3v3", "Switching / full 3v3 battle", ParityStatus.PENDING, false,
                "Current engine is a validated-target 1v1 simulator; team switching comes later.")
        ),
        externalResultParityConfirmed = false
    )
}
