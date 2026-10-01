package com.rui.pvpgo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.ui.theme.PvpColors

/**
 * Every PokéAPI National Dex entry is navigable, including species absent
 * from the separate PvPoke species catalog. PvP computations remain opt-in
 * only when a matching, validated battle entry exists.
 */
@Composable
fun NationalDexDetailScreen(
    entry: NationalDexEntry,
    ownedCount: Int,
    battleAvailable: Boolean,
    indexSource: String,
    onBack: () -> Unit,
    onOpenIvTargets: (() -> Unit)?,
    onOpenOwned: (() -> Unit)?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("‹ Voltar à Pokédex")
            }
        }
        item {
            Surface(
                color = PvpColors.SurfaceCard,
                border = BorderStroke(1.dp, PvpColors.BorderDefault),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "#${entry.dex.toString().padStart(4, '0')} · ${NationalDexPolicy.generation(entry.dex)}",
                        color = PvpColors.BrandBlue,
                        style = MaterialTheme.typography.labelLarge
                    )
                    PokemonArtwork(entry.name, Modifier.size(150.dp))
                    Text(
                        entry.name,
                        color = PvpColors.TextPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Espécie registada na Pokédex Nacional",
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = PvpColors.SurfaceCard,
                    border = BorderStroke(1.dp, PvpColors.BorderDefault),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("NA TUA COLEÇÃO", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Text("$ownedCount", color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = PvpColors.SurfaceCard,
                    border = BorderStroke(1.dp, PvpColors.BorderDefault),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("ANÁLISE PVP", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (battleAvailable) "Disponível" else "Indisponível",
                            color = if (battleAvailable) PvpColors.StateSuccess else PvpColors.StateWarning,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
        item {
            Surface(
                color = PvpColors.SurfaceRaised,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FONTE E LIMITES", color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(indexSource, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "O índice nacional identifica espécies. Não demonstra que esta espécie tenha ataques, tipos, IVs ou simulações PvP verificados no motor. Não inventamos esses dados.",
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        if (onOpenIvTargets != null && battleAvailable) item {
            Button(onClick = onOpenIvTargets, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Consultar IV Targets PvP")
            }
        }
        if (onOpenOwned != null && ownedCount > 0) item {
            OutlinedButton(onClick = onOpenOwned, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Ver os meus exemplares ($ownedCount)")
            }
        }
        if (!battleAvailable) item {
            Text(
                "Esta espécie pode ser consultada na Pokédex, mas os cálculos PvP ficam bloqueados até existir um registo de batalha verificado.",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
