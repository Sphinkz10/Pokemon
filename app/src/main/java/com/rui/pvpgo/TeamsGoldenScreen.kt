package com.rui.pvpgo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rui.pvpgo.domain.BuildStatus
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.domain.TeamRole
import com.rui.pvpgo.domain.TeamStyleBias
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.ui.components.PvpSearchField
import com.rui.pvpgo.ui.theme.PvpColors

/** GOLDEN T01/T02/T03: a presentation layer over actual Room data and the existing Team Lab. */
enum class TeamsGoldenView { HOME, BUILDER, DETAIL, ANALYSIS }

private val teamsCardShape = RoundedCornerShape(18.dp)
private val teamsBlue = PvpColors.BrandBlue

@Composable
private fun TeamsCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val click = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Surface(modifier = click.fillMaxWidth(), shape = teamsCardShape,
        color = PvpColors.SurfaceCard, border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun LeaguePicker(league: League, onChange: (League) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(League.GREAT to "Great", League.ULTRA to "Ultra", League.MASTER to "Master").forEach { (choice, label) ->
            FilterChip(selected = choice == league, onClick = { onChange(choice) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) })
        }
    }
}

@Composable
private fun TeamsSection(title: String, meta: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (meta != null) Text(meta, color = teamsBlue, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TeamsHeading(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) {
            Surface(onClick = onBack, shape = RoundedCornerShape(14.dp), color = PvpColors.SurfaceCard,
                border = BorderStroke(1.dp, PvpColors.BorderDefault), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 28.sp, color = PvpColors.TextPrimary) }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = PvpColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
        }
    }
}

@Composable
private fun TeamMemberTile(name: String, role: TeamRole?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        PokemonArtwork(name, Modifier.size(53.dp))
        Text(name, color = PvpColors.TextPrimary, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(when (role) {
            TeamRole.LEAD -> "Lead"
            TeamRole.SAFE_SWITCH -> "Safe"
            TeamRole.CLOSER -> "Closer"
            null -> "Em falta"
        }, style = MaterialTheme.typography.labelSmall, color = PvpColors.TextSecondary)
    }
}

@Composable
private fun ThreeTeamMembers(team: SavedTeam, ownedById: Map<String, OwnedPokemon>, namesById: Map<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        listOf(TeamRole.LEAD, TeamRole.SAFE_SWITCH, TeamRole.CLOSER).forEach { role ->
            val member = team.members.firstOrNull { it.role == role }
            val owned = member?.let { ownedById[it.ownedPokemonId] }
            val name = owned?.let { namesById[it.speciesId] ?: it.speciesId } ?: "Em falta"
            TeamMemberTile(name, role, Modifier.weight(1f))
        }
    }
}

