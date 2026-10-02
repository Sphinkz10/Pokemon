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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.TeamLabResult
import com.rui.pvpgo.domain.TeamLabStatus
import com.rui.pvpgo.domain.TeamLabAlternative
import com.rui.pvpgo.domain.SuggestedTeamRole
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.ui.theme.PvpColors
import java.util.Locale

/** T04/T05/T06. The recommendations displayed here ALWAYS come from TeamLabResult. */
enum class TeamsGoldenAnalysisPage { RESULTS, IDEAL, ADJUST }

private fun coverage(value: Double): String = String.format(Locale.US, "%.1f%%", value)
private fun leagueLabel(league: League): String = when (league) {
    League.GREAT -> "Great League"
    League.ULTRA -> "Ultra League"
    League.MASTER -> "Master League"
    else -> league.name
}

@Composable
private fun GoldenTeamPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = PvpColors.SurfaceCard,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun GoldenTeamTitle(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(onClick = onBack, modifier = Modifier.size(48.dp), color = PvpColors.SurfaceCard,
            shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
            Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PvpColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
        }
    }
}

@Composable
private fun GoldenTeamLineup(ids: List<String>, owned: List<OwnedPokemon>, catalog: List<PokemonSpecies>) {
    val byId = remember(owned) { owned.associateBy { it.id } }
    val names = remember(catalog) { catalog.associate { it.speciesId to it.name } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..2).forEach { index ->
            val id = ids.getOrNull(index)
            val mon = id?.let { byId[it] }
            val name = mon?.let { names[it.speciesId] ?: it.speciesId } ?: if (id == null) "Por preencher" else "Indisponível"
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                PokemonArtwork(name, Modifier.size(52.dp))
                Text(name, style = MaterialTheme.typography.bodySmall, color = PvpColors.TextPrimary, maxLines = 2)
                Text(listOf("Posição 1", "Posição 2", "Posição 3")[index], style = MaterialTheme.typography.labelSmall, color = PvpColors.TextSecondary)
            }
        }
    }
}

