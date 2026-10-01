import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.CollectionImportService
import com.rui.pvpgo.engine.IvSpread

fun main() {
    val now = 1_800_000_000_000L
    val collection = listOf(
        OwnedPokemon(
            id="owned-exact", speciesId="charizard", iv=IvSpread(0,15,13), cp=1500, level=19.5,
            createdAtEpochMs=now-1000, externalSource="pvp-export", externalId="go-001"
        ),
        OwnedPokemon(
            id="owned-strong", speciesId="lanturn", iv=IvSpread(0,13,14), cp=1498, level=27.0,
            createdAtEpochMs=now-1000
        )
    )

    val csv = """externalId,speciesId,ivAttack,ivDefense,ivStamina,cp,level,nickname,isShadow,fastMoveId,chargedMoveIds,tags
"go-001",charizard,0,15,13,1500,19.5,"Zard, GL",false,WING_ATTACK,"BLAST_BURN|DRAGON_CLAW","pvp|ready"
,lanturn,0,13,14,1498,27.0,Lanturn,false,SPARK,"SURF|THUNDERBOLT",candidate
,newmon,1,2,3,1400,20.0,New,false,FAST,"CHARGED",candidate
,badmon,16,2,3,1200,20.0,Bad,false,FAST,CHARGED,candidate
""".trimIndent()

    val batch = CollectionImportService.parseCsv(csv, "pvp-export", now)
    check(batch.inputRowCount == 4)
    check(batch.acceptedCount == 3)
    check(batch.issues.any { it.rowNumber == 5 && it.field == "ivAttack" })
    check(batch.candidates.first().candidate.nickname == "Zard, GL")
    check(batch.candidates.first().candidate.verificationSource == CollectionVerificationSource.IMPORT)
    check(batch.candidates.all { it.candidate.lastVerifiedAtEpochMs == now })

    val reconciled = CollectionImportService.reconcile(batch, collection)
    check(reconciled.reconciled[0].reconciliation.action == ReconciliationAction.MERGE_RECOMMENDED)
    check(reconciled.reconciled[0].reconciliation.matches.first().kind == ReconciliationMatchKind.EXACT_EXTERNAL_ID)
    check(reconciled.reconciled[1].reconciliation.action == ReconciliationAction.REVIEW_EXISTING)
    check(reconciled.reconciled[1].reconciliation.matches.any { it.existingOwnedPokemonId == "owned-strong" })
    check(reconciled.reconciled[2].reconciliation.action == ReconciliationAction.CREATE_NEW)
    check(reconciled.reconciled.all { it.reconciliation.requiresHumanConfirmation })

    val inbox = CollectionImportService.toInboxItems(reconciled)
    check(inbox.size == 3 && inbox.all { it.status == CandidateInboxStatus.PENDING })

    val json = """[
      {"externalId":"go-001","speciesId":"charizard","ivAttack":0,"ivDefense":15,"ivStamina":13,"cp":1500,"level":19.5,"chargedMoveIds":["BLAST_BURN","DRAGON_CLAW"],"tags":["pvp","ready"]},
      {"speciesId":"newmon","ivAttack":1,"ivDefense":2,"ivStamina":3,"isShiny":true}
    ]""".trimIndent()
    val jsonBatch = CollectionImportService.parseJson(json, "pvp-export", now)
    check(jsonBatch.acceptedCount == 2)
    check(jsonBatch.candidates[0].candidate.chargedMoveIds.size == 2)
    check(jsonBatch.candidates[1].candidate.isShiny)
    check(OwnedPokemonField.CP in jsonBatch.candidates[1].candidate.uncertainFields)
    check(OwnedPokemonField.LEVEL in jsonBatch.candidates[1].candidate.uncertainFields)

    val duplicate = CollectionImportService.parseJson(
        """[{"externalId":"x","speciesId":"a","ivAttack":1,"ivDefense":1,"ivStamina":1},{"externalId":"x","speciesId":"b","ivAttack":2,"ivDefense":2,"ivStamina":2}]""",
        "dup", now
    )
    check(duplicate.acceptedCount == 1)
    check(duplicate.issues.any { it.code == CollectionImportIssueCode.DUPLICATE_EXTERNAL_ID_IN_BATCH })

    println("COLLECTION_IMPORT_F18_V1_29_OK csvAccepted=${batch.acceptedCount} csvRejected=${batch.rejectedCount} jsonAccepted=${jsonBatch.acceptedCount}")
    println("reconciliation=${reconciled.reconciled.map { it.reconciliation.action }} inboxPending=${inbox.size}")
}
