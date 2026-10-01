package com.rui.pvpgo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvPRankEntry
import com.rui.pvpgo.engine.RankSettings
import com.rui.pvpgo.ui.theme.PvpColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IvBlue = Color(0xFF75D5FF)
private val IvGreen = Color(0xFF7BE0A1)
private val IvAmber = Color(0xFFFFC84D)
private val IvCard = Color(0xFF101D31)
private val IvBorder = Color(0xFF263C55)

@Composable
fun IvTargetsScreen(
    species: PokemonSpecies,
    collection: List<OwnedPokemon>,
    onBack: () -> Unit
) {
    val owned = remember(collection, species.speciesId) { collection.filter { it.speciesId == species.speciesId } }
    var topRows by remember(species.speciesId) { mutableStateOf<List<PvPRankEntry>>(emptyList()) }
    var bestOwned by remember(species.speciesId, owned) { mutableStateOf<Pair<OwnedPokemon, PvPRankEntry>?>(null) }
    var loading by remember(species.speciesId, owned) { mutableStateOf(true) }

    LaunchedEffect(species.speciesId, owned) {
        loading = true
        val result = withContext(Dispatchers.Default) {
            val settings = RankSettings()
            val ranks = RankRepository.ranks(species, League.GREAT, settings)
            val best = owned.mapNotNull { pokemon ->
                RankRepository.find(species, League.GREAT, pokemon.iv, settings)?.let { pokemon to it }
            }.minByOrNull { it.second.rank }
            ranks.take(4) to best
        }
        topRows = result.first
        bestOwned = result.second
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { IvTargetsHeader(species.name, onBack) }
        item { IvSpeciesHero(species, bestOwned) }
        item { IvSectionHeader("DOIS CONCEITOS DIFERENTES") }
        item { IvConcepts() }
        item { IvSectionHeader("TOP IVS · GREAT LEAGUE") }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = IvBlue)
                }
            }
        } else {
            topRows.forEach { row ->
                item(key = "rank-${row.rank}") {
                    IvRankRow(row = row, ownedRank = bestOwned?.second?.rank == row.rank)
                }
            }
            bestOwned?.let { (pokemon, rank) ->
                if (topRows.none { it.rank == rank.rank }) {
                    item { IvOwnedDivider(rank.rank) }
                    item { IvRankRow(row = rank, ownedRank = true, label = "TENS") }
                }
            }
        }
        item { IvSectionHeader("AÇÃO") }
        item { IvActionCard(bestOwned) }
    }
}

@Composable
private fun IvTargetsHeader(name: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(48.dp).clickable(onClick = onBack),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0B1728),
            border = BorderStroke(1.dp, IvBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", color = PvpColors.TextPrimary, fontSize = 30.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("IV Targets", color = PvpColors.TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("$name · Great League", color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun IvSpeciesHero(species: PokemonSpecies, bestOwned: Pair<OwnedPokemon, PvPRankEntry>?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF13283F),
        border = BorderStroke(1.dp, Color(0xFF2F5D7D))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PokemonArtwork(species.name, Modifier.size(76.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(species.name, color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                if (bestOwned == null) {
                    Text("Ainda não tens um exemplar desta espécie.", color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("OBJETIVO · encontrar um bom IV PvP", color = IvBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                } else {
                    val (owned, rank) = bestOwned
                    Text("Melhor teu: ${owned.iv.attack}/${owned.iv.defense}/${owned.iv.stamina} · Rank #${rank.rank}", color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("${rank.cp} CP · L${compactLevel(rank.level)} · ${oneDecimal(rank.percentOfRank1)}% do #1", color = IvBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun IvConcepts() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IvConceptCard(
            modifier = Modifier.weight(1f),
            title = "PvP Rank",
            detail = "Stat Product otimizado para o limite de CP.",
            accent = IvBlue
        )
        IvConceptCard(
            modifier = Modifier.weight(1f),
            title = "100% IV",
            detail = "15/15/15. Não significa automaticamente melhor PvP.",
            accent = IvAmber
        )
    }
}

@Composable
private fun IvConceptCard(modifier: Modifier, title: String, detail: String, accent: Color) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = IvCard, border = BorderStroke(1.dp, IvBorder)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(detail, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun IvSectionHeader(title: String) {
    Text(title, color = PvpColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun IvRankRow(row: PvPRankEntry, ownedRank: Boolean, label: String? = null) {
    val accent = if (ownedRank) IvGreen else IvBlue
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (ownedRank) Color(0xFF122E2E) else IvCard,
        border = BorderStroke(1.dp, if (ownedRank) Color(0xFF2D6B5B) else IvBorder)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.14f)) {
                Text(
                    "#${row.rank}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${row.iv.attack}/${row.iv.defense}/${row.iv.stamina}",
                    color = PvpColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${row.cp} CP · L${compactLevel(row.level)} · ${oneDecimal(row.percentOfRank1)}%",
                    color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(label ?: if (ownedRank) "TENS" else "ALVO", color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IvOwnedDivider(rank: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("O TEU MELHOR", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("Rank #$rank", color = IvGreen, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun IvActionCard(bestOwned: Pair<OwnedPokemon, PvPRankEntry>?) {
    val text = when {
        bestOwned == null -> "Procura um exemplar competitivo. O objetivo é comparar o IV real com a tabela Great League antes de investir recursos."
        bestOwned.second.rank == 1 -> "Já tens o Rank #1 matemático desta espécie para Great League. O próximo passo é validar moves e custo de build."
        bestOwned.second.rank <= 50 -> "O teu melhor exemplar já está no Top 50. Compara o custo de melhoria antes de substituir um build utilizável."
        else -> "O teu melhor exemplar é Rank #${bestOwned.second.rank}. Mantém-no como referência e procura uma melhoria sem apagar progresso útil."
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFF14283F), border = BorderStroke(1.dp, IvBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("O que fazer agora", color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(text, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun compactLevel(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else oneDecimal(value)
private fun oneDecimal(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