@Composable
fun TeamsGoldenHomeScreen(
    catalog: List<PokemonSpecies>, owned: List<OwnedPokemon>, plans: List<PvpBuildPlan>,
    teams: List<SavedTeam>, league: League, onLeagueChange: (League) -> Unit,
    onCreate: () -> Unit, onOpenTeam: (String) -> Unit
) {
    val names = remember(catalog) { catalog.associate { it.speciesId to it.name } }
    val ownedById = remember(owned) { owned.associateBy { it.id } }
    val scoped = remember(teams, league) { TeamsGoldenPolicy.forLeague(teams, league) }
    val eligible = remember(owned) { TeamsGoldenPolicy.completeCandidates(owned) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TeamsHeading("Equipas", "Sugestões baseadas na tua coleção") }
        item { LeaguePicker(league, onLeagueChange) }
        item { TeamsSection("PARA TI", when (league) { League.GREAT -> "Great League"; League.ULTRA -> "Ultra League"; League.MASTER -> "Master League"; else -> league.name }) }
        if (scoped.isEmpty()) {
            item {
                TeamsCard {
                    Text("A tua primeira equipa", style = MaterialTheme.typography.titleLarge)
                    Text("Ainda não tens equipas guardadas nesta liga. O Team Lab pode gerar sugestões a partir de Pokémon completos e de um Meta Pack válido.", color = PvpColors.TextSecondary)
                    Text("${eligible.size} Pokémon com nível e moves preenchidos", style = MaterialTheme.typography.labelMedium, color = teamsBlue)
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Criar equipa") }
                }
            }
        } else {
            val primary = scoped.first()
            item {
                TeamsCard(onClick = { onOpenTeam(primary.id) }) {
                    Text("EQUIPA GUARDADA", color = PvpColors.BrandYellow, style = MaterialTheme.typography.labelSmall)
                    Text(primary.name, style = MaterialTheme.typography.titleLarge)
                    ThreeTeamMembers(primary, ownedById, names)
                    Text(TeamsGoldenPolicy.summary(primary, ownedById, plans), color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Ver equipa  ›", color = teamsBlue, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (scoped.size > 1) {
                item { TeamsSection("OUTRAS EQUIPAS", "${scoped.size - 1} guardadas") }
                items(scoped.drop(1), key = { it.id }) { team ->
                    TeamsCard(onClick = { onOpenTeam(team.id) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(team.name, fontWeight = FontWeight.SemiBold)
                                Text(TeamsGoldenPolicy.memberNames(team, ownedById, names), color = PvpColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            Text("›", fontSize = 22.sp, color = teamsBlue)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Gerar nova equipa com Team Lab")
                }
            }
        }
        item { Text("Só aparecem equipas efetivamente guardadas. Nenhuma cobertura, matchup ou probabilidade é estimada nesta lista sem análise.",
            color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun TeamsGoldenDetailScreen(
    team: SavedTeam, owned: List<OwnedPokemon>, catalog: List<PokemonSpecies>,
    plans: List<PvpBuildPlan>, onBack: () -> Unit, onAdjust: () -> Unit
) {
    val names = remember(catalog) { catalog.associate { it.speciesId to it.name } }
    val ownedById = remember(owned) { owned.associateBy { it.id } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TeamsHeading(team.name, "${team.league.name.lowercase().replaceFirstChar { it.uppercase() }} League · equipa guardada", onBack) }
        item {
            TeamsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("A TUA EQUIPA", fontWeight = FontWeight.Bold)
                    Text(if (team.isPrimary) "PRINCIPAL" else "GUARDADA", color = PvpColors.StateSuccess,
                        style = MaterialTheme.typography.labelMedium)
                }
                ThreeTeamMembers(team, ownedById, names)
                Text(TeamsGoldenPolicy.summary(team, ownedById, plans), color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item { TeamsSection("EXEMPLARES", "3 posições") }
        items(listOf(TeamRole.LEAD, TeamRole.SAFE_SWITCH, TeamRole.CLOSER)) { role ->
            val member = team.members.firstOrNull { it.role == role }
            val specimen = member?.let { ownedById[it.ownedPokemonId] }
            TeamsCard {
                Text("${role.name.replace('_', ' ')} · ${specimen?.let { names[it.speciesId] ?: it.speciesId } ?: "Em falta na coleção"}",
                    fontWeight = FontWeight.SemiBold)
                Text(specimen?.let { "CP ${it.cp ?: "?"} · ${it.iv.attack}/${it.iv.defense}/${it.iv.stamina}" }
                    ?: "Este exemplar foi removido ou não está disponível.",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!team.notes.isNullOrBlank()) item { TeamsCard { Text("NOTAS", style = MaterialTheme.typography.labelMedium); Text(team.notes.orEmpty()) } }
        item { OutlinedButton(onClick = onAdjust, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Criar alternativa com Team Lab") } }
    }
}

@Composable
fun TeamsGoldenBuilderScreen(
    catalog: List<PokemonSpecies>, owned: List<OwnedPokemon>, plans: List<PvpBuildPlan>,
    league: League, style: TeamStyleBias, selectedAnchorId: String?,
    onLeagueChange: (League) -> Unit, onStyleChange: (TeamStyleBias) -> Unit,
    onAnchorChange: (String?) -> Unit, onBack: () -> Unit, onGenerate: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    val names = remember(catalog) { catalog.associate { it.speciesId to it.name } }
    val speciesById = remember(catalog) { catalog.associateBy { it.speciesId } }
    val eligible = remember(owned) { TeamsGoldenPolicy.completeCandidates(owned) }
    val selected = eligible.firstOrNull { it.id == selectedAnchorId }
    val filtered = remember(eligible, names, speciesById, query) {
        eligible.filter { candidate ->
            val name = names[candidate.speciesId] ?: candidate.speciesId
            TeamPickerSearchPolicy.matches(candidate, name, speciesById[candidate.speciesId]?.dex, query)
        }.take(75)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TeamsHeading("Criar equipa", "Simples, assistido, sem ruído", onBack) }
        item { TeamsSection("1 · LIGA") }
        item { LeaguePicker(league, onLeagueChange) }
        item { TeamsSection("2 · POKÉMON BASE", "Opcional") }
        item {
            TeamsCard {
                Text(selected?.let { names[it.speciesId] ?: it.speciesId } ?: "Deixa a app escolher",
                    style = MaterialTheme.typography.titleMedium)
                Text(selected?.let { "CP ${it.cp ?: "?"} · ${it.iv.attack}/${it.iv.defense}/${it.iv.stamina}" }
                    ?: "Podes fixar um exemplar completo na análise; o motor continuará a validar a elegibilidade da liga.",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { pickerOpen = !pickerOpen }, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(if (pickerOpen) "Fechar lista" else "Escolher Pokémon  ›")
                }
                if (selected != null) TextButton(onClick = { onAnchorChange(null) }, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Retirar Pokémon fixado")
                }
            }
        }
        if (pickerOpen) {
            item { PvpSearchField(query, onValueChange = { query = it }, placeholder = "Nome, alcunha ou #Pokédex…") }
            item { Text("${filtered.size}${if (eligible.size > 75 && filtered.size == 75) "+" else ""} de ${eligible.size} exemplares apresentados · máximo 75", style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary) }
            if (filtered.isEmpty()) item { TeamsCard { Text("Sem exemplares com nível e moves completos.") } }
            items(filtered, key = { it.id }) { pokemon ->
                TeamsCard(onClick = { onAnchorChange(pokemon.id); pickerOpen = false }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PokemonArtwork(names[pokemon.speciesId] ?: pokemon.speciesId, Modifier.size(42.dp))
                        Column(Modifier.weight(1f)) {
                            Text(names[pokemon.speciesId] ?: pokemon.speciesId, fontWeight = FontWeight.SemiBold)
                            Text("CP ${pokemon.cp ?: "?"} · ${pokemon.iv.attack}/${pokemon.iv.defense}/${pokemon.iv.stamina}",
                                color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("ESCOLHER", style = MaterialTheme.typography.labelSmall, color = teamsBlue)
                    }
                }
            }
        }
        item { TeamsSection("3 · ESTILO") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TeamStyleBias.BALANCED to "Equilibrado", TeamStyleBias.BULKY to "Mais segura", TeamStyleBias.AGGRESSIVE to "Pressão").forEach { (s, label) ->
                    FilterChip(selected = s == style, onClick = { onStyleChange(s) }, label = { Text(label) })
                }
            }
        }
        item {
            TeamsCard {
                Text("${when (league) { League.GREAT -> "Great League"; League.ULTRA -> "Ultra League"; League.MASTER -> "Master League"; else -> league.name }} · ${when (style) { TeamStyleBias.BALANCED -> "Equilibrada"; TeamStyleBias.BULKY -> "Mais segura"; TeamStyleBias.AGGRESSIVE -> "Pressão" }}",
                    fontWeight = FontWeight.Bold)
                Text("A análise usa apenas os Pokémon da tua coleção e os dados de Meta Pack disponíveis. Pode recusar se faltar informação.",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Gerar sugestões")
                }
            }
        }
    }
}
