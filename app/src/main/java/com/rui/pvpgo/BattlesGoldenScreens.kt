package com.rui.pvpgo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.BattleRecord
import com.rui.pvpgo.domain.MatchupAdvisorResult
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.ui.theme.PvpColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val goldenBattleRadius = RoundedCornerShape(17.dp)

@Composable
private fun BattleGoldenPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = goldenBattleRadius,
        color = PvpColors.SurfaceCard, border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
fun BattlesGoldenHomeScreen(
    catalog: List<PokemonSpecies>, owned: List<OwnedPokemon>, teams: List<SavedTeam>,
    battles: List<BattleRecord>, league: League, onLeagueChange: (League) -> Unit,
    onAnalyze: () -> Unit, onRecord: () -> Unit, onHistory: () -> Unit
) {
    val byId = remember(catalog) { catalog.associateBy { it.speciesId } }
    val filtered = remember(battles, league) { BattlesGoldenPolicy.forLeague(battles, league) }
    val scopedTeams = remember(teams, league) { BattlesGoldenPolicy.eligibleTeams(teams, league) }
    val complete = remember(owned) { BattlesGoldenPolicy.completeOwnedCount(owned) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Batalhas", color = PvpColors.TextPrimary, style = MaterialTheme.typography.headlineLarge)
            Text("Análise, registo e histórico PvP", color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(League.GREAT, League.ULTRA, League.MASTER).forEach { value ->
                    FilterChip(selected = league == value, onClick = { onLeagueChange(value) },
                        label = { Text(when (value) { League.GREAT -> "Great"; League.ULTRA -> "Ultra"; else -> "Master" }) })
                }
            }
        }
        item {
            BattleGoldenPanel {
                Text("A TUA EQUIPA", style = MaterialTheme.typography.labelLarge, color = PvpColors.TextSecondary)
                val team = scopedTeams.firstOrNull()
                if (team == null) {
                    Text("Ainda não tens equipa guardada nesta liga", fontWeight = FontWeight.Bold)
                    Text("Guarda uma equipa em Equipas. A análise individual continua disponível quando existirem builds completos.",
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(team.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val byOwned = owned.associateBy { it.id }
                        team.members.forEach { member ->
                            val item = byOwned[member.ownedPokemonId]
                            val name = item?.let { byId[it.speciesId]?.name ?: it.speciesId } ?: "Em falta"
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                PokemonArtwork(name, Modifier.size(51.dp))
                                Text(name, style = MaterialTheme.typography.bodySmall,
                                    color = PvpColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Text("$complete exemplares com nível e ataques preenchidos", color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text("FERRAMENTAS", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            BattleGoldenPanel {
                Text("Analisar matchup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Compara os teus builds completos com um adversário definido. IVs, nível, ataques e shields são obrigatórios.",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Configurar matchup")
                }
                Text("Para analisar os 3 membros da tua equipa, configura primeiro um adversário " +
                    "com IVs, nível, moves e shields explícitos. Após o resultado, escolhe 'Cobertura da equipa'.",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onRecord, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Registar batalha")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("HISTÓRICO", style = MaterialTheme.typography.labelLarge, color = PvpColors.TextSecondary)
                TextButton(onClick = onHistory, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver histórico") }
            }
            Text(BattlesGoldenPolicy.historyLabel(filtered), style = MaterialTheme.typography.bodySmall,
                color = PvpColors.TextSecondary)
        }
        items(filtered.take(3), key = { it.id }) { record ->
            Surface(onClick = onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                color = PvpColors.SurfaceCard, shape = goldenBattleRadius,
                border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val opponent = record.opponentPokemon.firstOrNull()?.speciesId?.let { byId[it]?.name ?: it } ?: "Adversário não identificado"
                        Text(opponent, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(SimpleDateFormat("dd/MM · HH:mm", Locale("pt", "PT")).format(Date(record.playedAtEpochMs)),
                            color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(when (record.outcome) { BattleOutcome.WIN -> "VITÓRIA"; BattleOutcome.LOSS -> "DERROTA"; BattleOutcome.DRAW -> "EMPATE" },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (record.outcome == BattleOutcome.WIN) PvpColors.StateSuccess else PvpColors.TextSecondary)
                }
            }
        }
        item {
            Text("Os resultados do histórico são observações gravadas, não previsões. Matchup Advisor só produz rating para cenários completos e certificados.",
                color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** B03: the advisor's exact result, not a made-up 3v3 score. */
@Composable
fun BattlesGoldenResultScreen(
    analysis: MatchupAdvisorResult, catalog: List<PokemonSpecies>,
    owned: List<OwnedPokemon>, onEdit: () -> Unit, onClose: () -> Unit,
    onTeamCoverage: () -> Unit
) {
    val names = remember(catalog) { catalog.associate { it.speciesId to it.name } }
    val byOwned = remember(owned) { owned.associateBy { it.id } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = onEdit, modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(13.dp), color = PvpColors.SurfaceCard,
                    border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
                    Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                }
                Column(Modifier.weight(1f)) {
                    Text("Resultado", style = MaterialTheme.typography.headlineLarge)
                    Text("Matchup Advisor · análise da coleção", style = MaterialTheme.typography.bodySmall,
                        color = PvpColors.TextSecondary)
                }
            }
        }
        item {
            BattleGoldenPanel {
                Text(BattlesGoldenPolicy.resultHeadline(analysis), style = MaterialTheme.typography.titleLarge)
                Text("Contra ${names[analysis.opponent.speciesId] ?: analysis.opponent.speciesId}",
                    style = MaterialTheme.typography.bodyMedium)
                Text(BattlesGoldenPolicy.RATING_NOTE, color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
                Text("Certificação: ${analysis.certificationRoute ?: "não disponível"}",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                analysis.refusalReason?.let { Text(it, color = PvpColors.StateWarning) }
            }
        }
        if (BattlesGoldenPolicy.canShowRatings(analysis)) {
            item { Text("CANDIDATOS AVALIADOS", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelLarge) }
            items(analysis.recommendations, key = { it.ownedPokemonId }) { recommendation ->
                BattleGoldenPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val name = names[recommendation.speciesId] ?: recommendation.speciesId
                        PokemonArtwork(name, Modifier.size(50.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("#${recommendation.position} · $name", fontWeight = FontWeight.Bold)
                            val specimen = byOwned[recommendation.ownedPokemonId]
                            if (specimen != null) Text("CP ${recommendation.pokemonCp} · ${specimen.iv.attack}/${specimen.iv.defense}/${specimen.iv.stamina}",
                                color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${recommendation.evaluation.battleRating}/1000", color = PvpColors.BrandBlue,
                            style = MaterialTheme.typography.labelLarge)
                    }
                    Text("${recommendation.evaluation.outcome} · ${recommendation.evaluation.turns} turns · ${recommendation.evaluation.band}",
                        style = MaterialTheme.typography.bodySmall)
                    Text(recommendation.why, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            item {
                BattleGoldenPanel {
                    Text("Nenhum resultado inventado", fontWeight = FontWeight.Bold)
                    Text(analysis.refusalReason ?: "Sem builds elegíveis: completa nível, ataques, IVs e regras antes de repetir.",
                        style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
                }
            }
        }
        if (analysis.exclusions.isNotEmpty()) {
            item { Text("EXCLUSÕES · ${analysis.exclusions.size}", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelLarge) }
            items(analysis.exclusions.take(15)) { e ->
                BattleGoldenPanel {
                    Text(names[e.speciesId] ?: e.speciesId, fontWeight = FontWeight.Bold)
                    Text("${e.reason} · ${e.detail}", style = MaterialTheme.typography.bodySmall,
                        color = PvpColors.TextSecondary)
                }
            }
        }
        item {
            if (BattlesGoldenPolicy.canShowRatings(analysis)) {
                Button(onClick = onTeamCoverage, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Ver cobertura da equipa · B04")
                }
            }
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Alterar cenário") }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Voltar a Batalhas") }
        }
    }
}

/** B05 — saved history and replay details from actual Room records only. */
@Composable
fun BattlesGoldenHistoryScreen(
    catalog: List<PokemonSpecies>, teams: List<SavedTeam>, battles: List<BattleRecord>,
    league: League, onBack: () -> Unit
) {
    val names = remember(catalog) { catalog.associate { it.speciesId to it.name } }
    val savedTeams = remember(teams) { teams.associateBy { it.id } }
    val filtered = remember(battles, league) { BattlesGoldenPolicy.forLeague(battles, league) }
    var selectedId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val selected = filtered.firstOrNull { it.id == selectedId }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = { if (selected != null) selectedId = null else onBack() },
                    modifier = Modifier.size(48.dp), shape = RoundedCornerShape(13.dp),
                    color = PvpColors.SurfaceCard, border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
                    Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                }
                Column(Modifier.weight(1f)) {
                    Text(if (selected == null) "Histórico" else "Detalhe batalha", style = MaterialTheme.typography.headlineMedium)
                    Text("${league.name.lowercase().replaceFirstChar { it.uppercase() }} League · registos locais",
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (selected != null) {
            item {
                BattleGoldenPanel {
                    val outcome = when (selected.outcome) {
                        BattleOutcome.WIN -> "VITÓRIA"; BattleOutcome.LOSS -> "DERROTA"; BattleOutcome.DRAW -> "EMPATE"
                    }
                    Text(outcome, fontWeight = FontWeight.Bold,
                        color = if (selected.outcome == BattleOutcome.WIN) PvpColors.StateSuccess else PvpColors.TextPrimary)
                    Text(SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("pt", "PT")).format(Date(selected.playedAtEpochMs)),
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Equipa: ${selected.ownTeamId?.let(savedTeams::get)?.name ?: "Não identificada / removida"}",
                        style = MaterialTheme.typography.bodyMedium)
                    selected.opponentPokemon.forEach { opp ->
                        Text("${opp.slot} · ${names[opp.speciesId] ?: opp.speciesId}")
                    }
                    if (selected.opponentPokemon.isEmpty()) Text("Adversário: dados não registados",
                        color = PvpColors.TextSecondary)
                    if (selected.ratingBefore != null || selected.ratingAfter != null) {
                        Text("Rating registado ${selected.ratingBefore ?: "?"} → ${selected.ratingAfter ?: "?"}")
                    }
                    selected.notes?.takeIf { it.isNotBlank() }?.let { Text("Notas: $it") }
                    if (selected.tags.isNotEmpty()) Text("Tags: ${selected.tags.sorted().joinToString()}",
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Text("Este detalhe é um registo histórico: não reconstrói turnos, shields ou movimentos não guardados.",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            item {
                BattleGoldenPanel {
                    Text(BattlesGoldenPolicy.historyLabel(filtered), fontWeight = FontWeight.SemiBold)
                    Text("Apenas batalhas registadas nesta liga. Sem resultados de demonstração.",
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(filtered, key = { it.id }) { battle ->
                Surface(onClick = { selectedId = battle.id },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    color = PvpColors.SurfaceCard, shape = goldenBattleRadius,
                    border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(battle.opponentPokemon.firstOrNull()?.let { names[it.speciesId] ?: it.speciesId }
                                ?: "Adversário não identificado", fontWeight = FontWeight.Bold)
                            Text(SimpleDateFormat("dd/MM · HH:mm", Locale("pt", "PT")).format(Date(battle.playedAtEpochMs)),
                                color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(when (battle.outcome) {
                            BattleOutcome.WIN -> "VITÓRIA"; BattleOutcome.LOSS -> "DERROTA"; BattleOutcome.DRAW -> "EMPATE"
                        }, style = MaterialTheme.typography.labelMedium,
                            color = if (battle.outcome == BattleOutcome.WIN) PvpColors.StateSuccess else PvpColors.TextSecondary)
                    }
                }
            }
        }
    }
}
