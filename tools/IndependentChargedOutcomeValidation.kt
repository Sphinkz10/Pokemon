import com.rui.pvpgo.engine.*
import kotlin.math.floor

/**
 * F44B/F91 charged-action micro scenarios. Expected ordering/damage/state is calculated here
 * without calling BattleEngine helpers other than the public simulate() contract.
 */
private object ChargedOracleV120 {
    const val BONUS = 1.2999999523162842
    const val CPM20 = 0.5974000096
    const val STAB = 1.2000000476837158

    fun stage(v: Int): Double = when (v.coerceIn(-4,4)) {
        -4 -> 0.5; -3 -> 4.0/7.0; -2 -> 4.0/6.0; -1 -> 0.8; 0 -> 1.0
        1 -> 1.25; 2 -> 1.5; 3 -> 1.75; 4 -> 2.0; else -> error("x")
    }
    fun attack(s: PokemonSpecies, iv: IvSpread): Double = (s.baseAttack + iv.attack) * CPM20
    fun defense(s: PokemonSpecies, iv: IvSpread): Double = (s.baseDefense + iv.defense) * CPM20
    fun hp(s: PokemonSpecies, iv: IvSpread): Int = kotlin.math.max(10, floor((s.baseStamina + iv.stamina) * CPM20).toInt())
    fun damage(a: PokemonSpecies, ai: IvSpread, aStage: Int, d: PokemonSpecies, di: IvSpread, dStage: Int, power: Int, stab: Boolean = true): Int =
        floor(power * (if (stab) STAB else 1.0) * (attack(a, ai) * stage(aStage) / (defense(d, di) * stage(dStage))) * 0.5 * BONUS).toInt() + 1
    fun rating(selfHp: Int, selfMax: Int, oppHp: Int, oppMax: Int): Int = floor(
        500.0 * ((oppMax - oppHp).toDouble() / oppMax) + 500.0 * (selfHp.toDouble() / selfMax)
    ).toInt().coerceIn(0, 1000)
}

private fun sp(id: String, dex: Int, atk: Int, def: Int, sta: Int) = PokemonSpecies(
    dex=dex, name=id, speciesId=id, types=listOf("normal"), baseAttack=atk, baseDefense=def, baseStamina=sta
)
private fun build(key: String, s: PokemonSpecies, iv: IvSpread, fast: PvpMove, charged: List<PvpMove>, energy:Int=0, atkStage:Int=0, defStage:Int=0) =
    BattleBuild(key, s, iv, 20.0, fast, charged, initialEnergy=energy, initialAttackStage=atkStage, initialDefenseStage=defStage)

