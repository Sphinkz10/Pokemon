package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon

/**
 * A small, honest learning journey over the user's *own* saved Pokémon.
 * It NEVER infers a competitive PvP rank from IV shape alone.
 * Comparing opens the existing species/league IV target tool.
 */
internal enum class CollectionGuideStep(val title: String, val description: String) {
    ALL(
        "Tudo",
        "Escolhe uma etapa para começares. A app não decide que um IV é bom só pela distribuição."
    ),
    REVIEW(
        "1 · Confirmar",
        "Começa pelos exemplares com CP, nível ou outros dados por confirmar no Pokémon GO."
    ),
    COMPARE(
        "2 · Comparar IVs",
        "Compara os IVs registados por espécie e por liga. Um 0/15/15 não é garantia de melhor rank."
    ),
    PLAN(
        "3 · Preparar plano",
        "Revê os exemplares com um plano PvP guardado, antes de gastar recursos."
    )
}

internal object CollectionGuidePolicy {
    fun matches(
        step: CollectionGuideStep,
        owned: OwnedPokemon,
        speciesExistsInCatalog: Boolean,
        hasSavedPlan: Boolean
    ): Boolean = when (step) {
        CollectionGuideStep.ALL -> true
        CollectionGuideStep.REVIEW ->
            owned.cp == null || owned.level == null || owned.uncertainFields.isNotEmpty()
        CollectionGuideStep.COMPARE ->
            speciesExistsInCatalog &&
                owned.iv.attack in 0..15 &&
                owned.iv.defense in 0..15 &&
                owned.iv.stamina in 0..15
        CollectionGuideStep.PLAN -> hasSavedPlan
    }

    fun canOpenIvComparison(
        step: CollectionGuideStep,
        speciesExistsInCatalog: Boolean
    ): Boolean = step == CollectionGuideStep.COMPARE && speciesExistsInCatalog
}
