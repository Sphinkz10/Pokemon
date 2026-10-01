package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvpBuildPlan

enum class MoveRequirement {
    ALREADY_OWNED,
    FAST_TM,
    CHARGED_TM,
    ELITE_FAST_TM,
    ELITE_CHARGED_TM,
    SECOND_CHARGED_MOVE,
    REMOVE_FRUSTRATION_WHEN_ELIGIBLE,
    SPECIAL_SHADOW_MOVE,
    SPECIAL_PURIFIED_MOVE,
    UNAVAILABLE,
    UNKNOWN
}

data class MoveChange(
    val targetMoveId: String,
    val category: MoveCategory,
    val requirement: MoveRequirement,
    val explanation: String
)

data class MovesetAudit(
    val speciesId: String,
    val desiredFastMoveId: String?,
    val desiredChargedMoveIds: List<String>,
    val changes: List<MoveChange>,
    val currentMovesLegal: Boolean,
    val battleReadyMoves: Boolean,
    val warnings: List<String>
) {
    val requiresEliteTm: Boolean get() = changes.any {
        it.requirement == MoveRequirement.ELITE_FAST_TM || it.requirement == MoveRequirement.ELITE_CHARGED_TM
    }
    val requiresSecondChargedUnlock: Boolean get() = changes.any { it.requirement == MoveRequirement.SECOND_CHARGED_MOVE }
}

object MovesetAnalyzer {
    private const val FRUSTRATION = "FRUSTRATION"
    private const val RETURN = "RETURN"

    fun analyze(
        species: PokemonSpecies,
        owned: OwnedPokemon,
        desiredFastMoveId: String?,
        desiredChargedMoveIds: List<String>,
        movesById: Map<String, PvpMove>
    ): MovesetAudit {
        require(desiredChargedMoveIds.size <= 2)

        val currentFast = owned.fastMoveId
        val currentCharged = owned.chargedMoveIds
        val changes = mutableListOf<MoveChange>()
        val warnings = mutableListOf<String>()

        val currentFastLegal = currentFast == null || isLegalForSpecies(species, owned, currentFast, MoveCategory.FAST, movesById)
        val currentChargedLegal = currentCharged.all { isLegalForSpecies(species, owned, it, MoveCategory.CHARGED, movesById) }

        if (!currentFastLegal) warnings += "O Fast Move atual não pertence ao movepool conhecido desta forma."
        if (!currentChargedLegal) warnings += "Há um Charged Move atual fora do movepool conhecido desta forma."
        if (owned.isShadow && FRUSTRATION in currentCharged && desiredChargedMoveIds.none { it == FRUSTRATION }) {
            warnings += "Frustration está presente; a remoção depende de uma janela/evento elegível no jogo."
        }

        desiredFastMoveId?.let { target ->
            changes += requiredChange(species, owned, target, MoveCategory.FAST, currentFast == target, movesById)
        }

        desiredChargedMoveIds.distinct().forEach { target ->
            val alreadyOwned = target in currentCharged
            if (!alreadyOwned && currentCharged.size < 2 && desiredChargedMoveIds.size >= 2 && owned.secondChargedMoveUnlocked != true) {
                changes += MoveChange(
                    targetMoveId = target,
                    category = MoveCategory.CHARGED,
                    requirement = MoveRequirement.SECOND_CHARGED_MOVE,
                    explanation = "É necessário desbloquear o segundo Charged Move antes de fechar este moveset."
                )
            }
            changes += requiredChange(species, owned, target, MoveCategory.CHARGED, alreadyOwned, movesById)
        }

        val actionable = changes.filter { it.requirement !in setOf(MoveRequirement.ALREADY_OWNED) }
        val impossible = changes.any { it.requirement == MoveRequirement.UNAVAILABLE }
        val battleReady = !impossible && desiredFastMoveId != null && desiredChargedMoveIds.isNotEmpty() &&
            actionable.none { it.requirement != MoveRequirement.ALREADY_OWNED }

        return MovesetAudit(
            speciesId = species.speciesId,
            desiredFastMoveId = desiredFastMoveId,
            desiredChargedMoveIds = desiredChargedMoveIds,
            changes = changes.distinctBy { Triple(it.targetMoveId, it.category, it.requirement) },
            currentMovesLegal = currentFastLegal && currentChargedLegal,
            battleReadyMoves = battleReady,
            warnings = warnings
        )
    }