@Composable
fun TeamsGoldenAnalysisScreen(
    page: TeamsGoldenAnalysisPage, result: TeamLabResult, league: League,
    owned: List<OwnedPokemon>, catalog: List<PokemonSpecies>, selectedIds: List<String>,
    onBack: () -> Unit, onShowIdeal: () -> Unit, onShowAdjust: () -> Unit,
    onShowResults: () -> Unit, onSelect: (List<String>) -> Unit,
    onSave: (List<String>) -> Unit, savedMessage: String?
) {
    val primary = result.primary ?: return
    if (result.status != TeamLabStatus.READY) return
    val primaryIds = primary.memberOwnedPokemonIds
    val variants = result.alternatives.map { it.memberOwnedPokemonIds }
    val currentIds = if (TeamsGoldenResultPolicy.isVerified(primaryIds, variants, selectedIds)) selectedIds else primaryIds
    val current = result.alternatives.firstOrNull { it.memberOwnedPokemonIds == currentIds }
    val score = current?.coverageScorePercent ?: primary.coverage.coverageScorePercent
    val idealIds = TeamsGoldenResultPolicy.highestCoverage(primaryIds, primary.coverage.coverageScorePercent,
        result.alternatives.map { it.memberOwnedPokemonIds to it.coverageScorePercent })
    val idealAlt = result.alternatives.firstOrNull { it.memberOwnedPokemonIds == idealIds }
    val idealCoverage = idealAlt?.coverageScorePercent ?: primary.coverage.coverageScorePercent
    val verifiedSingleSwaps = result.alternatives.filter {
        TeamsGoldenResultPolicy.singleSwap(primaryIds, it.memberOwnedPokemonIds)
    }.distinctBy { it.memberOwnedPokemonIds }
    val completeIds = remember(owned) { TeamsGoldenPolicy.completeCandidates(owned).map { it.id }.toSet() }
    val isComplete = currentIds.all { it in completeIds }
    val originalRoles = result.roleSuggestions.associate {
        it.ownedPokemonId to when (it.role) {
            SuggestedTeamRole.LEAD -> "LEAD"
            SuggestedTeamRole.SAFE_SWITCH -> "SAFE_SWITCH"
            SuggestedTeamRole.CLOSER -> "CLOSER"
        }
    }
    val canSave = isComplete && TeamsGoldenResultPolicy.rolesForVerifiedSingleSwap(primaryIds, variants, currentIds, originalRoles) != null
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            GoldenTeamTitle(if (page == TeamsGoldenAnalysisPage.ADJUST) "Ajustar equipa" else "Sugestões",
                leagueLabel(league) + " · análise com Meta Pack", onBack)
        }
        if (page != TeamsGoldenAnalysisPage.ADJUST) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = page == TeamsGoldenAnalysisPage.RESULTS,
                        modifier = Modifier.weight(1f),
                        onClick = onShowResults, label = { Text("Posso usar agora", style = MaterialTheme.typography.labelMedium, maxLines = 2) })
                    FilterChip(selected = page == TeamsGoldenAnalysisPage.IDEAL,
                        modifier = Modifier.weight(1f),
                        onClick = onShowIdeal, label = { Text("Melhor possível", style = MaterialTheme.typography.labelMedium, maxLines = 2) })
                }
            }
        }
        when (page) {
            TeamsGoldenAnalysisPage.RESULTS -> {
                item {
                    GoldenTeamPanel {
                        Text("Sugestões validadas pela tua coleção", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("1 principal + ${result.alternatives.size} alternativas do Team Lab. Ordenadas pelo motor; cobertura 1v1 não é probabilidade de vitória 3v3.",
                            style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
                    }
                }
                item {
                    Text("EQUIPA SELECIONADA", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                    GoldenTeamPanel {
                        Text(if (current == null) "Equipa principal" else "Alternativa · ${current.kind.name.lowercase().replace('_', ' ')}",
                            style = MaterialTheme.typography.titleLarge)
                        GoldenTeamLineup(currentIds, owned, catalog)
                        Text("Cobertura calculada: ${coverage(score)} · ranking 1v1", style = MaterialTheme.typography.bodyMedium)
                        val explanation = current?.reason ?: result.why?.summary
                        if (!explanation.isNullOrBlank()) Text(explanation, style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
                        savedMessage?.let { Text(it, color = PvpColors.StateSuccess) }
                        Button(onClick = { onSave(currentIds) }, enabled = canSave,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Guardar equipa") }
                        OutlinedButton(onClick = onShowAdjust, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ajustar") }
                        if (!canSave) Text("Guardar indisponível: papel dos três membros ou exemplar completo não verificável nesta alternativa.",
                            color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (result.alternatives.isNotEmpty()) {
                    item { Text("OUTRAS SUGESTÕES", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelLarge) }
                    items(result.alternatives, key = { "team:${it.kind}:${it.memberOwnedPokemonIds.joinToString()}" }) { alt ->
                        GoldenTeamPanel {
                            Text(alt.kind.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                            Text("Cobertura ${coverage(alt.coverageScorePercent)} · ${alt.reason}", color = PvpColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onSelect(alt.memberOwnedPokemonIds) },
                                modifier = Modifier.heightIn(min = 44.dp)) { Text("Ver esta equipa ›") }
                        }
                    }
                }
            }
            TeamsGoldenAnalysisPage.IDEAL -> {
                item {
                    GoldenTeamPanel {
                        Text("Teto verificado com a coleção atual", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("O motor não calcula aqui uma equipa futura com Pokémon que ainda não tens. Não são apresentados alvos hipotéticos.",
                            style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
                        GoldenTeamLineup(idealIds, owned, catalog)
                        Text("Cobertura calculada ${coverage(idealCoverage)}", color = PvpColors.TextPrimary)
                        if (idealIds == primaryIds) Text("A opção principal já é a de maior cobertura entre as variantes calculadas.",
                            style = MaterialTheme.typography.bodySmall)
                        else Text("Existe uma alternativa com maior cobertura 1v1 neste conjunto de resultados.",
                            style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onSelect(idealIds) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Ver equipa calculada")
                        }
                    }
                }
            }
            TeamsGoldenAnalysisPage.ADJUST -> {
                item {
                    GoldenTeamPanel {
                        Text("Ajustar com alternativas verificadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        GoldenTeamLineup(primaryIds, owned, catalog)
                        Text("As trocas mostradas preservam dois membros da equipa principal e já foram calculadas pelo motor para a mesma liga e Meta Pack.",
                            style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
                    }
                }
                if (verifiedSingleSwaps.isEmpty()) {
                    item { GoldenTeamPanel {
                        Text("Sem troca simples validada", fontWeight = FontWeight.SemiBold)
                        Text("Volta ao Builder para alterar restrições e voltar a analisar; não vamos sugerir um Pokémon sem validação.",
                            style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
                    } }
                } else {
                    item { Text("ALTERNATIVAS", color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelLarge) }
                    items(verifiedSingleSwaps, key = { "swap:${it.kind}" }) { alt ->
                        GoldenTeamPanel {
                            val inserted = alt.memberOwnedPokemonIds.single { it !in primaryIds }
                            val mon = owned.firstOrNull { it.id == inserted }
                            val name = catalog.firstOrNull { it.speciesId == mon?.speciesId }?.name ?: mon?.speciesId ?: "Exemplar indisponível"
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PokemonArtwork(name, Modifier.size(44.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.SemiBold)
                                    Text("Cobertura ${coverage(alt.coverageScorePercent)} · troca de 1 membro", color = PvpColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { onSelect(alt.memberOwnedPokemonIds) },
                                    modifier = Modifier.heightIn(min = 44.dp)) { Text("USAR") }
                            }
                            Text(alt.reason, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
