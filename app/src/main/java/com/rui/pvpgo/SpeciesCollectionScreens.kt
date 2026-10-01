package com.rui.pvpgo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvPRankEntry
import com.rui.pvpgo.engine.RankSettings
import com.rui.pvpgo.ui.theme.PvpColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/* C04/C05/C06 - presentation only. Ownership and write operations remain in the Room repository.
   Rankings are computed by the existing engine, never inferred from Figma sample values. */
private val speciesBlue: Color get() = PvpColors.AccentSky
private val speciesGreen: Color get() = PvpColors.AccentGreen
private val speciesAmber: Color get() = PvpColors.AccentAmber
private val speciesCard: Color get() = PvpColors.SurfaceCard
private val speciesBorder: Color get() = PvpColors.BorderDefault

@Composable
private fun rememberGreatRanks(species: PokemonSpecies, owned: List<OwnedPokemon>): Map<String, PvPRankEntry>? {
    var ranks by remember(species.speciesId, owned) { mutableStateOf<Map<String, PvPRankEntry>?>(null) }
    LaunchedEffect(species.speciesId, owned) {
        ranks = withContext(Dispatchers.Default) {
            val settings = RankSettings()
            owned.mapNotNull { item ->
                RankRepository.find(species, League.GREAT, item.iv, settings)?.let { item.id to it }
            }.toMap()
        }
    }
    return ranks
}

private fun nameFor(item: OwnedPokemon, species: PokemonSpecies): String =
    item.nickname?.takeIf { it.isNotBlank() } ?: species.name

private fun ivText(item: OwnedPokemon): String = "${item.iv.attack}/${item.iv.defense}/${item.iv.stamina}"
private fun cpText(item: OwnedPokemon): String = item.cp?.let { "CP $it" } ?: "CP por confirmar"
private fun percentText(n: Double): String = String.format(Locale.US, "%.1f", n)
@Composable
private fun CollectionDetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(48.dp).clickable(onClick = onBack),
            shape = RoundedCornerShape(14.dp),
            color = PvpColors.CanvasMiddle,
            border = BorderStroke(1.dp, speciesBorder)
        ) { Box(contentAlignment = Alignment.Center) { Text("‹", color = PvpColors.TextPrimary, fontSize = 30.sp) } }
        Column(Modifier.weight(1f)) {
            Text(title, color = PvpColors.TextPrimary, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = PvpColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CollectionDetailCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), color = speciesCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, speciesBorder)) {
        Box(Modifier.padding(13.dp)) { content() }
    }
}

