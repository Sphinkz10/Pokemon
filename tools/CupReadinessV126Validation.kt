import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.*

private fun ok(v:Boolean,m:String){ if(!v) error(m) }
private fun species(id:String)=PokemonSpecies(
    dex=id.hashCode().ushr(1)%10000+1,
    name=id,speciesId=id,familyId=id,types=listOf("normal"),
    fastMoveIds=listOf("FAST"),chargedMoveIds=listOf("C1","C2"),
    baseAttack=100,baseDefense=100,baseStamina=100
)
private fun owned(id:String,species:String,level:Double,charged:List<String>,second:Boolean)=OwnedPokemon(
    id=id,speciesId=species,iv=IvSpread(0,15,15),level=level,
    fastMoveId="FAST",chargedMoveIds=charged,secondChargedMoveUnlocked=second,createdAtEpochMs=1L
)
private fun metaEntry(species:String,weight:Double)=MetaBuildEntry(
    key="meta-$species",
    opponent=OpponentBuild(
        speciesId=species,fastMoveId="FAST",chargedMoveIds=listOf("C1","C2"),
        iv=IvSpread(0,15,15),level=20.5,isShadow=false
    ),
    weight=weight
)

fun main(){
    val alpha=species("alpha")
    val beta=species("beta")
    val gamma=species("gamma")
    val delta=species("delta")
    val epsilon=species("epsilon")
    val catalog=listOf(alpha,beta,gamma,delta,epsilon)
    val moves=listOf(
        PvpMove("FAST","Fast",type="normal",power=4,energyGain=8,turns=2),
        PvpMove("C1","Charge 1",type="normal",power=55,energyCost=40),
        PvpMove("C2","Charge 2",type="normal",power=80,energyCost=50)
    ).associateBy{it.moveId}
    val ruleset=PvPRuleset(
        id="fixture-cup",version="2026-09",title="Fixture Cup",baseLeague=League.GREAT,
        cpCap=9999,minLevel=1.0,maxLevel=20.5,explicitlyExcludedSpeciesIds=setOf("delta"),
        sourceName="fixture",sourceVersion="1",generatedAtEpochMs=1L
    )
    val meta=VersionedMetaGroup(
        id="fixture-meta",league=League.GREAT,seasonId="s1",cupId="fixture-cup",
        sourceName="fixture",sourceVersion="1",generatedAtEpochMs=1L,
        entries=listOf(metaEntry("alpha",4.0),metaEntry("beta",3.0),metaEntry("gamma",2.0),metaEntry("epsilon",1.0)),
        rulesetId=ruleset.id,rulesetVersion=ruleset.version
    )
    val collection=listOf(
        owned("a","alpha",20.5,listOf("C1","C2"),true),
        owned("b","beta",20.0,listOf("C1","C2"),true),
        owned("g","gamma",20.5,listOf("C1"),false),
        owned("d","delta",20.5,listOf("C1","C2"),true)
    )
    val pack=InvestmentCostPackValidator.build(
        id="costs",version="1",sourceName="fixture",sourceVersion="1",generatedAtEpochMs=1L,
        powerUpSteps=listOf(PowerUpStepCost(20.0,20.5,1000,candy=2)),
        speciesCosts=listOf(
            SpeciesInvestmentCost("alpha","alpha",secondChargedMoveStardust=10000,secondChargedMoveCandy=10),
            SpeciesInvestmentCost("beta","beta",secondChargedMoveStardust=10000,secondChargedMoveCandy=10),
            SpeciesInvestmentCost("gamma","gamma",secondChargedMoveStardust=50000,secondChargedMoveCandy=50),
            SpeciesInvestmentCost("delta","delta",secondChargedMoveStardust=10000,secondChargedMoveCandy=10),
            SpeciesInvestmentCost("epsilon","epsilon",secondChargedMoveStardust=10000,secondChargedMoveCandy=10)
        ),
        evolutionCosts=emptyList()
    )
    val ledger=ResourceLedger(
        stardust=5000,
        candyByFamily=mapOf("alpha" to 100,"beta" to 100,"gamma" to 100,"delta" to 100,"epsilon" to 100),
        fastTm=10,chargedTm=10
    )
    val report=CupReadinessAnalyzer.analyze(
        ruleset=ruleset,collection=collection,catalog=catalog,movesById=moves,
        metaGroup=meta,costPack=pack,ledger=ledger
    )
    ok(report.ready.map{it.ownedPokemonId}==listOf("a"),"F70 ready mismatch: ${report.ready.map{it.ownedPokemonId}}")
    ok(report.candidates.map{it.ownedPokemonId}==listOf("b"),"F70 candidate mismatch")
    ok(report.candidates.single().status==CupReadinessStatus.AFFORDABLE_UPGRADE,"Beta should be affordable upgrade")
    ok(report.candidates.single().investmentPlan?.costs?.stardust==1000,"Beta exact cost mismatch")
    ok(report.blocked.map{it.ownedPokemonId}==listOf("g"),"Gamma should be blocked")
    ok(report.blocked.single().status==CupReadinessStatus.MISSING_RESOURCES,"Gamma missing-resource status expected")
    ok(report.ineligible.map{it.ownedPokemonId}==listOf("d"),"Delta should be ruleset ineligible")
    val gaps=report.gaps.associateBy{it.speciesId}
    ok(gaps["gamma"]?.reason==CupGapReason.ONLY_BLOCKED_OR_INELIGIBLE,"Gamma blocked gap missing")
    ok(gaps["epsilon"]?.reason==CupGapReason.MISSING_FROM_COLLECTION,"Epsilon collection gap missing")
    ok("alpha" !in gaps && "beta" !in gaps,"Ready/candidate species must not be gaps")
    ok(report.notes.any{it.contains("never invents",true)},"F70 must state target moves are not invented")

    val noCosts=CupReadinessAnalyzer.analyze(ruleset,collection,catalog,moves,metaGroup=meta)
    ok(noCosts.ready.any{it.ownedPokemonId=="a"},"Already-ready build should stay READY without cost pack")
    ok(noCosts.candidates.any{it.ownedPokemonId=="b"},"Known target without cost pack should remain candidate")
    ok(noCosts.candidates.first{it.ownedPokemonId=="b"}.status==CupReadinessStatus.REVIEW_UNKNOWN_COST,"No-cost-pack candidate must expose unknown cost")

    val gapSummary = report.gaps.joinToString { "${it.speciesId}:${it.reason}" }
    println("CUP_READINESS_F70_V1_29_OK ready=${report.ready.size} candidate=${report.candidates.size} blocked=${report.blocked.size} ineligible=${report.ineligible.size} gaps=$gapSummary")
}
