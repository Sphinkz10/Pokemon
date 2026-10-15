package com.rui.pvpgo

/**
 * Target sections in the real Pokémon 360 LazyColumn.
 * The first eight static items are: identity (0), rank (1), league selector (2),
 * readiness (3), budget/target (4), actual build intro (5), CP/level (6),
 * and fast-move editor (7). Dynamic move rows are inserted after index 7.
 * The final six items always contain the saved-plan editor.
 *
 * A guided action NEVER writes specimen, resources or plans automatically.
 */
internal object SavedPlanFocusPolicy {
    fun targetIndex(step: SavedPlanNextStep, itemCount: Int): Int {
        require(itemCount > 0)
        val wanted = when (step) {
            SavedPlanNextStep.REVIEW_SPECIES -> 0
            SavedPlanNextStep.CORRECT_LEAGUE -> 2
            SavedPlanNextStep.REGISTER_RESOURCES,
            SavedPlanNextStep.SET_INVESTMENT_TARGET -> 4
            SavedPlanNextStep.VERIFY_SPECIMEN -> 6
            SavedPlanNextStep.REGISTER_MOVES -> 7
            SavedPlanNextStep.REVIEW_PLAN -> (itemCount - 6).coerceAtLeast(0)
        }
        return wanted.coerceIn(0, itemCount - 1)
    }

    fun label(step: SavedPlanNextStep): String = when (step) {
        SavedPlanNextStep.REVIEW_PLAN -> "Rever plano"
        SavedPlanNextStep.REVIEW_SPECIES -> "Confirmar espécie"
        SavedPlanNextStep.VERIFY_SPECIMEN -> "Confirmar CP e nível"
        SavedPlanNextStep.CORRECT_LEAGUE -> "Rever liga e CP"
        SavedPlanNextStep.REGISTER_MOVES -> "Confirmar ataques"
        SavedPlanNextStep.REGISTER_RESOURCES -> "Registar recursos"
        SavedPlanNextStep.SET_INVESTMENT_TARGET -> "Definir alvo"
    }
}
