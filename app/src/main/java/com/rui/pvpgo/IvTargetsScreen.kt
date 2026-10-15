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
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

private val IvBlue: Color get() = PvpColors.AccentSky
private val IvGreen: Color get() = PvpColors.AccentGreen
private val IvAmber: Color get() = PvpColors.AccentAmber
private val IvCard: Color get() = PvpColors.SurfaceCard
private val IvBorder: Color get() = PvpColors.BorderDefault

@Composable
fun IvTargetsScreen(
    species: PokemonSpecies,
    collection: List<OwnedPokemon>,
    league: League,
    onLeagueChange: (League) -> Unit,
    onBack: () -> Unit,
    onOpenOwned: ((String) -> Unit)? = null
) {
    val owned = remember(collection, species.speciesId) { collection.filter { it.speciesId == species.speciesId } }
    var topRows by remember(species.speciesId, league) { mutableStateOf<List<PvPRankEntry>>(emptyList()) }
    var ownedDecisions by remember(species.speciesId, owned, league) { mutableStateOf<List<OwnedIvDecision>>(emptyList()) }
    val bestOwned = ownedDecisions.firstOrNull { it.rank != null }?.let { it.owned to requireNotNull(it.rank) }
    var showAllOwned by rememberSaveable(species.speciesId, league) { mutableStateOf(false) }
    var loading by remember(species.speciesId, owned, league) { mutableStateOf(true) }

    LaunchedEffect(species.speciesId, owned, league) {
        loading = true
        val result = withContext(Dispatchers.Default) {
            val settings = RankSettings()
            val ranks = RankRepository.ranks(species, league, settings)
            val decisions = OwnedIvDecisionPolicy.sort(species, league, owned, ranks)
            ranks.take(4) to decisions
        }
        topRows = result.first
        ownedDecisions = result.second
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("iv-targets-scroll"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { IvTargetsHeader(species.name, league, onBack) }
        item { SpeciesLeagueSelector(league, onLeagueChange) }
        item {
            Text(
                "Ranking de IV por stat product para ${SpeciesLeaguePolicy.cpLimitLabel(league)}. Não representa a classificação competitiva da espécie.",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        item { IvSpeciesHero(species, bestOwned, owned.size, loading) }
        item { IvSectionHeader("DOIS CONCEITOS DIFERENTES") }
        item { IvConcepts(league) }
        item { IvSectionHeader("TOP IVS · ${SpeciesLeaguePolicy.title(league).uppercase()}") }
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
        item { IvSectionHeader("OS TEUS EXEMPLARES · ${owned.size}") }
        item {
            Text(
                "Ordenados pelo rank de IV potencial, não pelo CP atual nem pela força no meta. " +
                    "Abre o Pokémon 360 para confirmar dados e custos antes de investir.",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (!loading) {
            val visible = if (showAllOwned) ownedDecisions else ownedDecisions.take(3)
            items(visible.size, key = { "owned-rank-${visible[it].owned.id}" }) { index ->
                IvOwnedDecisionCard(
                    candidate = visible[index],
                    index = index + 1,
                    onOpenOwned = onOpenOwned
                )
            }
            if (ownedDecisions.size > 3) {
                item {
                    TextButton(
                        onClick = { showAllOwned = !showAllOwned },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("iv-owned-toggle-all")
                    ) {
                        Text(if (showAllOwned) "Mostrar só os três primeiros"
                            else "Ver os ${ownedDecisions.size} exemplares")
                    }
                }
            }
        }
        item { IvSectionHeader("AÇÃO") }
        item { IvActionCard(bestOwned, league, owned.size, loading) }
    }
}

@Composable
private fun IvOwnedDecisionCard(
    candidate: OwnedIvDecision,
    index: Int,
    onOpenOwned: ((String) -> Unit)?
) {
    val owned = candidate.owned
    val label = owned.nickname?.takeIf { it.isNotBlank() } ?: owned.speciesId
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("iv-owned-row-${owned.id}"),
        shape = RoundedCornerShape(14.dp),
        color = IvCard,
        border = BorderStroke(1.dp, IvBorder)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "$index · $label",
                    color = PvpColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    candidate.rank?.let { "Rank IV #${it.rank}" } ?: "SEM RANK",
                    color = if (candidate.rank != null) IvBlue else IvAmber,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("iv-owned-rank-${owned.id}")
                )
            }
            Text(
                "IV ${owned.iv.attack}/${owned.iv.defense}/${owned.iv.stamina} · " +
                    "CP ${owned.cp?.toString() ?: "?"} · " +
                    "Nível ${owned.level?.let(::compactLevel) ?: "?"}",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                OwnedIvDecisionPolicy.explanation(candidate),
                color = if (candidate.readiness.verdict == PvpGuideVerdict.WITHIN_LIMIT &&
                    candidate.rank != null) PvpColors.TextSecondary else IvAmber,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("iv-owned-status-${owned.id}")
            )
            if (onOpenOwned != null) {
                TextButton(
                    onClick = { onOpenOwned(owned.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("iv-owned-open-${owned.id}")
                ) { Text("Abrir Pokémon 360 →") }
            }
        }
    }
}

@Composable
private fun IvTargetsHeader(name: String, league: League, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(48.dp).testTag("iv-targets-back").clickable(onClick = onBack),
            shape = RoundedCornerShape(14.dp),
            color = PvpColors.CanvasMiddle,
            border = BorderStroke(1.dp, IvBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", color = PvpColors.TextPrimary, fontSize = 30.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("IV Targets", color = PvpColors.TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(
                "$name · ${SpeciesLeaguePolicy.title(league)}",
                modifier = Modifier.testTag("iv-targets-league"),
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun IvSpeciesHero(species: PokemonSpecies, bestOwned: Pair<OwnedPokemon, PvPRankEntry>?, ownedCount: Int, loading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PvpColors.SurfaceRaised,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PokemonArtwork(species.name, Modifier.size(76.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(species.name, color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                if (bestOwned == null) {
                    Text(
                        if (loading) "A calcular os IVs da liga…"
                        else if (ownedCount > 0) "Tens $ownedCount exemplares, mas nenhum ranking elegível nesta liga."
                        else "Ainda não tens um exemplar desta espécie.",
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("OBJETIVO · encontrar um bom IV PvP", color = IvBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                } else {
                    val (owned, rank) = bestOwned
                    Text("Melhor IV potencial teu: ${owned.iv.attack}/${owned.iv.defense}/${owned.iv.stamina} · Rank #${rank.rank}", color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("CP alvo ${rank.cp} · L${compactLevel(rank.level)} · ${oneDecimal(rank.percentOfRank1)}% do #1", color = IvBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun IvConcepts(league: League) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IvConceptCard(
            modifier = Modifier.weight(1f),
            title = "PvP Rank",
            detail = if (league.cpCap == null) "Stat Product ao nível máximo configurado." else "Stat Product para ${SpeciesLeaguePolicy.cpLimitLabel(league)}.",
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
        color = if (ownedRank) PvpColors.AccentDeep else IvCard,
        border = BorderStroke(1.dp, if (ownedRank) PvpColors.StateSuccess else IvBorder)
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
private fun IvActionCard(bestOwned: Pair<OwnedPokemon, PvPRankEntry>?, league: League, ownedCount: Int, loading: Boolean) {
    val text = when {
        loading -> "Os rankings da liga estão a ser calculados. Não interpretes resultados incompletos."
        bestOwned == null && ownedCount > 0 -> "Há $ownedCount exemplares registados, mas nenhum ranking elegível nesta liga com a configuração atual. Confirma o CP e os dados da espécie."
        bestOwned == null -> "Ainda não tens um exemplar com rank disponível para ${SpeciesLeaguePolicy.title(league)}. Compara os IVs e os custos antes de investir recursos."
        bestOwned.second.rank == 1 -> "O teu exemplar tem Rank #1 de stat product para ${SpeciesLeaguePolicy.title(league)}. Isto não mede o meta; confirma ataques, elegibilidade e custos."
        bestOwned.second.rank <= 50 -> "O teu melhor exemplar já está no Top 50. Compara o custo de melhoria antes de substituir um build utilizável."
        else -> "O teu melhor exemplar é Rank #${bestOwned.second.rank}. Mantém-no como referência e procura uma melhoria sem apagar progresso útil."
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = PvpColors.SurfaceRaised, border = BorderStroke(1.dp, IvBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("O que fazer agora", color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(text, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun compactLevel(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else oneDecimal(value)
private fun oneDecimal(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
