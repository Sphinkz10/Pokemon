import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.*

private fun checkThat(v:Boolean,m:String){if(!v) error(m)}
private fun sp(id:String,dex:Int,family:String,evolutions:List<String> = emptyList(), elite:Set<String> = emptySet())=PokemonSpecies(
    dex=dex,name=id,speciesId=id,familyId=family,evolutionSpeciesIds=evolutions,types=listOf("normal"),
    fastMoveIds=listOf("FAST"),chargedMoveIds=listOf("CHARGE"),eliteMoveIds=elite,baseAttack=100,baseDefense=100,baseStamina=100
)
private fun owned(
    id:String,species:String,iv:IvSpread=IvSpread(0,15,15),cp:Int?=500,level:Double?=10.0,
    ext:String?=null,favorite:Boolean=false,shiny:Boolean=false,tags:Set<String> = emptySet(),nickname:String?=null,
    verified:Long?=1_000L, uncertain:Set<OwnedPokemonField> = emptySet(), fast:String?="FAST", charges:List<String> = listOf("CHARGE")
)=OwnedPokemon(
    id=id,speciesId=species,iv=iv,cp=cp,level=level,nickname=nickname,isFavorite=favorite,isShiny=shiny,
    fastMoveId=fast,chargedMoveIds=charges,tags=tags,createdAtEpochMs=100L,updatedAtEpochMs=200L,
    externalSource=if(ext!=null)"scan" else null,externalId=ext,lastVerifiedAtEpochMs=verified,
    verificationSource=CollectionVerificationSource.SCAN,uncertainFields=uncertain
)

