package com.rui.pvpgo

/**
 * The Today screen presents *next steps*, not competitive predictions.
 * Inputs are observed local Room counts; no Pokémon GO account is read.
 *
 * This pure policy deliberately does not interpret IVs as PvP eligibility.
 * A completed level/CP record is still subject to league and move checks.
 */
enum class TodayGoal(val label: String, val shortLabel: String) {
    GREAT("Grande Liga", "Grande"),
    ULTRA("Ultra Liga", "Ultra"),
    COLLECTION("Coleção", "Coleção"),
    EVENTS("Eventos", "Eventos");

    companion object {
        /** Unknown or removed saved values safely fall back to Collection. */
        fun fromStored(value: String?): TodayGoal =
            entries.firstOrNull { it.name == value } ?: COLLECTION
    }
}

enum class TodayDestination { COLLECTION, TEAMS, RADAR }

data class TodayAction(
    val title: String,
    val explanation: String,
    val destination: TodayDestination
)

object TodayActionPolicy {
    fun decide(
        ownedCount: Int,
        pendingCount: Int,
        missingCpOrLevelCount: Int,
        savedTeamCount: Int,
        calendarAvailable: Boolean,
        calendarStale: Boolean,
        goal: TodayGoal = TodayGoal.COLLECTION
    ): List<TodayAction> {
        require(ownedCount >= 0 && pendingCount >= 0 && missingCpOrLevelCount >= 0)
        require(savedTeamCount >= 0 && missingCpOrLevelCount <= ownedCount)

        if (goal != TodayGoal.COLLECTION) {
            return goalFirstActions(
                goal, ownedCount, pendingCount, missingCpOrLevelCount,
                savedTeamCount, calendarAvailable, calendarStale
            )
        }

        val result = mutableListOf<TodayAction>()
        if (pendingCount > 0) {
            result += TodayAction(
                title = "Rever $pendingCount candidato(s)",
                explanation = "Capturas por confirmar antes de entrarem na coleção.",
                destination = TodayDestination.COLLECTION
            )
        }

        when {
            ownedCount == 0 -> result += TodayAction(
                title = "Começar a tua coleção",
                explanation = "Regista o primeiro exemplar ou importa candidatos.",
                destination = TodayDestination.COLLECTION
            )
            missingCpOrLevelCount > 0 -> result += TodayAction(
                title = "Completar $missingCpOrLevelCount exemplar(es)",
                explanation = "Confirma CP e nível antes de avaliar uma equipa.",
                destination = TodayDestination.COLLECTION
            )
            ownedCount < 3 -> result += TodayAction(
                title = "Completar um trio",
                explanation = "Tens $ownedCount exemplar(es) registados; escolhe os restantes.",
                destination = TodayDestination.COLLECTION
            )
        }

        if (ownedCount >= 3) {
            result += TodayAction(
                title = if (savedTeamCount == 0) "Construir uma equipa" else "Rever as tuas equipas",
                explanation = if (savedTeamCount == 0)
                    "Organiza três exemplares; a elegibilidade PvP ainda terá de ser confirmada."
                else
                    "$savedTeamCount equipa(s) guardadas; confirma as regras da liga antes de combater.",
                destination = TodayDestination.TEAMS
            )
        }

        result += when {
            calendarAvailable && !calendarStale -> TodayAction(
                title = "Consultar próximos eventos",
                explanation = "Vê as datas e confirma a fonte antes de planear.",
                destination = TodayDestination.RADAR
            )
            else -> TodayAction(
                title = "Ver o Radar",
                explanation = "Calendário por atualizar; não assumir datas ou bónus.",
                destination = TodayDestination.RADAR
            )
        }

        return result.take(3)
    }
    /**
     * Goal-first path. A saved team is not assumed to belong to the selected
     * league; the existing Teams screen must still ask the player to select it.
     */
    private fun goalFirstActions(
        goal: TodayGoal,
        ownedCount: Int,
        pendingCount: Int,
        missingCpOrLevelCount: Int,
        savedTeamCount: Int,
        calendarAvailable: Boolean,
        calendarStale: Boolean
    ): List<TodayAction> {
        val result = mutableListOf<TodayAction>()
        val radar = if (calendarAvailable && !calendarStale) {
            TodayAction(
                "Consultar próximos eventos",
                "Consulta o calendário e confirma a fonte e os horários.",
                TodayDestination.RADAR
            )
        } else {
            TodayAction(
                "Ver o Radar",
                "Informação de eventos por confirmar; não assumir datas nem bónus.",
                TodayDestination.RADAR
            )
        }
        if (goal == TodayGoal.EVENTS) result += radar

        if (goal != TodayGoal.EVENTS) {
            result += when {
                ownedCount == 0 -> TodayAction(
                    "Começar a " + goal.label,
                    "Regista exemplares para preparar este objetivo; ainda não há elegibilidade confirmada.",
                    TodayDestination.COLLECTION
                )
                missingCpOrLevelCount > 0 -> TodayAction(
                    goal.label + ": verificar " + missingCpOrLevelCount + " exemplar(es)",
                    "Confirma CP e nível antes de comparar IVs ou investir recursos.",
                    TodayDestination.COLLECTION
                )
                ownedCount < 3 -> TodayAction(
                    goal.label + ": reunir três exemplares",
                    "Faltam exemplares registados; nenhuma viabilidade PvP foi avaliada.",
                    TodayDestination.COLLECTION
                )
                else -> TodayAction(
                    goal.label + ": preparar a equipa",
                    if (savedTeamCount > 0)
                        "Abre Equipas, seleciona " + goal.label + " e revê as regras e os membros."
                    else
                        "Abre Equipas e seleciona " + goal.label + "; a elegibilidade ainda requer validação.",
                    TodayDestination.TEAMS
                )
            }
        }

        if (pendingCount > 0) {
            result += TodayAction(
                "Rever " + pendingCount + " candidato(s)",
                "Confirma as capturas antes de as adicionares à coleção.",
                TodayDestination.COLLECTION
            )
        }

        if (goal == TodayGoal.EVENTS) {
            if (ownedCount == 0) {
                result += TodayAction(
                    "Preparar a coleção",
                    "Regista os exemplares antes de planear capturas e investimentos.",
                    TodayDestination.COLLECTION
                )
            } else if (missingCpOrLevelCount > 0) {
                result += TodayAction(
                    "Confirmar " + missingCpOrLevelCount + " exemplar(es)",
                    "Dados incompletos para decisões de investimento; abre a Coleção.",
                    TodayDestination.COLLECTION
                )
            }
        } else {
            result += radar
        }

        return result.take(3)
    }

}