@Composable
private fun DetailSection(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = PvpColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (action != null && onAction != null) {
            Box(Modifier.heightIn(min = 44.dp).clickable(onClick = onAction), contentAlignment = Alignment.CenterEnd) {
                Text(action, color = speciesBlue, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DetailChip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = modifier.heightIn(min = if (onClick == null) 30.dp else 44.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = if (selected) PvpColors.AccentDeep else PvpColors.SurfaceRaised,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (selected) speciesBlue else speciesBorder)
    ) {
        Box(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (selected) speciesBlue else PvpColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SpeciesMetric(title: String, value: String, foot: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = PvpColors.SurfaceRaised, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, speciesBorder)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = PvpColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
            Text(value, color = PvpColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(foot, color = speciesGreen, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
private fun ExemplarRow(
    item: OwnedPokemon,
    species: PokemonSpecies,
    rank: PvPRankEntry?,
    plans: List<PvpBuildPlan>,
    onClick: () -> Unit,
    checked: Boolean = false
) {
    Surface(
        Modifier.fillMaxWidth().heightIn(min = 75.dp).clickable(onClick = onClick),
        color = if (checked) PvpColors.AccentDeep else speciesCard,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, if (checked) speciesBlue else speciesBorder)
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PokemonArtwork(species.name, Modifier.size(46.dp), shiny = item.isShiny, form = species.form)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(nameFor(item, species), color = PvpColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${cpText(item)} · ${ivText(item)}", color = PvpColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (rank == null) "Rank GL indisponível" else "Great League · Rank #${rank.rank}", color = speciesBlue, fontSize = 12.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (item.isFavorite) Text("♥", color = speciesAmber, fontSize = 18.sp)
                else if (SpeciesCollectionPolicy.isProtected(item, plans)) Text("PLANO", color = speciesGreen, fontSize = 12.sp)
                else if (checked) Text("✓", color = speciesBlue, fontSize = 20.sp)
                else Text("›", color = speciesBlue, fontSize = 22.sp)
            }
        }
    }
}

@Composable
fun SpeciesOverviewScreen(
    species: PokemonSpecies,
    owned: List<OwnedPokemon>,
    plans: List<PvpBuildPlan>,
    onBack: () -> Unit,
    onOpenExemplars: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenOwned: (String) -> Unit,
    onCompare: (String, String) -> Unit
) {
    val ranks = rememberGreatRanks(species, owned)
    val best = SpeciesCollectionPolicy.bestForGreat(owned, ranks)
    val favorite = owned.count { it.isFavorite }
    val hundo = owned.count { it.iv.total == 45 }
    val sorted = SpeciesCollectionPolicy.sortedForGreat(owned, ranks)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { CollectionDetailHeader(species.name, "Espécie · coleção + utilidade", onBack) }
        item {
            CollectionDetailCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PokemonArtwork(species.name, Modifier.size(82.dp), form = species.form)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(species.name, color = PvpColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(species.types.joinToString(" · ").ifBlank { "Tipos por confirmar" }, color = PvpColors.TextSecondary, fontSize = 12.sp)
                        Text("Tens ${owned.size} · $favorite favorito(s)", color = speciesBlue, fontSize = 12.sp)
                        Text(if (best == null) "Sem ranking GL calculado" else "Melhor Great League · Rank #${ranks?.get(best.id)?.rank}", color = PvpColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeciesMetric("MELHOR GL", if (ranks == null) "…" else best?.let { "#${ranks[it.id]?.rank}" } ?: "—", "da tua coleção", Modifier.weight(1f))
                SpeciesMetric("100% IV", hundo.toString(), "exemplares", Modifier.weight(1f))
                SpeciesMetric("PLANOS", plans.count { p -> owned.any { it.id == p.ownedPokemonId } }.toString(), "associados", Modifier.weight(1f))
            }
        }
        item { DetailSection("OS TEUS EXEMPLARES", "Ver todos ›", onOpenExemplars) }
        if (owned.isEmpty()) item { CollectionDetailCard { Text("Ainda não tens exemplares desta espécie.", color = PvpColors.TextSecondary) } }
        else items(sorted.take(3), key = { "overview-${it.id}" }) { item ->
            ExemplarRow(item, species, ranks?.get(item.id), plans, onClick = { onOpenOwned(item.id) })
        }
        item { DetailSection("PARA QUE SERVE") }
        item {
            CollectionDetailCard {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Great League", color = PvpColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(if (best == null) "Consulta os IV Targets para encontrar um exemplar GL." else "O teu melhor exemplar calculado é Rank #${ranks?.get(best.id)?.rank}. O Rank PvP não equivale a 100% IV.", color = PvpColors.TextSecondary, fontSize = 13.sp)
                    DetailChip("Ver IV Targets ›", onClick = onOpenTargets)
                }
            }
        }
        if (owned.size >= 2) item {
            DetailChip("Comparar os dois melhores ›", onClick = {
                // Deterministic selection: GL rank if present, otherwise stable collection order.
                onCompare(sorted[0].id, sorted[1].id)
            })
        }
    }
}

private enum class ExemplarsFilter(val label: String) { ALL("Todos"), PVP("PvP"), FAVORITES("Favoritos"), REVIEW("Rever") }

@Composable
fun SpeciesExemplarsScreen(
    species: PokemonSpecies,
    owned: List<OwnedPokemon>,
    plans: List<PvpBuildPlan>,
    onBack: () -> Unit,
    onOpenOwned: (String) -> Unit,
    onCompare: (String, String) -> Unit
) {
    val ranks = rememberGreatRanks(species, owned)
    var filter by remember(species.speciesId) { mutableStateOf(ExemplarsFilter.ALL) }
    var selected by remember(species.speciesId) { mutableStateOf<Set<String>>(emptySet()) }
    val visibleIds = owned.map { it.id }.toSet()
    LaunchedEffect(visibleIds) { selected = selected.intersect(visibleIds) }
    val filtered = owned.filter { item -> when (filter) {
        ExemplarsFilter.ALL -> true
        ExemplarsFilter.PVP -> (ranks?.get(item.id)?.rank ?: Int.MAX_VALUE) <= 100 || plans.any { it.ownedPokemonId == item.id }
        ExemplarsFilter.FAVORITES -> item.isFavorite
        ExemplarsFilter.REVIEW -> item.uncertainFields.isNotEmpty() || (!item.isFavorite && plans.none { it.ownedPokemonId == item.id })
    } }.let { SpeciesCollectionPolicy.sortedForGreat(it, ranks) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CollectionDetailHeader(species.name, "${owned.size} exemplares na tua coleção", onBack) }
        item {
            CollectionDetailCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Escolhe pelo uso, não só pela percentagem", color = PvpColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("O Rank Great League e os IV gerais medem coisas diferentes. Favoritos e Pokémon com planos devem ser revistos antes de qualquer transferência.", color = PvpColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ExemplarsFilter.entries.forEach { option -> DetailChip(option.label, selected = filter == option, onClick = { filter = option }) }
            }
        }
        item { DetailSection("EXEMPLARES", "${selected.size}/2 selecionados") }
        if (ranks == null) item { Text("A calcular ranks…", color = PvpColors.TextSecondary, fontSize = 12.sp) }
        if (filtered.isEmpty()) item { CollectionDetailCard { Text("Sem exemplares para este filtro.", color = PvpColors.TextSecondary) } }
        else items(filtered, key = { "exemplar-${it.id}" }) { item ->
            ExemplarRow(item, species, ranks?.get(item.id), plans, checked = item.id in selected, onClick = {
                selected = if (item.id in selected) selected - item.id else if (selected.size < 2) selected + item.id else setOf(item.id)
            })
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DetailChip("Comparar ${selected.size}/2", selected = selected.size == 2, onClick = if (selected.size == 2) {
                    { val ids = selected.toList().sorted(); onCompare(ids[0], ids[1]) }
                } else null)
                if (selected.size == 1) {
                    DetailChip("Abrir / editar exemplar selecionado ›", onClick = { onOpenOwned(selected.first()) })
                }
            }
        }
        item { DetailSection("PROTEÇÃO DOS DADOS") }
        item {
            CollectionDetailCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${owned.count { SpeciesCollectionPolicy.isProtected(it, plans) }} protegido(s)", color = speciesGreen, fontWeight = FontWeight.Bold)
                    Text("Não existem ações automáticas de transferência. O filtro Rever é apenas uma fila de revisão, não uma indicação de segurança para transferir.", color = PvpColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SpeciesCompareScreen(
    species: PokemonSpecies,
    first: OwnedPokemon,
    second: OwnedPokemon,
    plans: List<PvpBuildPlan>,
    onBack: () -> Unit
) {
    val two = remember(first, second) { listOf(first, second) }
    val ranks = rememberGreatRanks(species, two)
    val a = ranks?.get(first.id)
    val b = ranks?.get(second.id)
    val betterGl = SpeciesCollectionPolicy.betterGreatRank(first, second, ranks)
    val totalA = first.iv.total
    val totalB = second.iv.total
    val betterIv = SpeciesCollectionPolicy.higherIvTotal(first, second)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { CollectionDetailHeader("Comparar", "${species.name} · 2 exemplares", onBack) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(first to "A", second to "B").forEach { (item, label) ->
                    Surface(
                        Modifier.weight(1f), color = speciesCard, shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, if (label == betterGl) speciesBlue else speciesBorder)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
                            Text(label, color = speciesBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            PokemonArtwork(species.name, Modifier.size(57.dp), shiny = item.isShiny, form = species.form)
                            Text(ivText(item), color = PvpColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(cpText(item), color = PvpColors.TextSecondary, fontSize = 12.sp)
                            Text(if (ranks == null) "A calcular…" else ranks[item.id]?.let { "Rank GL #${it.rank}" } ?: "Sem Rank GL", color = speciesBlue, fontSize = 12.sp)
                            if (SpeciesCollectionPolicy.isProtected(item, plans)) Text("PROTEGIDO", color = speciesGreen, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { DetailSection("QUAL É MELHOR?") }
        item {
            CollectionDetailCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Depende do objetivo", color = PvpColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        when (betterGl) {
                            "A", "B" -> "Para Great League, o exemplar $betterGl tem o Rank calculado superior."
                            "Empate" -> "Os dois têm o mesmo Rank Great League."
                            else -> "A avaliação Great League está a ser calculada ou indisponível."
                        } + " Para IV gerais, ${if (betterIv == "Empate") "há empate" else "o exemplar $betterIv tem maior soma dos IV"}. Estes critérios não medem tudo sobre desempenho, moves ou investimento.",
                        color = PvpColors.TextSecondary, fontSize = 13.sp
                    )
                    Text("Não é emitida recomendação automática de transferência.", color = speciesAmber, fontSize = 12.sp)
                }
            }
        }
        item { DetailSection("DIFERENÇAS") }
        item { ComparisonMetricRow("Great League", a?.let { "A · #${it.rank}" } ?: "A · —", b?.let { "B · #${it.rank}" } ?: "B · —", betterGl) }
        item { ComparisonMetricRow("Soma dos IV", "A · $totalA/45", "B · $totalB/45", betterIv) }
        item { ComparisonMetricRow("CP registado", first.cp?.toString() ?: "—", second.cp?.toString() ?: "—", "") }
        item { ComparisonMetricRow("Favorito", if (first.isFavorite) "A · Sim" else "A · Não", if (second.isFavorite) "B · Sim" else "B · Não", "") }
        item { ComparisonMetricRow("Plano PvP", if (plans.any { it.ownedPokemonId == first.id }) "A · Sim" else "A · Não", if (plans.any { it.ownedPokemonId == second.id }) "B · Sim" else "B · Não", "") }
    }
}

@Composable
private fun ComparisonMetricRow(title: String, a: String, b: String, emphasis: String) {
    CollectionDetailCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = PvpColors.TextSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(a, color = if (emphasis == "A") speciesBlue else PvpColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(b, color = if (emphasis == "B") speciesBlue else PvpColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
