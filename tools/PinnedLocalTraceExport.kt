import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.engine.*
import java.io.File

private fun esc(s: String?): String = if (s == null) "null" else buildString {
    append('"')
    for (c in s) when (c) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> append(c)
    }
    append('"')
}

private fun actorIndex(key: String?, a: BattleBuild, b: BattleBuild): String = when (key) {
    a.key -> "0"
    b.key -> "1"
    else -> "null"
}

private fun finalJson(s: BattleStateSnapshot): String =
    "{\"hp\":${s.hp},\"energy\":${s.energy},\"shields\":${s.shields},\"attackStage\":${s.attackStage},\"defenseStage\":${s.defenseStage}}"

fun main(args: Array<String>) {
    PinnedBattleCaseCatalog.validatePinnedData()
    val outFile = File(args.firstOrNull() ?: "evidence/local-v1.29-pinned-traces.json")
    val cases = PinnedBattleCaseCatalog.baselineCases
    val engine = BattleEngine()
    val json = buildString {
        append("{\n  \"schemaVersion\": 2,\n  \"engineVersion\": \"1.29\",\n")
        append("  \"reference\": {\"commitSha\":${esc(PinnedBattleCaseCatalog.REFERENCE_SHA)},\"siteVersion\":${esc(PinnedBattleCaseCatalog.REFERENCE_VERSION)}},\n")
        append("  \"results\": [\n")
        cases.forEachIndexed { ci, c ->
            val r = engine.simulate(c.a, c.b, c.scenario())
            val winner = when (r.outcomeForA) { BattleOutcome.WIN -> "0"; BattleOutcome.LOSS -> "1"; BattleOutcome.DRAW -> "null" }
            append("    {\n      \"fixtureId\": ${esc(c.fixtureId)}, \"shieldsA\":${c.shieldsA}, \"shieldsB\":${c.shieldsB}, \"battleRatingA\": ${r.battleRatingA}, \"battleRatingB\": ${r.battleRatingB}, \"winner\": $winner, \"turns\": ${r.turns},\n")
            append("      \"final\": {\"a\":${finalJson(r.finalA)},\"b\":${finalJson(r.finalB)}},\n")
            append("      \"timeline\": [\n")
            r.timeline.forEachIndexed { i, e ->
                append("        {\"i\":$i,\"turn\":${e.turn},\"actor\":${actorIndex(e.actorKey, c.a, c.b)},\"actorKey\":${esc(e.actorKey)},\"type\":${esc(e.type.name)},\"moveId\":${esc(e.moveId)},\"damage\":${e.damage ?: "null"},\"energyAfter\":${e.energyAfter ?: "null"},\"shielded\":${e.shielded},\"note\":${esc(e.note)}}")
                if (i != r.timeline.lastIndex) append(',')
                append('\n')
            }
            append("      ],\n      \"decisionTrace\": [\n")
            r.decisionTrace.forEachIndexed { i, e ->
                append("        {\"i\":$i,\"turn\":${e.turn},\"actor\":${actorIndex(e.actorKey, c.a, c.b)},\"actorKey\":${esc(e.actorKey)},\"type\":${esc(e.type.name)},\"actorHp\":${e.actorHp ?: "null"},\"opponentHp\":${e.opponentHp ?: "null"},\"actorEnergy\":${e.actorEnergy ?: "null"},\"opponentEnergy\":${e.opponentEnergy ?: "null"},\"actorShields\":${e.actorShields ?: "null"},\"opponentShields\":${e.opponentShields ?: "null"},\"moveId\":${esc(e.moveId)},\"reason\":${esc(e.reason)}}")
                if (i != r.decisionTrace.lastIndex) append(',')
                append('\n')
            }
            append("      ]\n    }")
            if (ci != cases.lastIndex) append(',')
            append('\n')
        }
        append("  ]\n}\n")
    }
    outFile.parentFile?.mkdirs()
    outFile.writeText(json)
    println("LOCAL_PINNED_TRACE_EXPORT ${outFile.path}")
}
