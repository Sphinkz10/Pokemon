package com.rui.pvpgo

import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.CollectionImportModes

data class ImportModeCardUi(
    val mode: CollectionImportMode,
    val title: String,
    val description: String,
    val badge: String? = null,
    val enabled: Boolean = true,
    val recommended: Boolean = false
)

data class ImportHomeUiState(
    val title: String = "Adicionar à Collection",
    val subtitle: String = "Escolhe o método mais simples para o que tens agora.",
    val cards: List<ImportModeCardUi>,
    val pendingCount: Int,
    val safetyNote: String = "Nada é criado ou fundido sem revisão e confirmação."
)

data class ImportProgressUiState(
    val currentStep: CollectionImportStep,
    val stepNumber: Int,
    val totalSteps: Int,
    val title: String,
    val helper: String,
    val primaryAction: String,
    val secondaryAction: String? = null,
    val canContinue: Boolean,
    val canSaveForLater: Boolean
)

object CollectionImportExperience {
    fun home(context: CollectionImportEntryContext): ImportHomeUiState {
        val recommendation = CollectionImportModes.recommend(context)
        val cards = CollectionImportModes.descriptors.map { descriptor ->
            ImportModeCardUi(
                mode = descriptor.mode,
                title = descriptor.title,
                description = descriptor.shortDescription,
                badge = when {
                    descriptor.mode == recommendation.recommended -> "Recomendado"
                    descriptor.mode == CollectionImportMode.REVIEW_INBOX && context.pendingInboxCount > 0 -> "${context.pendingInboxCount} pendente(s)"
                    descriptor.mode == CollectionImportMode.SCREENSHOT_OCR && !context.ocrRuntimeAvailable -> "No telemóvel"
                    else -> null
                },
                enabled = descriptor.availableWithoutAndroidRuntime || context.ocrRuntimeAvailable,
                recommended = descriptor.mode == recommendation.recommended
            )
        }
        return ImportHomeUiState(cards = cards, pendingCount = context.pendingInboxCount)
    }

    fun progress(session: CollectionImportSession): ImportProgressUiState {
        val ordered = when (session.mode) {
            CollectionImportMode.QUICK_MANUAL -> listOf(CollectionImportStep.IDENTITY, CollectionImportStep.REVIEW, CollectionImportStep.RECONCILE)
            CollectionImportMode.GUIDED -> listOf(CollectionImportStep.IDENTITY, CollectionImportStep.PVP_DETAILS, CollectionImportStep.EXTRAS, CollectionImportStep.REVIEW, CollectionImportStep.RECONCILE)
            CollectionImportMode.BULK_FILE, CollectionImportMode.PASTE, CollectionImportMode.SCREENSHOT_OCR -> listOf(CollectionImportStep.REVIEW, CollectionImportStep.RECONCILE)
            CollectionImportMode.REVIEW_INBOX -> listOf(CollectionImportStep.RECONCILE)
        }
        val idx = ordered.indexOf(session.step).let { if (it < 0) ordered.lastIndex else it }
        val (title, helper) = copyFor(session.step)
        val canContinue = when (session.step) {
            CollectionImportStep.IDENTITY -> session.draft?.hasEssentialIdentity == true
            CollectionImportStep.COMPLETE -> false
            else -> true
        }
        return ImportProgressUiState(
            currentStep = session.step,
            stepNumber = idx + 1,
            totalSteps = ordered.size,
            title = title,
            helper = helper,
            primaryAction = if (session.step == CollectionImportStep.RECONCILE) "Rever e confirmar" else "Continuar",
            secondaryAction = when {
                session.mode == CollectionImportMode.GUIDED && session.step in setOf(CollectionImportStep.PVP_DETAILS, CollectionImportStep.EXTRAS) -> "Saltar por agora"
                session.step == CollectionImportStep.REVIEW -> "Voltar e editar"
                else -> null
            },
            canContinue = canContinue,
            canSaveForLater = session.mode == CollectionImportMode.GUIDED && session.step != CollectionImportStep.COMPLETE
        )
    }

    private fun copyFor(step: CollectionImportStep): Pair<String, String> = when (step) {
        CollectionImportStep.CHOOSE_MODE -> "Como queres importar?" to "Podes mudar de método a qualquer momento."
        CollectionImportStep.IDENTITY -> "Identificação" to "Espécie e IVs são o mínimo necessário."
        CollectionImportStep.PVP_DETAILS -> "Detalhes PvP" to "CP, nível e moves ajudam a análise, mas podes completar depois."
        CollectionImportStep.EXTRAS -> "Extras" to "Tags e estados especiais são opcionais."
        CollectionImportStep.REVIEW -> "Confirma os dados" to "Vê o que foi reconhecido, o que falta e o que será marcado como incerto."
        CollectionImportStep.RECONCILE -> "Evitar duplicados" to "Comparamos com a tua Collection antes de criar ou fundir qualquer item."
        CollectionImportStep.COMPLETE -> "Importação preparada" to "As alterações confirmadas ficam prontas para persistência."
    }
}