fun main() {
    var assertions = 0
    fun ok(c:Boolean, m:String){ check(c){m}; assertions++ }
    val engine = BattleEngine()
    val iv = IvSpread(0,0,0)
    val highAtk = sp("high", 1, 220, 180, 240)
    val lowAtk = sp("low", 2, 160, 180, 240)
    val equal = sp("equal", 3, 190, 180, 240)
    val fast = PvpMove("FAST", "Fast", type="normal", power=6, energyGain=8, turns=1)
    val charge40 = PvpMove("C40", "C40", type="normal", power=80, energyCost=40)
    val charge55 = PvpMove("C55", "C55", type="normal", power=120, energyCost=55)
    val buff = PvpMove("BUFF", "Buff", type="normal", power=40, energyCost=40, attackerAttackStageDelta=1, opponentDefenseStageDelta=-1, buffApplyChance=1.0)

    // 1. Unshielded charged: exact damage, energy spend, turn/rating.
    run {
        val a = build("a", highAtk, iv, fast, listOf(charge40), 40)
        val b = build("b", lowAtk, iv, fast, listOf(charge40), 0)
        val r = engine.simulate(a,b,BattleScenario(shieldsA=0, shieldsB=0,
            policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER),
            policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST, ShieldStrategy.NEVER), maxTurns=1))
        val dmg = ChargedOracleV120.damage(highAtk,iv,0,lowAtk,iv,0,80)
        val maxA=ChargedOracleV120.hp(highAtk,iv); val maxB=ChargedOracleV120.hp(lowAtk,iv)
        val hpB=(maxB-dmg).coerceAtLeast(0)
        // Defender selected a 1-turn Fast on the same turn; because the Charged was non-lethal,
        // that Fast still resolves after Charged priority.
        val counterFast = ChargedOracleV120.damage(lowAtk,iv,0,highAtk,iv,0,6)
        val hpA=(maxA-counterFast).coerceAtLeast(0)
        ok(r.timeline.first{it.type==BattleEventType.CHARGED_MOVE}.damage==dmg,"unshielded damage")
        ok(r.finalA.energy==0,"unshielded energy")
        ok(r.finalB.hp==hpB && r.finalA.hp==hpA,"unshielded hp + same-turn fast")
        ok(r.finalB.energy==8,"unshielded defender fast energy")
        ok(r.battleRatingA==ChargedOracleV120.rating(hpA,maxA,hpB,maxB),"unshielded rating")
    }

    // 2. Shielded charged: 1 damage, one shield, energy spend.
    run {
        val r=engine.simulate(build("a",highAtk,iv,fast,listOf(charge40),40),build("b",lowAtk,iv,fast,listOf(charge40)),
            BattleScenario(shieldsA=0,shieldsB=1,policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.ALWAYS),maxTurns=1))
        val ce=r.timeline.first{it.type==BattleEventType.CHARGED_MOVE}
        ok(ce.damage==1 && ce.shielded,"shield damage")
        ok(r.finalB.shields==0,"shield spend")
        ok(r.finalA.energy==0,"shield attacker energy")
        ok(r.timeline.any{it.type==BattleEventType.SHIELD},"shield event")
    }

    // 3. Higher true Attack wins CMP; both survive and both moves resolve in order.
    run {
        val r=engine.simulate(build("hi",highAtk,iv,fast,listOf(charge40),40),build("lo",lowAtk,iv,fast,listOf(charge40),40),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),maxTurns=1))
        val charges=r.timeline.filter{it.type==BattleEventType.CHARGED_MOVE}
        ok(charges.size==2,"cmp both resolve")
        ok(charges[0].actorKey=="hi" && charges[1].actorKey=="lo","cmp ordering")
        ok(r.timeline.any{it.type==BattleEventType.CMP},"cmp event")
    }

    // 4. CMP KO: winner's lethal Charged prevents loser Charged from resolving.
    run {
        val glass = sp("glass",4,150,70,70)
        val nuke=PvpMove("NUKE","Nuke",type="normal",power=250,energyCost=40)
        val r=engine.simulate(build("hi",highAtk,iv,fast,listOf(nuke),40),build("glass",glass,iv,fast,listOf(nuke),40),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),maxTurns=1))
        val charges=r.timeline.filter{it.type==BattleEventType.CHARGED_MOVE}
        ok(charges.size==1 && charges[0].actorKey=="hi","cmp ko cancels second")
        ok(r.finalB.hp==0,"cmp ko hp")
        ok(r.outcomeForA.name=="WIN","cmp ko outcome")
    }

    // 5. True Attack tie -> deterministic side A first, marked as CMP tie.
    run {
        val r=engine.simulate(build("a",equal,iv,fast,listOf(charge40),40),build("b",equal,iv,fast,listOf(charge40),40),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),maxTurns=1))
        val charges=r.timeline.filter{it.type==BattleEventType.CHARGED_MOVE}
        ok(charges.first().actorKey=="a","cmp tie deterministic order")
        ok(r.timeline.first{it.type==BattleEventType.CMP}.note?.contains("tie")==true,"cmp tie note")
    }

    // 6. Guaranteed stat effects apply after damage and clamp via the public final state.
    run {
        val r=engine.simulate(build("a",equal,iv,fast,listOf(buff),40,atkStage=4),build("b",equal,iv,fast,listOf(charge40),0,defStage=-4),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),statEffectPolicy=StatEffectPolicy.GUARANTEED_ONLY,maxTurns=1))
        ok(r.finalA.attackStage==4,"attack clamp")
        ok(r.finalB.defenseStage==-4,"defense clamp")
        ok(r.timeline.none{it.type==BattleEventType.STAT_CHANGE},"no event when clamps cause no change")
    }

    // 7. MAX_DAMAGE selects the stronger affordable move; CHEAPEST selects low energy.
    run {
        val rMax=engine.simulate(build("a",equal,iv,fast,listOf(charge40,charge55),60),build("b",equal,iv,fast,listOf(charge40)),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=BattleSidePolicy(ChargeStrategy.MAX_DAMAGE,ShieldStrategy.NEVER),policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),maxTurns=1))
        val rCheap=engine.simulate(build("a",equal,iv,fast,listOf(charge40,charge55),60),build("b",equal,iv,fast,listOf(charge40)),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),maxTurns=1))
        ok(rMax.timeline.first{it.type==BattleEventType.CHARGED_MOVE}.moveId=="C55","max damage choice")
        ok(rCheap.timeline.first{it.type==BattleEventType.CHARGED_MOVE}.moveId=="C40","cheapest choice")
        ok(rMax.finalA.energy==5 && rCheap.finalA.energy==20,"strategy energy spend")
    }

    // 8. Post-Charged one-turn lock: charged on T1, no input T2, 1-turn Fast resolves on T3.
    run {
        val r=engine.simulate(build("a",equal,iv,fast,listOf(charge40),40),build("b",equal,iv,fast,listOf(charge40),0),
            BattleScenario(shieldsA=0,shieldsB=0,policyA=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),policyB=BattleSidePolicy(ChargeStrategy.CHEAPEST,ShieldStrategy.NEVER),maxTurns=3))
        val charge=r.timeline.first{it.type==BattleEventType.CHARGED_MOVE && it.actorKey=="a"}
        val nextFast=r.timeline.firstOrNull{it.type==BattleEventType.FAST_MOVE && it.actorKey=="a"}
        ok(charge.turn==1,"postlock charged T1")
        ok(nextFast?.turn==3,"postlock fast T3 actual=${nextFast?.turn}")
        ok(r.decisionTrace.none{it.turn==2 && (it.type==BattleDecisionType.FAST_STARTED || it.type==BattleDecisionType.CHARGED_SELECTED)},"postlock no T2 input")
    }

    println("INDEPENDENT_CHARGED_OUTCOMES_V1_29_OK")
    println("scenarios=8 assertions=$assertions coverage=unshielded,shield,CMP,CMP-KO,tie,stage-clamp,strategy,post-charged-lock")
}