    fun analyzePlan(
        species: PokemonSpecies,
        owned: OwnedPokemon,
        plan: PvpBuildPlan,
        movesById: Map<String, PvpMove>
    ): MovesetAudit = analyze(
        species = species,
        owned = owned,
        desiredFastMoveId = plan.desiredFastMoveId,
        desiredChargedMoveIds = plan.desiredChargedMoveIds,
        movesById = movesById
    )

    fun isLegalForSpecies(
        species: PokemonSpecies,
        owned: OwnedPokemon,
        moveId: String,
        category: MoveCategory,
        movesById: Map<String, PvpMove>
    ): Boolean {
        val move = movesById[moveId] ?: return false
        if (move.category != category) return false
        if (owned.isShadow && moveId == FRUSTRATION && category == MoveCategory.CHARGED) return true
        if (owned.isPurified && moveId == RETURN && category == MoveCategory.CHARGED) return true
        return when (category) {
            MoveCategory.FAST -> moveId in species.fastMoveIds
            MoveCategory.CHARGED -> moveId in species.chargedMoveIds
        }
    }

    private fun requiredChange(
        species: PokemonSpecies,
        owned: OwnedPokemon,
        targetMoveId: String,
        category: MoveCategory,
        alreadyOwned: Boolean,
        movesById: Map<String, PvpMove>
    ): MoveChange {
        if (alreadyOwned) return MoveChange(targetMoveId, category, MoveRequirement.ALREADY_OWNED, "Já tens este ataque.")
        if (!isLegalForSpecies(species, owned, targetMoveId, category, movesById)) {
            return MoveChange(targetMoveId, category, MoveRequirement.UNAVAILABLE, "Este ataque não pertence ao movepool conhecido desta forma.")
        }
        if (owned.isShadow && category == MoveCategory.CHARGED && FRUSTRATION in owned.chargedMoveIds && targetMoveId != FRUSTRATION) {
            return MoveChange(
                targetMoveId, category, MoveRequirement.REMOVE_FRUSTRATION_WHEN_ELIGIBLE,
                "Primeiro tens de remover Frustration durante uma janela elegível; depois podes aplicar a TM necessária."
            )
        }
        if (targetMoveId == FRUSTRATION) return MoveChange(targetMoveId, category, MoveRequirement.SPECIAL_SHADOW_MOVE, "Frustration é um ataque especial de Shadow.")
        if (targetMoveId == RETURN) return MoveChange(targetMoveId, category, MoveRequirement.SPECIAL_PURIFIED_MOVE, "Return é um ataque especial de Purified.")
        if (targetMoveId in species.eliteMoveIds) {
            val req = if (category == MoveCategory.FAST) MoveRequirement.ELITE_FAST_TM else MoveRequirement.ELITE_CHARGED_TM
            return MoveChange(targetMoveId, category, req, "Este ataque está marcado como Elite/legacy para esta espécie.")
        }
        val req = if (category == MoveCategory.FAST) MoveRequirement.FAST_TM else MoveRequirement.CHARGED_TM
        return MoveChange(targetMoveId, category, req, "Pode ser obtido através do movepool normal desta espécie.")
    }
}

object MoveMath {
    const val SAME_TYPE_ATTACK_BONUS = 1.2

    fun hasStab(species: PokemonSpecies, move: PvpMove): Boolean =
        species.types.any { it.equals(move.type, ignoreCase = true) }

    fun baseDpt(move: PvpMove, species: PokemonSpecies? = null): Double? =
        move.dpt?.let { if (species != null && hasStab(species, move)) it * SAME_TYPE_ATTACK_BONUS else it }

    fun baseDpe(move: PvpMove, species: PokemonSpecies? = null): Double? =
        move.dpe?.let { if (species != null && hasStab(species, move)) it * SAME_TYPE_ATTACK_BONUS else it }
}
