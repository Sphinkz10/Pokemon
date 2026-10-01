package com.rui.pvpgo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.domain.MatchupAdvisorResult
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.domain.TeamRole
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.ui.theme.PvpColors

/** B04 GOLDEN: visual projection of explicit opponent vs EACH team member, not a 3v3 engine. */
@Composable
fun BattleTeamCoverageScreen(
    analysis: MatchupAdvisorResult,
    catalog: List<PokemonSpecies>,
    owned: List<OwnedPokemon>,
    savedTeams: List<SavedTeam>,
    onBack: () -> Unit,
    onEditScenario: () -> Unit
) {
    val teams = remember(savedTeams, analysis.scenario.league) {
        BattlesGoldenPolicy.eligibleTeams(savedTeams, analysis.scenario.league)
    }
    var selectedId by remember(analysis.scenario.league) { mutableStateOf<String?>(null) }
    val team = teams.firstOrNull { it.id == selectedId } ?: teams.firstOrNull()
    val names = remember(catalog) { catalog.associate { it.speciesId to it.name } }
    val projection = remember(team, owned, analysis) {
        team?.let { BattleTeamCoveragePolicy.project(it, owned, analysis) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = onBack, modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(13.dp), color = PvpColors.SurfaceCard,
                    border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
                    Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                }
                Column(Modifier.weight(1f)) {
                    Text("Simulação de equipa", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = PvpColors.TextPrimary)
                    Text("B04 · Cobertura 1v1 por membro", style = MaterialTheme.typography.bodySmall,
                        color = PvpColors.TextSecondary)
                }
            }
        }
        item {
            CoverageCard {
                Text("Adversário: ${names[analysis.opponent.speciesId] ?: analysis.opponent.speciesId}",
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Shields ${analysis.scenario.ownShields}–${analysis.scenario.opponentShields} · " +
                    "Energia ${analysis.scenario.ownStartingEnergy}–${analysis.scenario.opponentStartingEnergy}",
                    style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
                Text("Não prevê trocas, alinhamentos, shields partilhados, nem vitória 3v3. " +
                    "Battle Rating (0–1000) não é probabilidade de vitória.",
                    color = PvpColors.StateWarning, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (teams.isEmpty()) {
            item {
                CoverageCard {
                    Text("Sem equipa guardada nesta liga", fontWeight = FontWeight.Bold)
                    Text("Cria uma equipa válida em Equipas e volta a analisar um adversário completo.",
                        style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
                    OutlinedButton(onClick = onEditScenario, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Voltar ao cenário")
                    }
                }
            }
        } else {
            item { Text("ESCOLHER EQUIPA", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelLarge) }
            items(teams, key = { "select:${it.id}" }) { choice ->
                Surface(onClick = { selectedId = choice.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    color = if (team?.id == choice.id) PvpColors.SurfaceRaised else PvpColors.SurfaceCard,
                    shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = team?.id == choice.id, onClick = { selectedId = choice.id })
                        Spacer(Modifier.width(8.dp))
                        Text(choice.name, modifier = Modifier.weight(1f), maxLines = 2)
                        if (choice.isPrimary) Text("PRINCIPAL", style = MaterialTheme.typography.labelSmall,
                            color = PvpColors.BrandYellow)
                    }
                }
            }
            projection?.let { projectionValue ->
                item {
                    CoverageCard {
                        Text("${projectionValue.evaluated}/3 membros avaliados", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text("${projectionValue.winsInOneVsOne} resultados WIN individuais · " +
                            "${projectionValue.unavailable} sem rating", color = PvpColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                        Text(projectionValue.explanation, color = PvpColors.StateWarning,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(projectionValue.members, key = { "member:${it.ownedPokemonId}" }) { member ->
                    CoverageCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val name = member.speciesId?.let { names[it] ?: it } ?: "Exemplar indisponível"
                            if (member.speciesId != null) PokemonArtwork(name, Modifier.size(48.dp))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text(when (member.role) {
                                    TeamRole.LEAD -> "LEAD"
                                    TeamRole.SAFE_SWITCH -> "SAFE SWITCH"
                                    TeamRole.CLOSER -> "CLOSER"
                                }, style = MaterialTheme.typography.labelSmall, color = PvpColors.TextSecondary)
                            }
                            member.rating?.let { Text("$it/1000", color = PvpColors.BrandBlue,
                                style = MaterialTheme.typography.labelLarge) }
                        }
                        Text(when (member.state) {
                            B04MemberState.EVALUATED -> "1v1 · ${member.outcome}"
                            B04MemberState.EXCLUDED -> "EXCLUÍDO"
                            B04MemberState.UNAVAILABLE -> "NÃO AVALIADO"
                        }, color = if (member.state == B04MemberState.EVALUATED) PvpColors.StateSuccess else PvpColors.StateWarning,
                            style = MaterialTheme.typography.labelMedium)
                        Text(member.detail, style = MaterialTheme.typography.bodySmall,
                            color = PvpColors.TextSecondary)
                    }
                }
            }
            item {
                OutlinedButton(onClick = onEditScenario, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Alterar adversário / condições")
                }
            }
        }
    }
}

@Composable
private fun CoverageCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), color = PvpColors.SurfaceCard,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
