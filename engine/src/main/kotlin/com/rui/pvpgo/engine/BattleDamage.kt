package com.rui.pvpgo.engine

import kotlin.math.floor

object BattleDamage {
    const val TRAINER_BATTLE_BONUS = 1.2999999523162842
    const val STAB = 1.2000000476837158
    const val SHADOW_ATTACK = 1.2
    const val SHADOW_DEFENSE = 0.83333331

    /** GO Trainer Battle stage multiplier. Stage range is -4..+4. */
    fun stageMultiplier(stage: Int): Double {
        val s = stage.coerceIn(-4, 4)
        return if (s > 0) (4.0 + s) / 4.0 else 4.0 / (4.0 - s)
    }

    fun effectiveAttack(baseAttack: Double, stage: Int, isShadow: Boolean): Double =
        baseAttack * stageMultiplier(stage) * if (isShadow) SHADOW_ATTACK else 1.0

    fun effectiveDefense(baseDefense: Double, stage: Int, isShadow: Boolean): Double =
        baseDefense * stageMultiplier(stage) * if (isShadow) SHADOW_DEFENSE else 1.0

    fun damage(
        attacker: BattleBuild,
        attackerAttackStage: Int,
        defender: BattleBuild,
        defenderDefenseStage: Int,
        move: PvpMove,
        chargeMultiplier: Double = 1.0
    ): Int {
        require(chargeMultiplier > 0.0)
        val atk = effectiveAttack(attacker.stats.attack, attackerAttackStage, attacker.isShadow)
        val def = effectiveDefense(defender.stats.defense, defenderDefenseStage, defender.isShadow)
        val stab = if (MoveMath.hasStab(attacker.species, move)) STAB else 1.0
        val effectiveness = PvpTypeChart.effectiveness(move.type, defender.species.types)
        return floor(
            move.power * stab * (atk / def) * effectiveness * chargeMultiplier * 0.5 * TRAINER_BATTLE_BONUS
        ).toInt() + 1
    }

    /** CMP uses the unmodified Attack stat: stat stages and Shadow multipliers do not alter priority. */
    fun cmpAttack(build: BattleBuild): Double = build.stats.attack
}
