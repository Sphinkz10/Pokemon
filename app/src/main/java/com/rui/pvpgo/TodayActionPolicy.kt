package com.rui.pvpgo

/**
 * The Today screen presents *next steps*, not competitive predictions.
 * Inputs are observed local Room counts; no Pokémon GO account is read.
 *
 * This pure policy deliberately does not interpret IVs as PvP eligibility.
 * A completed level/CP record is still subject to league and move checks.
 */
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
        calendarStale: Boolean
    ): List<TodayAction> {
        require(ownedCount >= 0 && pendingCount >= 0 && missingCpOrLevelCount >= 0)
        require(savedTeamCount >= 0 && missingCpOrLevelCount <= ownedCount)

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
}
