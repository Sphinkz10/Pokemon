import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.*

fun main() {
    val modes = CollectionImportModes.descriptors
    check(modes.size == 6)
    check(modes.map { it.mode }.toSet() == CollectionImportMode.entries.toSet())
    check(modes.first { it.mode == CollectionImportMode.SCREENSHOT_OCR }.requiresDeviceCapture)
    check(!modes.first { it.mode == CollectionImportMode.SCREENSHOT_OCR }.availableWithoutAndroidRuntime)

    val defaultRec = CollectionImportModes.recommend(CollectionImportEntryContext())
    check(defaultRec.recommended == CollectionImportMode.QUICK_MANUAL)
    val inboxRec = CollectionImportModes.recommend(CollectionImportEntryContext(pendingInboxCount = 3))
    check(inboxRec.recommended == CollectionImportMode.REVIEW_INBOX)
    val pasteRec = CollectionImportModes.recommend(CollectionImportEntryContext(clipboardText = "speciesId,ivAttack,ivDefense,ivStamina"))
    check(pasteRec.recommended == CollectionImportMode.PASTE)
    val bulkRec = CollectionImportModes.recommend(CollectionImportEntryContext(wantsManyPokemon = true))
    check(bulkRec.recommended == CollectionImportMode.BULK_FILE)
    val ocrRec = CollectionImportModes.recommend(CollectionImportEntryContext(screenshotReady = true, ocrRuntimeAvailable = true))
    check(ocrRec.recommended == CollectionImportMode.SCREENSHOT_OCR)

    val q0 = CollectionImportModes.start(CollectionImportMode.QUICK_MANUAL, "q1")
    check(q0.step == CollectionImportStep.IDENTITY)
    val qDraft = CollectionImportDraft("q1", CollectionImportMode.QUICK_MANUAL, "charizard", 0, 15, 13)
    val q1 = CollectionImportModes.withDraft(q0, qDraft)
    val q2 = CollectionImportModes.next(q1)
    check(q2.step == CollectionImportStep.REVIEW)
    val quickBatch = CollectionImportModes.manualBatch(qDraft, 1000L)
    check(quickBatch.format == CollectionImportFormat.MANUAL)
    check(quickBatch.acceptedCount == 1)
    check(quickBatch.candidates.single().candidate.uncertainFields.containsAll(setOf(OwnedPokemonField.CP, OwnedPokemonField.LEVEL, OwnedPokemonField.FAST_MOVE, OwnedPokemonField.CHARGED_MOVES)))

    val g0 = CollectionImportModes.start(CollectionImportMode.GUIDED, "g1")
    val gDraft = CollectionImportDraft("g1", CollectionImportMode.GUIDED, "azumarill", 0, 15, 15)
    val g1 = CollectionImportModes.withDraft(g0, gDraft)
    val g2 = CollectionImportModes.next(g1)
    check(g2.step == CollectionImportStep.PVP_DETAILS)
    val g3 = CollectionImportModes.next(g2)
    check(g3.step == CollectionImportStep.EXTRAS)
    val saved = CollectionImportModes.saveForLater(g3)
    check(saved.savedForLater)
    val gBack = CollectionImportModes.back(g3)
    check(gBack.step == CollectionImportStep.PVP_DETAILS)

    val jsonPaste = """[{"speciesId":"lanturn","ivAttack":0,"ivDefense":13,"ivStamina":14}]"""
    val jsonBatch = CollectionImportModes.parsePasted(jsonPaste, "clipboard", 2000L)
    check(jsonBatch.format == CollectionImportFormat.JSON && jsonBatch.acceptedCount == 1)

    val csvPaste = "speciesId,ivAttack,ivDefense,ivStamina\nnoctowl,0,15,15"
    val csvBatch = CollectionImportModes.parsePasted(csvPaste, "clipboard", 3000L)
    check(csvBatch.format == CollectionImportFormat.CSV && csvBatch.acceptedCount == 1)

    val reconciled = CollectionImportService.reconcile(quickBatch, emptyList())
    check(reconciled.reconciled.single().reconciliation.requiresHumanConfirmation)
    val inbox = CollectionImportService.toInboxItems(reconciled)
    val summary = CollectionImportModes.inboxSummary(inbox)
    check(summary[CandidateInboxStatus.PENDING] == 1)

    println("COLLECTION_IMPORT_MODES_F18B_V1_29_OK modes=${modes.size} quick=${quickBatch.acceptedCount} guidedSave=${saved.savedForLater} pasteJson=${jsonBatch.acceptedCount} pasteCsv=${csvBatch.acceptedCount}")
}
