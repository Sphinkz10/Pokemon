import com.rui.pvpgo.engine.*

fun main() {
    PinnedBattleCaseCatalog.validatePinnedData()
    check(PinnedBattleCaseCatalog.baselineCases.size == 3)
    check(PinnedBattleCaseCatalog.expandedCases.size == 8)
    check(PinnedBattleCaseCatalog.baselineCases.map { it.fixtureId } == listOf(
        "PINNED-AZU-FERALIGATR", "PINNED-AZU-FERALIGATR-SHADOW", "PINNED-AZU-WALREIN"
    ))
    val coverage = PinnedBattleCaseCatalog.expandedCases.flatMap { it.coverage }.toSet()
    check((0..2).all { n -> coverage.contains("$n-shield") })
    check((1..5).all { n -> coverage.contains("fast-$n") })
    check("shadow" in coverage)
    check("guaranteed-debuff" in coverage)
    check("guaranteed-self-buff" in coverage)
    println("PINNED_CASE_CATALOG_OK")
    println("baseline=${PinnedBattleCaseCatalog.baselineCases.size} expanded=${PinnedBattleCaseCatalog.expandedCases.size} coverage=${coverage.sorted().joinToString(",")}")
}
