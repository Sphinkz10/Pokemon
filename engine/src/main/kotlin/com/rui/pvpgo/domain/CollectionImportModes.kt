package com.rui.pvpgo.domain

enum class CollectionImportMode {
    QUICK_MANUAL,
    GUIDED,
    BULK_FILE,
    PASTE,
    SCREENSHOT_OCR,
    REVIEW_INBOX
}

enum class CollectionImportStep {
    CHOOSE_MODE,
    IDENTITY,
    PVP_DETAILS,
    EXTRAS,
    REVIEW,
    RECONCILE,
    COMPLETE
}

data class CollectionImportModeDescriptor(
    val mode: CollectionImportMode,
    val title: String,
    val shortDescription: String,
    val batchCapable: Boolean,
    val allowsPartialData: Boolean,
    val requiresDeviceCapture: Boolean = false,
    val availableWithoutAndroidRuntime: Boolean = true
) {
    init {
        require(title.isNotBlank())
        require(shortDescription.isNotBlank())
    }
}

data class CollectionImportEntryContext(
    val pendingInboxCount: Int = 0,
    val clipboardText: String? = null,
    val wantsManyPokemon: Boolean = false,
    val screenshotReady: Boolean = false,
    val ocrRuntimeAvailable: Boolean = false
) {
    init { require(pendingInboxCount >= 0) }
}

data class CollectionImportDraft(
    val sessionId: String,
    val mode: CollectionImportMode,
    val speciesId: String? = null,
    val ivAttack: Int? = null,
    val ivDefense: Int? = null,
    val ivStamina: Int? = null,
    val cp: Int? = null,
    val level: Double? = null,
    val nickname: String? = null,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isShiny: Boolean = false,
    val isLucky: Boolean = false,
    val isBestBuddy: Boolean = false,
    val isFavorite: Boolean = false,
    val fastMoveId: String? = null,
    val chargedMoveIds: List<String> = emptyList(),
    val secondChargedMoveUnlocked: Boolean? = null,
    val tags: Set<String> = emptySet(),
    val externalSource: String? = null,
    val externalId: String? = null
) {
    init {
        require(sessionId.isNotBlank())
        require(ivAttack == null || ivAttack in 0..15)
        require(ivDefense == null || ivDefense in 0..15)
        require(ivStamina == null || ivStamina in 0..15)
        require(cp == null || cp > 0)
        require(level == null || (level in 1.0..51.0 && level * 2 == (level * 2).toInt().toDouble()))
        require(chargedMoveIds.size <= 2)
        require(!(isShadow && isPurified))
    }

    val hasEssentialIdentity: Boolean
        get() = !speciesId.isNullOrBlank() && ivAttack != null && ivDefense != null && ivStamina != null

    val optionalPvpComplete: Boolean
        get() = cp != null && level != null && !fastMoveId.isNullOrBlank() && chargedMoveIds.isNotEmpty()
}

data class CollectionImportSession(
    val id: String,
    val mode: CollectionImportMode,
    val step: CollectionImportStep,
    val draft: CollectionImportDraft? = null,
    val completedSteps: Set<CollectionImportStep> = emptySet(),
    val savedForLater: Boolean = false
) {
    init {
        require(id.isNotBlank())
        require(draft == null || draft.sessionId == id)
        require(draft == null || draft.mode == mode)
    }
}

data class CollectionImportModeRecommendation(
    val recommended: CollectionImportMode,
    val reason: String,
    val alternatives: List<CollectionImportMode>
) {
    init {
        require(reason.isNotBlank())
        require(recommended !in alternatives)
        require(alternatives.distinct().size == alternatives.size)
    }
}
