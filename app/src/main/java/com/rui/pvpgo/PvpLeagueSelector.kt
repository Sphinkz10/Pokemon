package com.rui.pvpgo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.ui.theme.PvpColors

/** One selectable ruleset across Species 360, Exemplars, Compare and IV Targets. */
object PvpLeagueLabels {
    val options: List<League> = listOf(League.LITTLE, League.GREAT, League.ULTRA, League.MASTER)
    fun short(league: League): String = when (league) {
        League.LITTLE -> "Little"
        League.GREAT -> "Great"
        League.ULTRA -> "Ultra"
        League.MASTER -> "Master"
    }
    fun title(league: League): String = "${short(league)} League"
    fun capText(league: League): String =
        league.cpCap?.let { "até $it CP" } ?: "sem limite de CP"
    fun context(league: League): String =
        "Ranking de IV calculado para ${title(league)} (${capText(league)}) pelo stat product. Não é um ranking do meta nem garante vitórias."
}

@Composable
fun PvpLeagueSelector(
    selected: League,
    onSelect: (League) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (league in PvpLeagueLabels.options) {
            val active = league == selected
            Surface(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(league) },
                color = if (active) PvpColors.AccentDeep else PvpColors.SurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (active) PvpColors.BrandBlue else PvpColors.BorderDefault)
            ) {
                Text(
                    text = PvpLeagueLabels.short(league),
                    modifier = Modifier.padding(PaddingValues(horizontal = 15.dp, vertical = 12.dp)),
                    color = if (active) PvpColors.TextPrimary else PvpColors.TextSecondary,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
