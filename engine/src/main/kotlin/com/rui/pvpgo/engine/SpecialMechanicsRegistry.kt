package com.rui.pvpgo.engine

/**
 * Explicit trust boundary for pinned PvPoke mechanics that are not represented by the standard
 * BattleBuild/BattleEngine state model yet. These species must not silently receive authoritative
 * matchup advice from the standard engine.
 */
enum class SpecialMechanicKind {
    FORM_CHANGE_ON_SHIELD_OR_CHARGED,
    DISGUISE_PROTECT,
    VARIABLE_GULP_MISSILE,
    TOGGLE_FORM_ON_CHARGED
}

data class UnsupportedBattleMechanic(
    val id: String,
    val speciesIds: Set<String>,
    val kind: SpecialMechanicKind,
    val reason: String,
    val sourceCommitSha: String = PvPokeReferencePin.COMMIT_SHA
)

object SpecialMechanicsRegistry {
    /** Implemented locally, but still subject to the global exact-pin external parity gate. */
    val implementedLocally: Set<String> = setOf("mimikyu-disguise", "aegislash-stance-change", "morpeko-form-toggle", "cramorant-gulp-missile")

    val unsupportedForStandardAdvice: List<UnsupportedBattleMechanic> = listOf(
        UnsupportedBattleMechanic(
            id = "aegislash-stance-change",
            speciesIds = setOf("aegislash_blade", "aegislash_shield"),
            kind = SpecialMechanicKind.FORM_CHANGE_ON_SHIELD_OR_CHARGED,
            reason = "V1.29 models Shield -> Blade before Charged damage, Blade -> Shield after Protect Shield, and swaps Shield-specific Fast Moves (0 power/+6 energy) to normal Blade values. Authoritative advice remains blocked until exact-pin trace, switch-reset/3v3 and form-specific ActionLogic validation pass."
        ),
        UnsupportedBattleMechanic(
            id = "mimikyu-disguise",
            speciesIds = setOf("mimikyu", "mimikyu_busted"),
            kind = SpecialMechanicKind.DISGUISE_PROTECT,
            reason = "V1.29 retains the pinned Disguise transition locally, but authoritative advice remains blocked until exact-pin external trace validation passes."
        ),
        UnsupportedBattleMechanic(
            id = "cramorant-gulp-missile",
            speciesIds = setOf("cramorant", "cramorant_gulping", "cramorant_gorging"),
            kind = SpecialMechanicKind.VARIABLE_GULP_MISSILE,
            reason = "V1.29 retains the Cramorant core locally (HP-based Gulping/Gorging load, Gulp Missile retaliation, percent-max-HP damage, guaranteed debuff, faint-through trigger, reset and pinned weak-hit shield delegation). Authoritative advice remains blocked until exact-pin traces, switch-reset/3v3 behavior and broader special action-policy are validated."
        ),
        UnsupportedBattleMechanic(
            id = "morpeko-form-toggle",
            speciesIds = setOf("morpeko_full_belly", "morpeko_hangry"),
            kind = SpecialMechanicKind.TOGGLE_FORM_ON_CHARGED,
            reason = "V1.29 retains the intended toggle on every Charged Move and swaps Aura Wheel Electric/Dark with the active form. The pinned a93147b source has a documented one-way-toggle bug (#379), so authoritative advice stays blocked until the divergence policy is validated against an independent reference."
        )
    )

    private val bySpecies: Map<String, UnsupportedBattleMechanic> = buildMap {
        unsupportedForStandardAdvice.forEach { m -> m.speciesIds.forEach { put(it, m) } }
    }

    fun restrictionFor(speciesId: String): UnsupportedBattleMechanic? = bySpecies[speciesId.lowercase()]
    fun isStandardAdviceSafe(speciesId: String): Boolean = restrictionFor(speciesId) == null
}
