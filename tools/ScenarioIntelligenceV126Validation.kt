import com.rui.pvpgo.engine.*

private fun ok(v:Boolean,m:String){ if(!v) error(m) }
private fun sp(id:String)=PokemonSpecies(
    dex=1,name=id,speciesId=id,types=listOf("normal"),
    baseAttack=120,baseDefense=120,baseStamina=120
)

fun main(){
    val fast=PvpMove("FAST","Fast",type="normal",power=10,energyGain=8,turns=2)
    val charged=PvpMove("CHARGE","Charge",type="normal",power=60,energyCost=40)
    val chargedHeavy=PvpMove("HEAVY","Heavy",type="normal",power=100,energyCost=55)
    val subjectSpecies=sp("scenario_subject")
    val opponentSpecies=sp("scenario_opponent")
    val opponent=BattleBuild("opp",opponentSpecies,IvSpread(15,15,15),20.0,fast,listOf(charged))

    val baseline=BattleBuild("base",subjectSpecies,IvSpread(0,11,0),20.0,fast,listOf(charged))
    val bpBulk=BattleBuild("bp-bulk",subjectSpecies,IvSpread(15,12,0),20.0,fast,listOf(charged))
    val hpBulk=BattleBuild("hp-bulk",subjectSpecies,IvSpread(0,11,15),20.0,fast,listOf(charged))
    val report=BreakpointBulkpointAnalyzer.analyze(baseline,listOf(bpBulk,hpBulk),opponent)
    val bb=report.comparisons.first{it.candidateKey=="bp-bulk"}
    ok(report.baseline.outgoingFastDamage==7,"Expected baseline outgoing damage 7, got ${report.baseline.outgoingFastDamage}")
    ok(report.baseline.incomingFastDamage==9,"Expected baseline incoming damage 9, got ${report.baseline.incomingFastDamage}")
    ok(bb.profile.outgoingFastDamage==8 && BreakpointBulkpointSignal.BREAKPOINT_GAIN in bb.signals,"F88 breakpoint gain missing")
    ok(bb.profile.incomingFastDamage==8 && BreakpointBulkpointSignal.BULKPOINT_GAIN in bb.signals,"F88 bulkpoint gain missing")
    val hp=report.comparisons.first{it.candidateKey=="hp-bulk"}
    ok(hp.profile.incomingFastDamage==report.baseline.incomingFastDamage,"HP bulk fixture must keep same incoming damage")
    ok(BreakpointBulkpointSignal.HP_BULK_GAIN in hp.signals,"F88 HP bulk gain missing")
    ok(hp.opponentFastMovesToKoSubjectDelta>0,"HP bulk should require more opponent Fast Moves")
    ok(report.notes.any{it.contains("not",true) || it.contains("não",true)},"F88 notes should reject global-IV interpretation")

    val baseVariant=ScenarioBuildVariant("base","baseline",baseline)
    val heavyVariant=ScenarioBuildVariant("heavy","alternate Charged Move",baseline.copy(key="heavy",chargedMoves=listOf(chargedHeavy)))
    val shadowVariant=ScenarioBuildVariant("shadow","Shadow toggle",baseline.copy(key="shadow",isShadow=true))
    val explored=ScenarioExplorer.explore(
        ScenarioExplorerRequest(
            subjectVariants=listOf(baseVariant,heavyVariant,shadowVariant),
            opponentVariants=listOf(ScenarioBuildVariant("opp","opponent baseline",opponent)),
            shieldPairs=listOf(ScenarioShieldPair(0,0),ScenarioShieldPair(1,1)),
            energyPairs=listOf(ScenarioEnergyPair(0,0),ScenarioEnergyPair(40,0)),
            maxCases=32
        )
    )
    ok(explored.status==ScenarioExplorerStatus.READY,"F89 should be READY: ${explored.refusalReason}")
    ok(explored.cases.size==12,"Expected 12 F89 cases, got ${explored.cases.size}")
    ok(explored.cases.all{it.certificationRoute==CertificationRoute.INDEPENDENT_FALLBACK},"F89 must inherit F48 independent certification route")
    ok(explored.cases.any{it.ratingDeltaVsBaseline!=0},"Scenario matrix should produce at least one nonzero rating delta")
    ok(explored.cases.any{it.subjectVariantId=="heavy"} && explored.cases.any{it.subjectVariantId=="shadow"},"Move/Shadow variants missing")
    ok(explored.notes.any{it.contains("not a win probability",true)},"F89 must reject win-probability wording")

    val guardedSpecies=sp("mimikyu")
    val guarded=baseline.copy(key="guarded",species=guardedSpecies)
    val refused=ScenarioExplorer.explore(
        ScenarioExplorerRequest(
            subjectVariants=listOf(ScenarioBuildVariant("guarded","guarded",guarded)),
            opponentVariants=listOf(ScenarioBuildVariant("opp","opp",opponent))
        )
    )
    ok(refused.status==ScenarioExplorerStatus.REFUSED,"F89 special mechanics must remain refused")
    ok(refused.refusalReason?.contains("F47") == true,"Expected explicit F47 refusal")

    println("BREAKPOINT_BULKPOINT_F88_V1_29_OK baseline=${report.baseline.outgoingFastDamage}/${report.baseline.incomingFastDamage} bp=${bb.profile.outgoingFastDamage}/${bb.profile.incomingFastDamage} hpKo=${report.baseline.opponentFastMovesToKoSubject}->${hp.profile.opponentFastMovesToKoSubject}")
    println("SCENARIO_EXPLORER_F89_V1_29_OK cases=${explored.cases.size} route=${explored.cases.first().certificationRoute} changed=${explored.cases.count{it.ratingDeltaVsBaseline!=0}}")
}
