import com.rui.pvpgo.*
import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.CollectionImportModes

fun main() {
    val home = CollectionImportExperience.home(CollectionImportEntryContext(pendingInboxCount = 2))
    check(home.cards.size == 6)
    check(home.cards.single { it.recommended }.mode == CollectionImportMode.REVIEW_INBOX)
    check(home.cards.first { it.mode == CollectionImportMode.SCREENSHOT_OCR }.enabled == false)
    check(home.safetyNote.contains("confirmação"))

    val g0 = CollectionImportModes.start(CollectionImportMode.GUIDED, "ux")
    val p0 = CollectionImportExperience.progress(g0)
    check(p0.stepNumber == 1 && p0.totalSteps == 5)
    check(!p0.canContinue)

    val g1 = CollectionImportModes.withDraft(g0, CollectionImportDraft("ux", CollectionImportMode.GUIDED, "medicham", 5, 15, 15))
    val p1 = CollectionImportExperience.progress(g1)
    check(p1.canContinue)
    val g2 = CollectionImportModes.next(g1)
    val p2 = CollectionImportExperience.progress(g2)
    check(p2.secondaryAction == "Saltar por agora")
    check(p2.canSaveForLater)

    println("COLLECTION_IMPORT_EXPERIENCE_V1_29_OK cards=${home.cards.size} guidedSteps=${p0.totalSteps} safety=true")
}
