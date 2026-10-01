import com.rui.pvpgo.engine.*
import java.io.File

fun main() {
    PinnedBattleCaseCatalog.validatePinnedData()
    val cases = PinnedBattleCaseCatalog.expandedCases
    check(cases.size == 8)
    val coverage = cases.flatMap { it.coverage }.toSet()
    check(listOf("0-shield","1-shield","2-shield","fast-1","fast-2","fast-3","fast-4","fast-5","shadow","guaranteed-debuff","guaranteed-self-buff").all { it in coverage })
    val engine = BattleEngine()
    val byId = cases.associate { it.fixtureId to engine.simulate(it.a, it.b, it.scenario()) }
    val debuff = byId.getValue("EXP-GALVANTULA-SWAMPERT-4TURN-DEBUFF")
    val buff = byId.getValue("EXP-TALONFLAME-GALVANTULA-5TURN-BUFF")
    check(debuff.finalB.attackStage == -1)
    check(debuff.timeline.any { it.type == BattleEventType.STAT_CHANGE && it.moveId == "LUNGE" })
    check(buff.finalA.attackStage == 1)
    check(buff.timeline.any { it.type == BattleEventType.STAT_CHANGE && it.moveId == "FLAME_CHARGE" })
    val runner = File("tools/pvpoke_expanded_parity_runner.js").readText()
    check("EXP-AZU-FERALIGATR-0S" in runner && "EXP-AZU-FERALIGATR-2S" in runner)
    check("EXP-ALTARIA-AZU-1TURN" in runner)
    check("EXP-GALVANTULA-SWAMPERT-4TURN-DEBUFF" in runner)
    check("EXP-TALONFLAME-GALVANTULA-5TURN-BUFF" in runner)
    check("attackStage:Number(a.statBuffs" in runner)
    println("EXPANDED_PARITY_V1_29_READY")
    println("cases=${cases.size} localDebuff=${debuff.finalB.attackStage} localBuff=${buff.finalA.attackStage} externalCapture=PENDING")
}
