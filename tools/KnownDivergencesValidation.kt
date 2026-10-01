import com.rui.pvpgo.engine.*

fun main() {
    check(KnownDivergencesRegistry.entries.size >= 2)
    val morpeko = KnownDivergencesRegistry.entries.first { it.id == "a93147b-morpeko-toggle-one-way" }
    val pruning = KnownDivergencesRegistry.entries.first { it.id == "a93147b-actionlogic-dead-dominance-pruning" }
    check(morpeko.authoritativeAdviceBlocked)
    check(morpeko.upstreamIssueUrl?.endsWith("/379") == true)
    check(pruning.affectedCapability == "action-dp-core")
    check(pruning.upstreamIssueUrl?.endsWith("/380") == true)
    check(pruning.disposition == DivergenceDisposition.LOCAL_CORRECTNESS_WINS)
    check(KnownDivergencesRegistry.forCapability("action-dp-core").contains(pruning))
    println("KNOWN_DIVERGENCES_V1_29_OK entries=${KnownDivergencesRegistry.entries.size}")
    println("morpeko=${morpeko.id} dp=${pruning.id}")
}
