package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.*

object CollectionImportModes {
    val descriptors: List<CollectionImportModeDescriptor> = listOf(
        CollectionImportModeDescriptor(
            CollectionImportMode.QUICK_MANUAL,
            "Rápido",
            "Adicionar um Pokémon com espécie + IVs e completar o resto mais tarde.",
            batchCapable = false,
            allowsPartialData = true
        ),
        CollectionImportModeDescriptor(
            CollectionImportMode.GUIDED,
            "Guiado",
            "Fluxo faseado: Identidade → PvP → Extras → Confirmar, com campos opcionais ignoráveis.",
            batchCapable = false,
            allowsPartialData = true
        ),
        CollectionImportModeDescriptor(
            CollectionImportMode.BULK_FILE,
            "Em lote",
            "Importar CSV/JSON, validar tudo primeiro e rever erros antes de criar ou fundir.",
            batchCapable = true,
            allowsPartialData = true
        ),
        CollectionImportModeDescriptor(
            CollectionImportMode.PASTE,
            "Colar",
            "Colar CSV/JSON do clipboard; o formato é detetado automaticamente.",
            batchCapable = true,
            allowsPartialData = true
        ),
        CollectionImportModeDescriptor(
            CollectionImportMode.SCREENSHOT_OCR,
            "Screenshot / OCR",
            "Capturar appraisal/moves e enviar o resultado para revisão com confidence explícita.",
            batchCapable = false,
            allowsPartialData = true,
            requiresDeviceCapture = true,
            availableWithoutAndroidRuntime = false
        ),
        CollectionImportModeDescriptor(
            CollectionImportMode.REVIEW_INBOX,
            "Rever pendentes",
            "Resolver duplicados, campos incertos e candidatos guardados antes de alterar a Collection.",
            batchCapable = true,
            allowsPartialData = true
        )
    )

    fun recommend(context: CollectionImportEntryContext): CollectionImportModeRecommendation {
        val recommended = when {
            context.pendingInboxCount > 0 -> CollectionImportMode.REVIEW_INBOX
            !context.clipboardText.isNullOrBlank() -> CollectionImportMode.PASTE
            context.wantsManyPokemon -> CollectionImportMode.BULK_FILE
            context.screenshotReady && context.ocrRuntimeAvailable -> CollectionImportMode.SCREENSHOT_OCR
            else -> CollectionImportMode.QUICK_MANUAL
        }
        val reason = when (recommended) {
            CollectionImportMode.REVIEW_INBOX -> "Há ${context.pendingInboxCount} candidato(s) pendente(s) para rever."
            CollectionImportMode.PASTE -> "Foi detetado conteúdo no clipboard que pode ser validado antes de importar."
            CollectionImportMode.BULK_FILE -> "Foi pedido um fluxo para vários Pokémon de uma vez."
            CollectionImportMode.SCREENSHOT_OCR -> "Há uma captura pronta e o runtime OCR está disponível."
            CollectionImportMode.QUICK_MANUAL -> "É o caminho com menos campos obrigatórios para adicionar um Pokémon."
            CollectionImportMode.GUIDED -> "O modo guiado está disponível como alternativa para preencher mais detalhes."
        }
        val alternatives = descriptors.map { it.mode }.filter { it != recommended }
        return CollectionImportModeRecommendation(recommended, reason, alternatives)
    }

    fun start(mode: CollectionImportMode, sessionId: String): CollectionImportSession {
        require(sessionId.isNotBlank())
        val draft = when (mode) {
            CollectionImportMode.QUICK_MANUAL, CollectionImportMode.GUIDED -> CollectionImportDraft(sessionId, mode)
            else -> null
        }
        val step = when (mode) {
            CollectionImportMode.QUICK_MANUAL, CollectionImportMode.GUIDED -> CollectionImportStep.IDENTITY
            CollectionImportMode.BULK_FILE, CollectionImportMode.PASTE, CollectionImportMode.SCREENSHOT_OCR -> CollectionImportStep.REVIEW
            CollectionImportMode.REVIEW_INBOX -> CollectionImportStep.RECONCILE
        }
        return CollectionImportSession(sessionId, mode, step, draft)
    }

