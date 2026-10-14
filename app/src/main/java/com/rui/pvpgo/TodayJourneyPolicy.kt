package com.rui.pvpgo

/**
 * A three-stage, deliberately manual journey. No external Pokémon GO account
 * is read; copying a string is not equivalent to importing a Pokémon.
 * Progress describes user actions, NOT verified PvP eligibility.
 */
enum class TodayJourneyStage(val index: Int) {
    COPY_FILTER(1),
    REVIEW_CANDIDATES(2),
    NEXT_ACTION(3);

    companion object {
        fun fromStored(value: String?): TodayJourneyStage =
            entries.firstOrNull { it.name == value } ?: COPY_FILTER
    }
}

data class GoSearchSuggestion(
    val filter: String,
    val purpose: String,
    val limitation: String,
    val requiresEnglishGoTerms: Boolean
)

object TodayJourneyPolicy {
    // Conservative, copyable Pokémon GO inventory search expressions.
    // These collect possible candidates; none proves PvP rank or transfer safety.
    fun suggestion(goal: TodayGoal): GoSearchSuggestion = when (goal) {
        TodayGoal.GREAT, TodayGoal.ULTRA -> GoSearchSuggestion(
            filter = "0-1attack&3-4defense&3-4hp",
            purpose = "Encontrar alguns IVs típicos de ligas com limite de CP.",
            limitation = "Não encontra todos os bons exemplares; espécies e formas mudam o IV ideal. " +
                "Não transferir Pokémon excluídos pelo filtro.",
            requiresEnglishGoTerms = true
        )
        TodayGoal.MASTER -> GoSearchSuggestion(
            filter = "4*",
            purpose = "Ver primeiro os exemplares com avaliação perfeita.",
            limitation = "Exclui bons candidatos com IVs inferiores. " +
                "A utilidade depende dos movimentos, espécie e nível; não transferir outros.",
            requiresEnglishGoTerms = false
        )
        TodayGoal.IVS -> GoSearchSuggestion(
            filter = "0-1attack&3-4defense&3-4hp",
            purpose = "Começar pelos IVs com ataque baixo e resistência alta.",
            limitation = "O filtro é apenas uma triagem para PvP com CP limitado; " +
                "não calcula ranking nem substitui avaliar a espécie.",
            requiresEnglishGoTerms = true
        )
        TodayGoal.COLLECTION -> GoSearchSuggestion(
            filter = "3*,4*",
            purpose = "Rever primeiro exemplares com avaliação de três ou quatro estrelas.",
            limitation = "Não mostra todos os raros, shinies ou bons Pokémon PvP. " +
                "Não usar para decidir transferências.",
            requiresEnglishGoTerms = false
        )
        TodayGoal.EVENTS -> GoSearchSuggestion(
            filter = "age0-7",
            purpose = "Rever capturas recentes, independentemente do evento.",
            limitation = "Isto não identifica participações num evento nem confirma bónus. " +
                "Consulta sempre o Radar e a fonte do evento.",
            requiresEnglishGoTerms = true
        )
    }

    /**
     * Live, local Room Inbox summary. Null means the first Room emission has
     * not arrived; never display zero until this happens.
     * These pending entries may NOT be the Pokémon displayed in Pokémon GO.
     */
    fun inboxStatus(pendingCount: Int?): String = when {
        pendingCount == null -> "A verificar candidatos pendentes na Companion…"
        pendingCount < 0 -> throw IllegalArgumentException("Invalid Inbox count")
        pendingCount == 0 ->
            "0 candidatos pendentes na Companion. Pode haver Pokémon por rever no jogo."
        pendingCount == 1 ->
            "1 candidato pendente na Companion. Confirma o exemplar na Inbox."
        else ->
            "${pendingCount} candidatos pendentes na Companion. Revê-os na Inbox."
    }

    fun hasPendingInboxReview(pendingCount: Int?): Boolean = pendingCount != null && pendingCount > 0

    /** Only an explicit user action advances the journey. */
    fun afterCopy(stage: TodayJourneyStage): TodayJourneyStage =
        if (stage == TodayJourneyStage.COPY_FILTER) TodayJourneyStage.REVIEW_CANDIDATES else stage

    /** Marked by the player after reviewing; no automatic approval of Pokémon. */
    fun afterUserReview(stage: TodayJourneyStage): TodayJourneyStage =
        if (stage == TodayJourneyStage.REVIEW_CANDIDATES) TodayJourneyStage.NEXT_ACTION else stage

    fun reset(): TodayJourneyStage = TodayJourneyStage.COPY_FILTER
}
