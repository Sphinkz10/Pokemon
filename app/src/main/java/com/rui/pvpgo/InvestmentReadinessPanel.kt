package com.rui.pvpgo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies

/**
 * Evidence-first checklist shared by Inbox and Pokémon 360.
 * No side effects: actual saves remain under explicit user action.
 */
@Composable
internal fun InvestmentReadinessPanel(
    owned: OwnedPokemon,
    species: PokemonSpecies?,
    league: League,
    compact: Boolean
) {
    val report = remember(owned, species, league) {
        InvestmentReadinessPolicy.inspect(owned, species, league)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                if (compact) "Antes de investir · ${SpeciesLeaguePolicy.shortLabel(league)}"
                else "Checklist para investir · ${SpeciesLeaguePolicy.shortLabel(league)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(report.headline, fontWeight = FontWeight.SemiBold)
            Text(report.nextAction, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            val visible = if (compact) {
                val outstanding = report.checks.filter {
                    it.status != ReadinessCheckStatus.CHECKED
                }
                outstanding.take(3)
            } else report.checks
            visible.forEach { check ->
                val marker = when (check.status) {
                    ReadinessCheckStatus.CHECKED -> "Confirmar"
                    ReadinessCheckStatus.MISSING -> "Falta"
                    ReadinessCheckStatus.RECHECK -> "Rever"
                    ReadinessCheckStatus.BASE_INELIGIBLE -> "Não cumpre"
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${check.name} · $marker",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!compact || check.status != ReadinessCheckStatus.CHECKED) {
                        Text(check.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (compact && report.outstandingCount > visible.size) {
                Text(
                    "Mais ${report.outstandingCount - visible.size} ponto(s) por verificar em Pokémon 360.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                InvestmentReadinessPolicy.LIMITATION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