    fun next(session: CollectionImportSession): CollectionImportSession {
        val step = when (session.mode) {
            CollectionImportMode.QUICK_MANUAL -> when (session.step) {
                CollectionImportStep.IDENTITY -> {
                    require(session.draft?.hasEssentialIdentity == true) { "Quick mode needs species + IVs before review" }
                    CollectionImportStep.REVIEW
                }
                CollectionImportStep.REVIEW -> CollectionImportStep.RECONCILE
                CollectionImportStep.RECONCILE -> CollectionImportStep.COMPLETE
                else -> session.step
            }
            CollectionImportMode.GUIDED -> when (session.step) {
                CollectionImportStep.IDENTITY -> {
                    require(session.draft?.hasEssentialIdentity == true) { "Guided mode needs species + IVs before PvP details" }
                    CollectionImportStep.PVP_DETAILS
                }
                CollectionImportStep.PVP_DETAILS -> CollectionImportStep.EXTRAS
                CollectionImportStep.EXTRAS -> CollectionImportStep.REVIEW
                CollectionImportStep.REVIEW -> CollectionImportStep.RECONCILE
                CollectionImportStep.RECONCILE -> CollectionImportStep.COMPLETE
                else -> session.step
            }
            else -> when (session.step) {
                CollectionImportStep.REVIEW -> CollectionImportStep.RECONCILE
                CollectionImportStep.RECONCILE -> CollectionImportStep.COMPLETE
                else -> session.step
            }
        }
        return session.copy(step = step, completedSteps = session.completedSteps + session.step, savedForLater = false)
    }

    fun back(session: CollectionImportSession): CollectionImportSession {
        val previous = when (session.mode) {
            CollectionImportMode.QUICK_MANUAL -> when (session.step) {
                CollectionImportStep.REVIEW -> CollectionImportStep.IDENTITY
                CollectionImportStep.RECONCILE -> CollectionImportStep.REVIEW
                else -> session.step
            }
            CollectionImportMode.GUIDED -> when (session.step) {
                CollectionImportStep.PVP_DETAILS -> CollectionImportStep.IDENTITY
                CollectionImportStep.EXTRAS -> CollectionImportStep.PVP_DETAILS
                CollectionImportStep.REVIEW -> CollectionImportStep.EXTRAS
                CollectionImportStep.RECONCILE -> CollectionImportStep.REVIEW
                else -> session.step
            }
            else -> if (session.step == CollectionImportStep.RECONCILE) CollectionImportStep.REVIEW else session.step
        }
        return session.copy(step = previous, savedForLater = false)
    }

    fun saveForLater(session: CollectionImportSession): CollectionImportSession = session.copy(savedForLater = true)

    fun withDraft(session: CollectionImportSession, draft: CollectionImportDraft): CollectionImportSession {
        require(session.mode == draft.mode && session.id == draft.sessionId)
        return session.copy(draft = draft, savedForLater = false)
    }

    fun manualBatch(
        draft: CollectionImportDraft,
        importedAtEpochMs: Long,
        sourceLabel: String = if (draft.mode == CollectionImportMode.QUICK_MANUAL) "manual-quick" else "manual-guided"
    ): CollectionImportBatch {
        require(draft.mode == CollectionImportMode.QUICK_MANUAL || draft.mode == CollectionImportMode.GUIDED)
        return CollectionImportService.parseManual(draft.toImportFields(), sourceLabel, importedAtEpochMs)
    }

    fun parsePasted(text: String, sourceLabel: String, importedAtEpochMs: Long): CollectionImportBatch {
        require(text.isNotBlank())
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            CollectionImportService.parseJson(text, sourceLabel, importedAtEpochMs)
        } else {
            CollectionImportService.parseCsv(text, sourceLabel, importedAtEpochMs)
        }
    }

    fun inboxSummary(items: List<CandidateInboxItem>): Map<CandidateInboxStatus, Int> =
        CandidateInboxStatus.entries.associateWith { status -> items.count { it.status == status } }

    private fun CollectionImportDraft.toImportFields(): Map<String, Any?> = buildMap {
        speciesId?.let { put("speciesId", it) }
        ivAttack?.let { put("ivAttack", it) }
        ivDefense?.let { put("ivDefense", it) }
        ivStamina?.let { put("ivStamina", it) }
        cp?.let { put("cp", it) }
        level?.let { put("level", it) }
        nickname?.let { put("nickname", it) }
        put("isShadow", isShadow)
        put("isPurified", isPurified)
        put("isShiny", isShiny)
        put("isLucky", isLucky)
        put("isBestBuddy", isBestBuddy)
        put("isFavorite", isFavorite)
        fastMoveId?.let { put("fastMoveId", it) }
        if (chargedMoveIds.isNotEmpty()) put("chargedMoveIds", chargedMoveIds)
        secondChargedMoveUnlocked?.let { put("secondChargedMoveUnlocked", it) }
        if (tags.isNotEmpty()) put("tags", tags.toList())
        externalId?.let { put("externalId", it) }
    }
}
