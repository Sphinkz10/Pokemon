package com.rui.pvpgo.engine

data class IvComparison(
    val a: PvPRankEntry,
    val b: PvPRankEntry,
    val statProductDeltaPercentBvsA: Double,
    val attackDeltaBvsA: Double,
    val defenseDeltaBvsA: Double,
    val hpDeltaBvsA: Int,
    val cmpWinner: ComparisonWinner,
    val statProductWinner: ComparisonWinner
)

enum class ComparisonWinner { A, B, TIE }

object IvComparator {
    fun compare(a: PvPRankEntry, b: PvPRankEntry): IvComparison {
        val spA = a.stats.statProduct
        val spB = b.stats.statProduct
        val spDelta = if (spA == 0.0) 0.0 else ((spB / spA) - 1.0) * 100.0
        return IvComparison(
            a = a,
            b = b,
            statProductDeltaPercentBvsA = spDelta,
            attackDeltaBvsA = b.stats.attack - a.stats.attack,
            defenseDeltaBvsA = b.stats.defense - a.stats.defense,
            hpDeltaBvsA = b.stats.hp - a.stats.hp,
            cmpWinner = winner(a.stats.attack, b.stats.attack),
            statProductWinner = winner(spA, spB)
        )
    }

    private fun winner(a: Double, b: Double): ComparisonWinner = when {
        a > b + 1e-9 -> ComparisonWinner.A
        b > a + 1e-9 -> ComparisonWinner.B
        else -> ComparisonWinner.TIE
    }
}
