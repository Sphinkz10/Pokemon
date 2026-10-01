package com.rui.pvpgo.live

/**
 * User-entered, ephemeral observation notes. NOT an engine LiveBattleEvent stream.
 * In particular, these observations are never presented as camera detections,
 * trusted live recommendations or an automatically saved BattleRecord.
 */
enum class ManualCompanionEvent(val label: String) {
    OPPONENT_SHIELD("Rival usou shield"),
    OPPONENT_SWAP("Rival trocou"),
    OPPONENT_CHARGED("Rival lançou carregado"),
    OWN_SHIELD("Usei shield"),
    OPPONENT_KO("Rival perdeu um Pokémon")
}

data class ManualCompanionNote(val kind: ManualCompanionEvent, val atEpochMs: Long)

data class CompanionQuickLog(
    val sessionId: String? = null,
    val notes: List<ManualCompanionNote> = emptyList()
) {
    val opponentShieldsNoted: Int get() = notes.count { it.kind == ManualCompanionEvent.OPPONENT_SHIELD }
    val ownShieldsNoted: Int get() = notes.count { it.kind == ManualCompanionEvent.OWN_SHIELD }
    val opponentKoNoted: Int get() = notes.count { it.kind == ManualCompanionEvent.OPPONENT_KO }

    /** A new MediaProjection session must not inherit notes from a previous session. */
    fun forSession(nextSessionId: String?): CompanionQuickLog =
        if (nextSessionId == sessionId) this else CompanionQuickLog(sessionId = nextSessionId)

    fun append(kind: ManualCompanionEvent, atEpochMs: Long): CompanionQuickLog {
        require(atEpochMs >= 0) { "Epoch timestamp must be nonnegative" }
        // Never allow manual counters to imply more shields or knock-outs than possible.
        if (kind == ManualCompanionEvent.OPPONENT_SHIELD && opponentShieldsNoted >= 2) return this
        if (kind == ManualCompanionEvent.OWN_SHIELD && ownShieldsNoted >= 2) return this
        if (kind == ManualCompanionEvent.OPPONENT_KO && opponentKoNoted >= 3) return this
        return copy(notes = (notes + ManualCompanionNote(kind, atEpochMs)).takeLast(80))
    }

    fun undo(): CompanionQuickLog = if (notes.isEmpty()) this else copy(notes = notes.dropLast(1))
    fun clear(): CompanionQuickLog = copy(notes = emptyList())
}

data class CompanionCapturePresentation(
    val title: String,
    val detail: String,
    val captureActive: Boolean,
    val canStart: Boolean,
    val canStop: Boolean,
    val attention: Boolean
)

/** Only reports capture telemetry, never claims semantic battle detection. */
object CompanionCapturePolicy {
    fun present(status: String, frameCount: Long, error: String? = null): CompanionCapturePresentation {
        return when (status) {
            "STARTING" -> CompanionCapturePresentation(
                "A iniciar captura", "A aguardar a autorização e os primeiros frames do Android.",
                false, false, true, false
            )
            "CAPTURING" -> CompanionCapturePresentation(
                if (frameCount > 0) "Captura em curso" else "Captura iniciada",
                if (frameCount > 0) "Frames recebidos. O reconhecimento de batalhas ainda não está disponível."
                else "À espera de frames. Ainda não há identificação automática de Pokémon.",
                true, false, true, frameCount == 0L
            )
            "STOPPING" -> CompanionCapturePresentation("A parar", "A terminar a sessão de captura.", false, false, false, false)
            "ERROR" -> CompanionCapturePresentation(
                "Falha na captura", error?.takeIf { it.isNotBlank() } ?: "O Android interrompeu a captura.",
                false, true, false, true
            )
            "STOPPED" -> CompanionCapturePresentation("Captura terminada", "Podes começar outra sessão com nova autorização.", false, true, false, false)
            else -> CompanionCapturePresentation("Pronto para observar", "Autoriza a captura quando quiseres. Sem deteção automática nesta versão.", false, true, false, false)
        }
    }
}
