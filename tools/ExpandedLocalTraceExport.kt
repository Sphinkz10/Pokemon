import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.engine.*
import java.io.File

private fun esc2(s: String?): String = if (s == null) "null" else buildString {
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
private fun actor2(key: String?, a: BattleBuild, b: BattleBuild): String = when (key) { a.key -> "0"; b.key -> "1"; else -> "null" }
private fun state2(s: BattleStateSnapshot): String = "{\"hp\":${s.hp},\"energy\":${s.energy},\"shields\":${s.shields},\"attackStage\":${s.attackStage},\"defenseStage\":${s.defenseStage}}"

fun main(args: Array<String>) {
    PinnedBattleCaseCatalog.validatePinnedData()
    val outFile = File(args.firstOrNull() ?: "evidence/local-v1.29-expanded-traces.json")
    val engine = BattleEngine()
    val rows = PinnedBattleCaseCatalog.expandedCases
    val json = buildString {
        append("{\n  \"schemaVersion\":2,\n  \"engineVersion\":\"1.29\",\n")
        append("  \"reference\":{\"commitSha\":${esc2(PinnedBattleCaseCatalog.REFERENCE_SHA)},\"siteVersion\":${esc2(PinnedBattleCaseCatalog.REFERENCE_VERSION)}},\n")
        append("  \"results\":[\n")
        rows.forEachIndexed { ci, c ->
            val r = engine.simulate(c.a, c.b, c.scenario())
            val winner = when(r.outcomeForA){ BattleOutcome.WIN->"0"; BattleOutcome.LOSS->"1"; BattleOutcome.DRAW->"null" }
            append("    {\"fixtureId\":${esc2(c.fixtureId)},\"shieldsA\":${c.shieldsA},\"shieldsB\":${c.shieldsB},\"coverage\":[${c.coverage.sorted().joinToString(","){esc2(it)}}],\"battleRatingA\":${r.battleRatingA},\"battleRatingB\":${r.battleRatingB},\"winner\":$winner,\"turns\":${r.turns},\n")
            append("     \"final\":{\"a\":${state2(r.finalA)},\"b\":${state2(r.finalB)}},\"timeline\":[\n")
            r.timeline.forEachIndexed { i,e ->
                append("       {\"i\":$i,\"turn\":${e.turn},\"actor\":${actor2(e.actorKey,c.a,c.b)},\"type\":${esc2(e.type.name)},\"moveId\":${esc2(e.moveId)},\"damage\":${e.damage?:"null"},\"energyAfter\":${e.energyAfter?:"null"},\"shielded\":${e.shielded},\"note\":${esc2(e.note)}}")
                if(i!=r.timeline.lastIndex)append(','); append('\n')
            }
            append("     ]}")
            if(ci!=rows.lastIndex)append(','); append('\n')
        }
        append("  ]\n}\n")
    }
    outFile.parentFile?.mkdirs(); outFile.writeText(json)
    println("LOCAL_EXPANDED_TRACE_EXPORT ${outFile.path}")
}