fun main(){
    val catalog=listOf(
        sp("alpha",1,"fam-a",listOf("alpha2"),elite=setOf("ELITE")),
        sp("alpha2",2,"fam-a"),
        sp("beta",3,"fam-b")
    )
    val a1=owned("a1","alpha",ext="external-1",favorite=true,nickname="PvPA")
    val a2=owned("a2","alpha",IvSpread(1,14,14),tags=setOf("candidate"),nickname="PvPA2",verified=null)
    val b1=owned("b1","beta",IvSpread(5,5,5),shiny=true,uncertain=setOf(OwnedPokemonField.CP),nickname=null)
    val collection=listOf(a1,a2,b1)

    val exactCandidate=owned("scan-1","alpha",ext="external-1")
    val exact=CollectionReconciler.reconcile(exactCandidate,collection)
    checkThat(exact.action==ReconciliationAction.MERGE_RECOMMENDED && exact.matches.first().confidence==1.0,"External-id reconciliation failed")
    checkThat(exact.requiresHumanConfirmation,"Reconciliation must require human confirmation")
    val freshCandidate=owned("scan-2","alpha2",IvSpread(2,13,13),ext="new")
    checkThat(CollectionReconciler.reconcile(freshCandidate,collection).action==ReconciliationAction.CREATE_NEW,"New candidate should create")

    val plans=listOf(PvpBuildPlan("p1","a2",League.GREAT,status=BuildStatus.PLANNED,priority=80,createdAtEpochMs=100L))
    val box=PvpBoxClassifier.build(collection,plans,2_000L).associateBy{it.ownedPokemonId}
    checkThat(PvpBoxCategory.IMPORTANT in box.getValue("a1").categories,"Favorite should be important")
    checkThat(PvpBoxCategory.CANDIDATE in box.getValue("a2").categories,"Planned build should be candidate")
    checkThat(PvpBoxCategory.NEEDS_CONFIRMATION in box.getValue("b1").categories,"Uncertain data should need confirmation")

    val inbox=CandidateInbox()
    inbox.submit(CandidateInboxItem("in1",freshCandidate,1_000L))
    checkThat(inbox.pending().size==1,"Inbox submit failed")
    val reviewed=inbox.merge("in1","a2",2_000L)
    checkThat(reviewed.status==CandidateInboxStatus.MERGED && inbox.pending().isEmpty(),"Inbox merge failed")

    val duplicateReports=DuplicateResolver.resolve(collection,catalog,listOf(League.GREAT),RankSettings(maxLevel=50.0))
    val fam=duplicateReports.single{it.familyKey=="fam-a"}
    checkThat(fam.ownedPokemonIds==listOf("a1","a2"),"Duplicate family grouping failed")
    checkThat(fam.bestUses.isNotEmpty(),"Duplicate resolver should suggest best uses")
    checkThat(fam.notes.all{!it.contains("transferir",true) || it.contains("não",true)},"Duplicate resolver must not issue transfer command")

    val a1Safety=TransferSafetyEvaluator.assess(a1,collection,catalog,plans)
    checkThat(a1Safety.priority==TransferPriority.PROTECTED && TransferSafetyReason.FAVORITE in a1Safety.reasons,"Favorite safety failed")
    val bSafety=TransferSafetyEvaluator.assess(b1,collection,catalog,plans)
    checkThat(bSafety.priority==TransferPriority.PROTECTED && TransferSafetyReason.UNCERTAIN_DATA in bSafety.reasons,"Uncertain safety failed")
    val low=owned("low","beta",IvSpread(15,0,0),verified=2_000L,charges=emptyList(),fast=null)
    val lowAssessment=TransferSafetyEvaluator.assess(low,collection+low,catalog,plans,highPvpRankThreshold=1)
    checkThat(lowAssessment.message.contains("não é uma ordem",true) || lowAssessment.priority!=TransferPriority.LOW_PRIORITY,"Low priority wording must be safe")

    val nickSearch=PokemonGoSearchStringGenerator.forOwned(listOf("a1","a2"),collection,catalog)
    checkThat(nickSearch.query=="PvPA,PvPA2" && nickSearch.precision==SearchStringPrecision.INDIVIDUAL_HINT,"Nickname search mismatch")
    val speciesSearch=PokemonGoSearchStringGenerator.forOwned(listOf("a1","b1"),collection,catalog)
    checkThat(speciesSearch.query=="1,3" && speciesSearch.precision==SearchStringPrecision.SPECIES_LEVEL,"Dex OR search mismatch")

    val freshness=PokemonFreshnessEvaluator.evaluate(a2,100_000L)
    checkThat(freshness.status==PokemonFreshnessStatus.UNKNOWN,"Missing verification should be unknown")
    val uncertainFresh=PokemonFreshnessEvaluator.evaluate(b1,2_000L)
    checkThat(uncertainFresh.status==PokemonFreshnessStatus.CHECK_SOON,"Uncertain field must override freshness")

    val team=SavedTeam("team1","Test",League.GREAT,listOf(
        TeamMember("a1",TeamRole.LEAD),TeamMember("a2",TeamRole.SAFE_SWITCH),TeamMember("b1",TeamRole.CLOSER)
    ),createdAtEpochMs=100L)
    val battle=BattleRecord("battle1",League.GREAT,BattleOutcome.WIN,3_000L,ownTeamId="team1",ownPokemon=listOf(
        OwnBattlePokemon("a1",BattleSlot.LEAD),OwnBattlePokemon("a2",BattleSlot.SWITCH),OwnBattlePokemon("b1",BattleSlot.CLOSER)
    ),opponentPokemon=listOf(OpponentBattlePokemon("beta",BattleSlot.LEAD)),notes="ok",tags=setOf("set1"))
    val snapshot=CollectionBackupSnapshot(collection,plans,listOf(team),listOf(battle),4_000L,"1.28")
    val envelope=CollectionBackupCodec.encode(snapshot)
    val text=CollectionBackupCodec.serialize(envelope)
    val decodedEnvelope=CollectionBackupCodec.deserialize(text)
    val restored=CollectionBackupCodec.decode(decodedEnvelope)
    checkThat(restored==snapshot,"Backup roundtrip mismatch")
    val tampered=text.replace("PVPGO_BACKUP","PVPGO_BACKUP",ignoreCase=false).dropLast(1)+"X"
    checkThat(runCatching{CollectionBackupCodec.deserialize(tampered)}.isFailure,"Tampered backup must be refused")

    println("COLLECTION_RECONCILIATION_F71_V1_29_OK matches=${exact.matches.size}")
    println("COLLECTION_FRESHNESS_F72_V1_29_OK")
    println("PVP_BOX_F73_V1_29_OK entries=${box.size}")
    println("CANDIDATE_INBOX_F74_V1_29_OK")
    println("DUPLICATE_RESOLVER_F75_V1_29_OK groups=${duplicateReports.size}")
    println("TRANSFER_SAFETY_F76_V1_29_OK")
    println("SEARCH_STRING_F77_V1_29_OK query=${speciesSearch.query}")
    println("BACKUP_RESTORE_F78_V1_29_OK sha=${envelope.payloadSha256.take(12)}")
}
