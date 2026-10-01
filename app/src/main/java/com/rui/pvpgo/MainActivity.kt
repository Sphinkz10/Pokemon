package com.rui.pvpgo

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.rui.pvpgo.ui.components.PvpScreen
import com.rui.pvpgo.ui.theme.PvpColors
import com.rui.pvpgo.ui.theme.PvpTheme
import com.rui.pvpgo.ui.theme.PvpSkin
import com.rui.pvpgo.ui.theme.PvpSkinPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.engine.*
import com.rui.pvpgo.events.CalendarSnapshot
import com.rui.pvpgo.events.EventCalendarRepository
import com.rui.pvpgo.location.JsonFeedSpawnProvider
import com.rui.pvpgo.location.MockSpawnProvider
import com.rui.pvpgo.location.ProviderHealth
import com.rui.pvpgo.location.ProviderHealthMonitor
import com.rui.pvpgo.location.RadarTarget
import com.rui.pvpgo.location.RadarTargetStore
import com.rui.pvpgo.location.RadarWatchScheduler
import com.rui.pvpgo.location.RankedSpawn
import com.rui.pvpgo.location.SpawnFeedStore
import com.rui.pvpgo.location.SpawnMatcher
import com.rui.pvpgo.location.SpawnTextParser
import com.rui.pvpgo.notifications.RadarNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var requestedSpeciesId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedSpeciesId = intent.getStringExtra("speciesId")
        RadarNotifier.ensureChannel(this)
        RadarWatchScheduler.sync(this)
        EventCalendarRepository.schedule(this)
        PvpColors.useSkin(PvpSkinPreferences.load(this))
        setContent {
            PvPGoApp(
                initialSpeciesId = requestedSpeciesId,
                onDeepLinkConsumed = { requestedSpeciesId = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedSpeciesId = intent.getStringExtra("speciesId")
    }
}

private enum class AppTab(val label: String, val glyph: String) {
    HOME("Hoje", "⌂"),
    COLLECTION("Coleção", "▣"),
    TEAMS("Equipas", "◉"),
    BATTLES("Batalhas", "⚔"),
    MORE("Mais", "⋯")
}

@Composable
private fun AppBottomBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    Surface(
        color = PvpColors.CanvasStart,
        border = BorderStroke(1.dp, PvpColors.BorderDefault.copy(alpha = 0.75f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val selected = current == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) PvpColors.AccentDeep else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        tab.glyph,
                        color = if (selected) PvpColors.AccentSky else PvpColors.TextSecondary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        tab.label,
                        color = if (selected) PvpColors.TextPrimary else PvpColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PvPGoApp(initialSpeciesId: String? = null, onDeepLinkConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    var skin by remember { mutableStateOf(PvpSkinPreferences.load(context)) }
    var eventCalendar by remember { mutableStateOf<CalendarSnapshot?>(null) }
    var eventRefreshToken by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<List<PokemonSpecies>>(emptyList()) }
    var moves by remember { mutableStateOf<List<PvpMove>>(emptyList()) }
    var source by remember { mutableStateOf("a carregar…") }
    var moveWarning by remember { mutableStateOf<String?>(null) }
    var sourceUpdatedAt by remember { mutableStateOf<Long?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<PokemonSpecies?>(null) }
    var selectedReturnTab by remember { mutableStateOf(AppTab.HOME) }
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var moreStartRoute by remember { mutableStateOf(MoreRoute.HOME) }
    var favorites by remember { mutableStateOf(UserStore.favorites(context)) }
    var recents by remember { mutableStateOf(UserStore.recents(context)) }

    LaunchedEffect(eventRefreshToken) {
        eventCalendar = EventCalendarRepository.load(context, forceRefresh = eventRefreshToken > 0)
    }

    LaunchedEffect(refreshToken) {
        loading = true
        val loaded = PokemonRepository.load(context, forceRefresh = refreshToken > 0)
        val loadedMoves = MoveRepository.load(context, forceRefresh = refreshToken > 0)
        catalog = loaded.pokemon
        PokemonArtworkIndex.update(loaded.pokemon)
        moves = loadedMoves.moves
        source = loaded.source
        sourceUpdatedAt = loaded.updatedAtEpochMs
        warning = loaded.warning
        moveWarning = loadedMoves.warning
        if (refreshToken > 0) RankRepository.clear()
        loading = false
    }

    LaunchedEffect(catalog, initialSpeciesId) {
        if (!initialSpeciesId.isNullOrBlank() && catalog.isNotEmpty()) {
            catalog.firstOrNull { it.speciesId == initialSpeciesId }?.let {
                selectedReturnTab = tab
                selected = it
            }
            onDeepLinkConsumed()
        }
    }

    PvpTheme(skin = skin) {
        PvpScreen {
            Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            val current = selected
            if (current != null) {
                PokemonScreen(
                    species = current,
                    catalog = catalog,
                    moves = moves,
                    moveWarning = moveWarning,
                    isFavorite = current.speciesId in favorites,
                    onToggleFavorite = {
                        favorites = UserStore.toggleFavorite(context, current.speciesId, favorites)
                    },
                    onSelectSpecies = { next ->
                        recents = UserStore.pushRecent(context, next.speciesId, recents)
                        selected = next
                    },
                    onBack = {
                        selected = null
                        tab = selectedReturnTab
                    }
                )
            } else {
                Scaffold(
                    bottomBar = {
                        AppBottomBar(tab) { next ->
                            if (next == AppTab.MORE) moreStartRoute = MoreRoute.HOME
                            tab = next
                        }
                    }
                ) { shellPadding ->
                    Box(Modifier.padding(shellPadding).fillMaxSize()) {
                        when (tab) {
                            AppTab.HOME -> TodayScreen(
                                calendar = eventCalendar,
                                catalog = catalog,
                                loading = loading,
                                onSelectPokemon = { pokemon ->
                                    recents = UserStore.pushRecent(context, pokemon.speciesId, recents)
                                    selectedReturnTab = AppTab.HOME
                                    selected = pokemon
                                },
                                onExploreEvent = {
                                    moreStartRoute = MoreRoute.EVENT
                                    tab = AppTab.MORE
                                },
                                onOpenRadar = {
                                    moreStartRoute = MoreRoute.RADAR
                                    tab = AppTab.MORE
                                },
                                onOpenAgenda = {
                                    moreStartRoute = MoreRoute.AGENDA
                                    tab = AppTab.MORE
                                },
                                onSearch = { tab = AppTab.COLLECTION }
                            )
                            AppTab.COLLECTION -> CollectionModuleScreen(catalog = catalog, moves = moves)
                            AppTab.TEAMS -> TeamLabRootScreen(catalog = catalog, moves = moves)
                            AppTab.BATTLES -> BattlesRootScreen(catalog = catalog, moves = moves)
                            AppTab.MORE -> MoreRootScreen(
                                catalog = catalog, moves = moves, startRoute = moreStartRoute,
                                selectedSkin = skin,
                                calendar = eventCalendar,
                                onRefreshCalendar = { eventRefreshToken++ },
                                onSkinSelected = { chosen ->
                                    skin = chosen
                                    PvpColors.useSkin(chosen)
                                    PvpSkinPreferences.save(context, chosen)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    catalog: List<PokemonSpecies>,
    source: String,
    sourceUpdatedAt: Long?,
    warning: String?,
    loading: Boolean,
    favorites: Set<String>,
    recents: List<String>,
    onRefresh: () -> Unit,
    onSelect: (PokemonSpecies) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val byId = remember(catalog) { catalog.associateBy { it.speciesId } }
    val results = remember(query, catalog) { PokemonSearch.find(catalog, query, 100) }
    val favoritePokemon = remember(favorites, catalog) { favorites.mapNotNull(byId::get).sortedBy { it.dex } }
    val recentPokemon = remember(recents, catalog) { recents.mapNotNull(byId::get) }
    var targetRefresh by remember { mutableIntStateOf(0) }
    val radarTargets = remember(targetRefresh) { RadarTargetStore.all(context) }

    Scaffold(topBar = { TopAppBar(title = { Text("PvP GO") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text("Encontra os IVs certos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Pesquisa qualquer Pokémon e vê os melhores IVs por liga.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Pesquisar Pokémon") },
                placeholder = { Text("Charizard, zard, #6…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (loading) "A sincronizar…" else "${catalog.size} Pokémon/formas · $source${sourceUpdatedAt?.let { " · ${formatDataAge(it)}" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRefresh, enabled = !loading) { Text("Atualizar") }
            }
            warning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (loading && catalog.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (query.isBlank() && radarTargets.isNotEmpty()) {
                AlertTargetsSummary(
                    targets = radarTargets,
                    onToggle = { key, enabled ->
                        RadarTargetStore.setEnabled(context, key, enabled)
                        targetRefresh++
                    },
                    onRemove = { key ->
                        RadarTargetStore.remove(context, key)
                        targetRefresh++
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (query.isBlank() && favoritePokemon.isNotEmpty()) {
                    item { SectionTitle("Favoritos") }
                    items(favoritePokemon, key = { "fav:${it.speciesId}" }) { PokemonRow(it, onSelect) }
                }
                if (query.isBlank() && recentPokemon.isNotEmpty()) {
                    item { SectionTitle("Recentes") }
                    items(recentPokemon, key = { "recent:${it.speciesId}" }) { PokemonRow(it, onSelect) }
                }
                item { SectionTitle(if (query.isBlank()) "Pokémon" else "Resultados · ${results.size}") }
                items(results, key = { "result:${it.speciesId}" }) { PokemonRow(it, onSelect) }
            }
        }
    }
}

@Composable
private fun AlertTargetsSummary(
    targets: List<RadarTarget>,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔔 Alertas ativos · ${targets.count { it.enabled }}", fontWeight = FontWeight.Bold)
            targets.take(3).forEach { target ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(target.speciesName, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${leagueShort(target.league)} · ${if (target.maxRank == 1) "Rank #1" else "Top ${target.maxRank}"}${if (target.bestBuddy) " · Lv51" else ""}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = target.enabled,
                        onCheckedChange = { onToggle(target.key, it) }
                    )
                    TextButton(onClick = { onRemove(target.key) }) { Text("Remover") }
                }
            }
            if (targets.size > 3) Text("+${targets.size - 3} outros alertas", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun PokemonRow(pokemon: PokemonSpecies, onSelect: (PokemonSpecies) -> Unit) {
    Card(onClick = { onSelect(pokemon) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(pokemon.name, fontWeight = FontWeight.SemiBold)
                    if (pokemon.isShadow) SmallBadge("Shadow")
                    if (pokemon.isMega) SmallBadge("Mega")
                }
                val typeText = pokemon.types.joinToString(" / ") { it.replaceFirstChar(Char::uppercase) }
                Text("#${pokemon.dex}${if (typeText.isBlank()) "" else " · $typeText"}", style = MaterialTheme.typography.bodySmall)
            }
            Text("→", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SmallBadge(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PokemonScreen(
    species: PokemonSpecies,
    catalog: List<PokemonSpecies>,
    moves: List<PvpMove>,
    moveWarning: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSelectSpecies: (PokemonSpecies) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var show500 by remember(species) { mutableStateOf(false) }
    var bestBuddy by remember(species) { mutableStateOf(false) }
    val rankSettings = remember(bestBuddy) { RankSettings(maxLevel = if (bestBuddy) 51.0 else 50.0) }
    val leagues = remember(show500) {
        buildList {
            if (show500) add(League.LITTLE)
            add(League.GREAT)
            add(League.ULTRA)
            add(League.MASTER)
        }
    }
    var rankings by remember(species, show500, bestBuddy) { mutableStateOf<Map<League, List<PvPRankEntry>>>(emptyMap()) }
    var recommendedMovesets by remember(species) { mutableStateOf<Map<League, RecommendedMoveset>>(emptyMap()) }
    var recommendationWarning by remember(species) { mutableStateOf<String?>(null) }
    var expandedLeague by remember(species) { mutableStateOf<League?>(null) }
    var topCount by remember(species) { mutableIntStateOf(10) }

    LaunchedEffect(species, show500) {
        val targetLeagues = buildList {
            if (show500) add(League.LITTLE)
            add(League.GREAT)
            add(League.ULTRA)
            add(League.MASTER)
        }
        val found = linkedMapOf<League, RecommendedMoveset>()
        val warnings = mutableListOf<String>()
        targetLeagues.forEach { league ->
            val loaded = PvpokeMovesetRepository.load(context, league)
            loaded.recommendations[species.speciesId]?.let { found[league] = it }
            loaded.warning?.let(warnings::add)
        }
        recommendedMovesets = found
        recommendationWarning = warnings.distinct().joinToString(" ").takeIf { it.isNotBlank() }
    }

    LaunchedEffect(species, show500, bestBuddy) {
        rankings = withContext(Dispatchers.Default) {
            leagues.associateWith { RankRepository.ranks(species, it, rankSettings) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(species.name)
                        Text("#${species.dex}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                actions = { TextButton(onClick = onToggleFavorite) { Text(if (isFavorite) "★" else "☆") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    species.types.forEach { SmallBadge(it.replaceFirstChar(Char::uppercase)) }
                    if (species.isShadow) SmallBadge("Shadow")
                    if (species.isMega) SmallBadge("Mega")
                }
                Spacer(Modifier.height(10.dp))
                Text("Melhores IVs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Ranking por Stat Product · IV floor 0 · máximo ${if (bestBuddy) "51" else "50"}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = show500, onClick = { show500 = !show500 }, label = { Text("500 CP") })
                    FilterChip(selected = bestBuddy, onClick = { bestBuddy = !bestBuddy }, label = { Text("Best Buddy Lv51") })
                }
                if (show500) {
                    Text("500 CP mostra o ranking matemático. A elegibilidade depende das regras de cada Little Cup.", style = MaterialTheme.typography.labelSmall)
                }
            }

            items(leagues, key = { it.name }) { league ->
                val list = rankings[league]
                LeagueCard(league, list?.firstOrNull(), expandedLeague == league) {
                    expandedLeague = if (expandedLeague == league) null else league
                }
                if (expandedLeague == league && list != null) {
                    itemTopControls(topCount = topCount, onTopCount = { topCount = it })
                    TopRanks(rows = list.take(topCount))
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MovePoolSection(
                    species = species,
                    moves = moves,
                    warning = moveWarning,
                    recommendations = recommendedMovesets,
                    recommendationWarning = recommendationWarning
                )
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                IvCalculator(species = species, catalog = catalog, rankSettings = rankSettings, onSelectSpecies = onSelectSpecies)
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                RadarSection(species = species, rankSettings = rankSettings)
            }
        }
    }
}

@Composable
private fun MovePoolSection(
    species: PokemonSpecies,
    moves: List<PvpMove>,
    warning: String?,
    recommendations: Map<League, RecommendedMoveset>,
    recommendationWarning: String?
) {
    val byId = remember(moves) { moves.associateBy { it.moveId } }
    val fast = remember(species, moves) { species.fastMoveIds.mapNotNull(byId::get) }
    val charged = remember(species, moves) { species.chargedMoveIds.mapNotNull(byId::get) }

    Text("Ataques PvP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Movepool factual desta forma. DPT/EPT comparam Fast Moves; DPE compara Charged Moves. O melhor moveset por liga será tratado separadamente pelo motor de matchups.",
        style = MaterialTheme.typography.bodySmall
    )
    warning?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall) }
    if (moves.isEmpty()) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        return
    }

    if (recommendations.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("Movesets por liga", fontWeight = FontWeight.Bold)
        Text(
            "Fonte: PvPoke Overall. São recomendações de meta/simulação atuais, separadas do teu IV Rank e do movepool factual.",
            style = MaterialTheme.typography.labelSmall
        )
        recommendations.forEach { (league, rec) ->
            val fastName = byId[rec.fastMoveId]?.name ?: rec.fastMoveId
            val chargedNames = rec.chargedMoveIds.map { byId[it]?.name ?: it }
            Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(leagueLabel(league), fontWeight = FontWeight.SemiBold)
                        rec.metaScore?.let { Text("Meta ${fmt(it)}", style = MaterialTheme.typography.labelMedium) }
                    }
                    Text(listOf(fastName).plus(chargedNames).joinToString(" + "), style = MaterialTheme.typography.bodyMedium)
                    val elite = (listOf(rec.fastMoveId) + rec.chargedMoveIds).filter { it in species.eliteMoveIds }
                    if (elite.isNotEmpty()) {
                        Text(
                            "Elite TM: ${elite.joinToString { byId[it]?.name ?: it }}",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    } else {
        recommendationWarning?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
    }

    Spacer(Modifier.height(8.dp))
    Text("Fast Moves", fontWeight = FontWeight.Bold)
    fast.forEach { move ->
        Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(move.name, fontWeight = FontWeight.SemiBold)
                        if (move.moveId in species.eliteMoveIds) SmallBadge("Elite")
                        if (MoveMath.hasStab(species, move)) SmallBadge("STAB")
                    }
                    Text("${move.type.replaceFirstChar(Char::uppercase)} · ${move.power} dano · ${move.energyGain} energia · ${move.turns} turn${if (move.turns == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                }
                Text("DPT ${fmt(move.dpt ?: 0.0)}\nEPT ${fmt(move.ept ?: 0.0)}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Charged Moves", fontWeight = FontWeight.Bold)
    charged.forEach { move ->
        Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(move.name, fontWeight = FontWeight.SemiBold)
                        if (move.moveId in species.eliteMoveIds) SmallBadge("Elite")
                        if (MoveMath.hasStab(species, move)) SmallBadge("STAB")
                    }
                    Text("${move.type.replaceFirstChar(Char::uppercase)} · ${move.power} dano · ${move.energyCost} energia", style = MaterialTheme.typography.bodySmall)
                    moveEffectText(move)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                }
                Text("DPE ${fmt(move.dpe ?: 0.0)}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    species.thirdMoveStardustCost?.let {
        Text("2.º Charged Move: ${"%,d".format(Locale.US, it)} Stardust (custo de Candy será tratado por dados próprios, sem inferência).", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun moveEffectText(move: PvpMove): String? {
    if (!move.hasStatEffect) return null
    fun stage(value: Int): String = if (value > 0) "+$value" else value.toString()
    val parts = buildList {
        if (move.attackerAttackStageDelta != 0) add("teu ATK ${stage(move.attackerAttackStageDelta)}")
        if (move.attackerDefenseStageDelta != 0) add("tua DEF ${stage(move.attackerDefenseStageDelta)}")
        if (move.opponentAttackStageDelta != 0) add("ATK adversário ${stage(move.opponentAttackStageDelta)}")
        if (move.opponentDefenseStageDelta != 0) add("DEF adversário ${stage(move.opponentDefenseStageDelta)}")
    }
    val chance = fmt(move.buffApplyChance * 100)
    return "$chance% · ${parts.joinToString(" · ")}"
}

@Composable
private fun LeagueCard(league: League, rank: PvPRankEntry?, expanded: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(leagueLabel(league), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "▲" else "▼")
            }
            if (rank == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text("#1 · ${rank.iv}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Nível ${fmtLevel(rank.level)} · ${rank.cp} CP")
                Text("ATK ${fmt(rank.stats.attack)} · DEF ${fmt(rank.stats.defense)} · HP ${rank.stats.hp}")
                Text("Stat Product ${formatProduct(rank.stats.statProduct)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun itemTopControls(topCount: Int, onTopCount: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10, 50, 100).forEach { count ->
            FilterChip(selected = topCount == count, onClick = { onTopCount(count) }, label = { Text("Top $count") })
        }
    }
}

@Composable
private fun TopRanks(rows: List<PvPRankEntry>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("#${r.rank}  ${r.iv}", fontWeight = if (r.rank == 1) FontWeight.Bold else FontWeight.Normal)
                    Text("L${fmtLevel(r.level)} · ${r.cp} · SP ${fmt(r.percentOfRank1)}% · CMP #${r.mirrorRank}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun IvCalculator(
    species: PokemonSpecies,
    catalog: List<PokemonSpecies>,
    rankSettings: RankSettings,
    onSelectSpecies: (PokemonSpecies) -> Unit
) {
    var atk by remember(species) { mutableIntStateOf(0) }
    var def by remember(species) { mutableIntStateOf(15) }
    var hp by remember(species) { mutableIntStateOf(15) }
    var result by remember(species, rankSettings) { mutableStateOf<Map<League, PvPRankEntry?>>(emptyMap()) }
    var calculating by remember(species, rankSettings) { mutableStateOf(true) }
    var showEvolutions by remember(species) { mutableStateOf(false) }
    var evolutionResults by remember(species, rankSettings) { mutableStateOf<Map<PokemonSpecies, Map<League, PvPRankEntry?>>>(emptyMap()) }
    val descendants = remember(species, catalog) { EvolutionRepository.descendants(species, catalog) }
    var keepAdvice by remember(species, rankSettings) { mutableStateOf<KeepAdvice?>(null) }

    var compareLeague by remember(species) { mutableStateOf(League.GREAT) }
    var bAtk by remember(species) { mutableIntStateOf(0) }
    var bDef by remember(species) { mutableIntStateOf(15) }
    var bHp by remember(species) { mutableIntStateOf(15) }
    var compareB by remember(species, rankSettings) { mutableStateOf<PvPRankEntry?>(null) }

    fun requestCalculation() { calculating = true }

    LaunchedEffect(atk, def, hp, species, rankSettings, calculating) {
        if (!calculating) return@LaunchedEffect
        val iv = IvSpread(atk, def, hp)
        result = withContext(Dispatchers.Default) {
            listOf(League.GREAT, League.ULTRA, League.MASTER).associateWith { league ->
                RankRepository.find(species, league, iv, rankSettings)
            }
        }
        keepAdvice = withContext(Dispatchers.Default) {
            PvPAdvisor.evaluate(species, catalog, iv, rankSettings)
        }
        if (showEvolutions && descendants.isNotEmpty()) {
            evolutionResults = withContext(Dispatchers.Default) {
                descendants.associateWith { evolved ->
                    listOf(League.GREAT, League.ULTRA, League.MASTER).associateWith { league ->
                        RankRepository.find(evolved, league, iv, rankSettings)
                    }
                }
            }
        }
        calculating = false
    }

    LaunchedEffect(species, rankSettings, compareLeague, bAtk, bDef, bHp) {
        compareB = withContext(Dispatchers.Default) {
            RankRepository.find(species, compareLeague, IvSpread(bAtk, bDef, bHp), rankSettings)
        }
    }

    Text("Calcular o meu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Introduz os IVs do appraisal. O Rank é calculado entre todas as 4.096 combinações.", style = MaterialTheme.typography.bodySmall)
    Text("CMP Rank favorece Ataque real mais alto no espelho; não substitui o Rank de Stat Product.", style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { atk = 0; def = 15; hp = 15; requestCalculation() },
            modifier = Modifier.weight(1f)
        ) { Text("0/15/15") }
        OutlinedButton(
            onClick = { atk = 15; def = 15; hp = 15; requestCalculation() },
            modifier = Modifier.weight(1f)
        ) { Text("15/15/15") }
    }
    IvSelector("Ataque", atk) { atk = it; requestCalculation() }
    IvSelector("Defesa", def) { def = it; requestCalculation() }
    IvSelector("HP", hp) { hp = it; requestCalculation() }

    if (calculating) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        Spacer(Modifier.height(6.dp))
        result.forEach { (league, rank) ->
            RankResultRow(league, rank)
        }
        keepAdvice?.let { advice ->
            Spacer(Modifier.height(10.dp))
            KeepAdviceCard(advice)
        }
    }

    if (descendants.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                showEvolutions = !showEvolutions
                if (showEvolutions) requestCalculation()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showEvolutions) "Ocultar evoluções" else "Ver estes IVs depois de evoluir")
        }

        if (showEvolutions && !calculating) {
            evolutionResults.forEach { (evolved, leagues) ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onSelectSpecies(evolved) }
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(evolved.name, fontWeight = FontWeight.Bold)
                        leagues.forEach { (league, rank) ->
                            val text = rank?.let { "${leagueShort(league)} #${it.rank} · ${fmt(it.percentOfRank1)}% · ${it.cp} CP" }
                                ?: "${leagueShort(league)} —"
                            Text(text, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Abrir →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text("Comparar dois spreads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        "A usa os IVs acima. Define B e compara Rank, Stat Product e Ataque/CMP na mesma liga.",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(League.GREAT, League.ULTRA, League.MASTER).forEach { league ->
            FilterChip(
                selected = compareLeague == league,
                onClick = { compareLeague = league },
                label = { Text(leagueShort(league)) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("A · $atk/$def/$hp", fontWeight = FontWeight.SemiBold)
    Text("B · $bAtk/$bDef/$bHp", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    IvSelector("B Ataque", bAtk) { bAtk = it }
    IvSelector("B Defesa", bDef) { bDef = it }
    IvSelector("B HP", bHp) { bHp = it }

    val compareA = result[compareLeague]
    if (compareA != null && compareB != null) {
        ComparisonCard(IvComparator.compare(compareA, compareB!!))
    } else {
        Text("Um dos spreads não é aplicável nesta liga.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun KeepAdviceCard(advice: KeepAdvice) {
    val best = advice.bestUse
    val title = when (advice.tier) {
        KeepTier.MUST_KEEP -> "🟢 GUARDA"
        KeepTier.STRONG_KEEP -> "🟢 GUARDA"
        KeepTier.KEEP_IF_USEFUL -> "🟡 VALE A PENA"
        KeepTier.LOW_PRIORITY -> "⚪ BAIXA PRIORIDADE PvP"
    }
    val explanation = when (advice.tier) {
        KeepTier.MUST_KEEP -> "Este spread é Rank #1 em pelo menos uma utilização da cadeia evolutiva."
        KeepTier.STRONG_KEEP -> "Este spread entra no Top 50 em pelo menos uma utilização da cadeia evolutiva."
        KeepTier.KEEP_IF_USEFUL -> "Tem pelo menos uma utilização entre Top 51 e Top 250."
        KeepTier.LOW_PRIORITY -> "Não aparece no Top 250 das utilizações analisadas."
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = keepTierColor(advice.tier))
            best?.let {
                Text("Melhor uso: ${it.species.name} · ${leagueShort(it.league)} · Rank #${it.rank.rank}", fontWeight = FontWeight.SemiBold)
                Text("Stat Product ${fmt(it.rank.percentOfRank1)}% do Rank #1 · CMP #${it.rank.mirrorRank}", style = MaterialTheme.typography.bodySmall)
            }
            Text(explanation, style = MaterialTheme.typography.bodySmall)
            Text("Avaliação baseada apenas nos IVs; não mede a força do Pokémon no meta atual.", style = MaterialTheme.typography.labelSmall)
            if (advice.uses.isNotEmpty()) {
                val top = advice.uses.take(3)
                HorizontalDivider(Modifier.padding(vertical = 3.dp))
                top.forEach { use ->
                    Text("${use.species.name} · ${leagueShort(use.league)} #${use.rank.rank}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(comparison: IvComparison) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Resultado da comparação", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("A · ${comparison.a.iv}")
                Text("B · ${comparison.b.iv}")
            }
            ComparisonMetric("Rank", "#${comparison.a.rank}", "#${comparison.b.rank}")
            ComparisonMetric("CMP Rank", "#${comparison.a.mirrorRank}", "#${comparison.b.mirrorRank}")
            ComparisonMetric("Stat Product", fmt(comparison.a.stats.statProduct), fmt(comparison.b.stats.statProduct))
            ComparisonMetric("Ataque", fmt(comparison.a.stats.attack), fmt(comparison.b.stats.attack))
            ComparisonMetric("Defesa", fmt(comparison.a.stats.defense), fmt(comparison.b.stats.defense))
            ComparisonMetric("HP", comparison.a.stats.hp.toString(), comparison.b.stats.hp.toString())
            ComparisonMetric("Nível / CP", "L${fmtLevel(comparison.a.level)} · ${comparison.a.cp}", "L${fmtLevel(comparison.b.level)} · ${comparison.b.cp}")
            HorizontalDivider()
            val spText = when (comparison.statProductWinner) {
                ComparisonWinner.A -> "A tem maior Stat Product."
                ComparisonWinner.B -> "B tem maior Stat Product."
                ComparisonWinner.TIE -> "Stat Product empatado."
            }
            val cmpText = when (comparison.cmpWinner) {
                ComparisonWinner.A -> "A tem mais Ataque real e vantagem de CMP num espelho equivalente."
                ComparisonWinner.B -> "B tem mais Ataque real e vantagem de CMP num espelho equivalente."
                ComparisonWinner.TIE -> "Ataque real empatado; sem vantagem de CMP por Ataque."
            }
            Text(spText, style = MaterialTheme.typography.bodySmall)
            Text(cmpText, style = MaterialTheme.typography.bodySmall)
            Text("B vs A · Δ Stat Product ${signedFmt(comparison.statProductDeltaPercentBvsA)}%", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ComparisonMetric(label: String, a: String, b: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
        Text(a, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(b, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun keepTierColor(tier: KeepTier): Color = when (tier) {
    KeepTier.MUST_KEEP, KeepTier.STRONG_KEEP -> MaterialTheme.colorScheme.primary
    KeepTier.KEEP_IF_USEFUL -> MaterialTheme.colorScheme.tertiary
    KeepTier.LOW_PRIORITY -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun RankResultRow(league: League, rank: PvPRankEntry?) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (rank == null) {
            Text("${leagueLabel(league)} · não aplicável", modifier = Modifier.padding(12.dp))
        } else {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${leagueLabel(league)} · Rank #${rank.rank}", fontWeight = FontWeight.SemiBold)
                    Text(rankVerdict(rank.rank), color = rankVerdictColor(rank.rank), style = MaterialTheme.typography.labelMedium)
                }
                Text("Stat Product ${fmt(rank.percentOfRank1)}% do #1 · L${fmtLevel(rank.level)} · ${rank.cp} CP · CMP #${rank.mirrorRank}", style = MaterialTheme.typography.bodySmall)
                Text("ATK ${fmt(rank.stats.attack)} · DEF ${fmt(rank.stats.defense)} · HP ${rank.stats.hp}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun RadarSection(
    species: PokemonSpecies,
    rankSettings: RankSettings
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var league by remember(species) { mutableStateOf(League.GREAT) }
    var maxRank by remember(species) { mutableIntStateOf(10) }
    var endpoint by remember(species) { mutableStateOf(SpawnFeedStore.endpoint(context)) }
    var endpointDraft by remember(species, endpoint) { mutableStateOf(endpoint) }
    var showFeedSettings by remember(species) { mutableStateOf(false) }
    var loading by remember(species) { mutableStateOf(false) }
    var error by remember(species) { mutableStateOf<String?>(null) }
    var providerLabel by remember(species) { mutableStateOf<String?>(null) }
    var results by remember(species) { mutableStateOf<List<RankedSpawn>>(emptyList()) }
    var health by remember(species) { mutableStateOf<ProviderHealth?>(null) }
    var autoRefresh by remember(species) { mutableStateOf(false) }
    var targets by remember(species) { mutableStateOf(RadarTargetStore.all(context)) }
    var pendingTarget by remember { mutableStateOf<RadarTarget?>(null) }

    val currentTarget = remember(targets, species.speciesId, league, maxRank, rankSettings) {
        targets.firstOrNull {
            it.speciesId == species.speciesId &&
                it.league == league &&
                it.maxRank == maxRank &&
                it.bestBuddy == (rankSettings.maxLevel > 50.0)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingTarget
        pendingTarget = null
        if (granted && target != null) {
            targets = RadarTargetStore.upsert(context, target)
            Toast.makeText(context, "Alerta PvP ativado", Toast.LENGTH_SHORT).show()
        } else if (!granted) {
            Toast.makeText(context, "Sem permissão não consigo mostrar alertas em background", Toast.LENGTH_LONG).show()
        }
    }

    suspend fun executeSearch(useDemo: Boolean = false) {
        error = null
        if (!useDemo && endpoint.isBlank()) {
            error = "Configura um feed JSON para pesquisa automática. Entretanto podes abrir uma das fontes públicas abaixo."
            return
        }
        val provider = if (useDemo) MockSpawnProvider() else JsonFeedSpawnProvider(endpoint)
        loading = true
        providerLabel = provider.displayName
        val query = withContext(Dispatchers.IO) { ProviderHealthMonitor.query(provider, species.speciesId) }
        health = query.health
        if (!query.health.ok) {
            results = emptyList()
            error = query.health.error ?: "Falha ao consultar o feed."
            loading = false
            return
        }
        val matches = withContext(Dispatchers.Default) {
            SpawnMatcher.bestMatches(
                species = species,
                spawns = query.spawns,
                league = league,
                maxRank = maxRank,
                settings = rankSettings
            )
        }
        results = matches
        if (matches.isEmpty()) error = "Nenhum ${species.name} Top $maxRank encontrado neste feed agora."
        loading = false
    }

    suspend fun analyzeClipboard() {
        error = null
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isBlank()) {
            error = "O clipboard está vazio."
            return
        }
        val spawn = SpawnTextParser.parse(text, species.speciesId, "clipboard")
        if (spawn == null) {
            error = "Não consegui reconhecer IVs e coordenadas. Exemplo aceite: 0/15/13 · CP 1200 · L19.5 · 41.123,-8.456"
            return
        }
        val ranked = withContext(Dispatchers.Default) {
            RankRepository.find(species, league, spawn.iv, rankSettings)?.let { RankedSpawn(spawn, league, it) }
        }
        if (ranked == null) {
            error = "Não foi possível classificar estes IVs para ${leagueShort(league)}."
            return
        }
        results = listOf(ranked)
        providerLabel = "clipboard"
        health = null
        if (ranked.rank.rank > maxRank) {
            error = "Este spawn é Rank #${ranked.rank.rank}; fica fora do filtro ${if (maxRank == 1) "Rank #1" else "Top $maxRank"}."
        }
    }

    LaunchedEffect(autoRefresh, endpoint, species.speciesId, league, maxRank, rankSettings) {
        if (!autoRefresh || endpoint.isBlank()) return@LaunchedEffect
        while (true) {
            executeSearch(false)
            delay(60_000)
        }
    }

    Text("Radar PvP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Procura spawns pelos IVs reais e deixa a app calcular localmente se são Rank #1, Top 10, Top 50 ou Top 100.",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(League.GREAT, League.ULTRA, League.MASTER).forEach { option ->
            FilterChip(
                selected = league == option,
                onClick = { league = option; results = emptyList(); error = null },
                label = { Text(leagueShort(option)) }
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 10, 50, 100).forEach { rank ->
            FilterChip(
                selected = maxRank == rank,
                onClick = { maxRank = rank; results = emptyList(); error = null },
                label = { Text(if (rank == 1) "Rank #1" else "Top $rank") }
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    Button(
        onClick = { scope.launch { executeSearch(false) } },
        enabled = !loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (loading) "A procurar…" else "Procurar agora")
    }
    OutlinedButton(
        onClick = { scope.launch { analyzeClipboard() } },
        enabled = !loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Analisar alerta do clipboard")
    }
    Text(
        "Aceita texto com IVs no formato 0/15/13 e coordenadas GPS; CP e nível são opcionais.",
        style = MaterialTheme.typography.labelSmall
    )

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Auto-refresh com a app aberta", fontWeight = FontWeight.SemiBold)
            Text("Atualiza aproximadamente a cada 60 s.", style = MaterialTheme.typography.labelSmall)
        }
        Switch(
            checked = autoRefresh,
            onCheckedChange = { enabled ->
                if (enabled && endpoint.isBlank()) {
                    error = "Configura primeiro um feed JSON."
                } else {
                    autoRefresh = enabled
                }
            }
        )
    }

    val target = RadarTarget(
        speciesId = species.speciesId,
        speciesName = species.name,
        league = league,
        maxRank = maxRank,
        bestBuddy = rankSettings.maxLevel > 50.0
    )
    OutlinedButton(
        onClick = {
            if (currentTarget != null) {
                targets = RadarTargetStore.remove(context, currentTarget.key)
                Toast.makeText(context, "Alerta removido", Toast.LENGTH_SHORT).show()
            } else if (endpoint.isBlank()) {
                error = "Configura primeiro um feed JSON para poderes monitorizar em background."
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                pendingTarget = target
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                targets = RadarTargetStore.upsert(context, target)
                Toast.makeText(context, "Alerta PvP ativado", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (currentTarget != null) "Desativar alerta em background" else "🔔 Alertar em background")
    }
    Text(
        "Background: o Android verifica periodicamente com WorkManager (mínimo 15 min; pode atrasar por bateria/Doze).",
        style = MaterialTheme.typography.labelSmall
    )

    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    health?.let { ProviderHealthCard(it) }
    error?.let {
        Text(
            it,
            color = if (results.isEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    providerLabel?.let { label ->
        if (results.isNotEmpty()) Text("Fonte: $label", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
    }

    results.forEach { ranked -> RadarResultCard(ranked) }

    Spacer(Modifier.height(8.dp))
    Text("Fontes live", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(
        "Enquanto não há uma API pública universal, podes consultar diretamente serviços que mostram spawns live e depois ligar um feed JSON quando tiveres um disponível.",
        style = MaterialTheme.typography.bodySmall
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { openUrl(context, "https://pokexperience.com/pokemon-finder/") },
            modifier = Modifier.weight(1f)
        ) { Text("PokeXperience") }
        OutlinedButton(
            onClick = { openUrl(context, "https://pokemongocoordinates.com/pokesearch/") },
            modifier = Modifier.weight(1f)
        ) { Text("PokéSearch") }
    }

    TextButton(onClick = { showFeedSettings = !showFeedSettings }) {
        Text(if (showFeedSettings) "Ocultar configuração do feed" else "Configurar feed JSON")
    }
    if (showFeedSettings) {
        OutlinedTextField(
            value = endpointDraft,
            onValueChange = { endpointDraft = it },
            label = { Text("URL do feed JSON") },
            placeholder = { Text("https://…/spawns.json") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    endpoint = endpointDraft.trim()
                    SpawnFeedStore.saveEndpoint(context, endpoint)
                    Toast.makeText(context, "Feed guardado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Guardar") }
            OutlinedButton(
                onClick = { scope.launch { executeSearch(true) } },
                modifier = Modifier.weight(1f)
            ) { Text("Testar radar") }
        }
        Text(
            "O adaptador aceita campos equivalentes a speciesId, attackIv, defenseIv, staminaIv, latitude/longitude e, opcionalmente, CP, level e expiresAt.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ProviderHealthCard(health: ProviderHealth) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Estado da fonte", fontWeight = FontWeight.SemiBold)
                Text(if (health.ok) "● Online" else "● Erro", color = if (health.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Text("${health.providerName} · ${health.latencyMs} ms", style = MaterialTheme.typography.bodySmall)
            if (health.ok) {
                Text("${health.freshSpawns} ativos · ${health.expiredSpawns} expirados · ${health.totalSpawns} recebidos", style = MaterialTheme.typography.labelSmall)
            } else {
                Text(health.error ?: "Erro desconhecido", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RadarResultCard(ranked: RankedSpawn) {
    val context = LocalContext.current
    val spawn = ranked.spawn
    val remaining = spawn.expiresAtEpochMs?.let { ((it - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) }
    Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Rank #${ranked.rank.rank} · ${ranked.rank.iv}", fontWeight = FontWeight.Bold)
                    Text(rankVerdict(ranked.rank.rank), style = MaterialTheme.typography.labelSmall, color = rankVerdictColor(ranked.rank.rank))
                }
                Text("SP ${fmt(ranked.rank.percentOfRank1)}% · CMP #${ranked.rank.mirrorRank}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            val cp = spawn.cp?.let { "$it CP" } ?: "CP —"
            val level = spawn.level?.let { "L${fmtLevel(it)}" } ?: "L—"
            Text("Apanhado: $cp · $level", style = MaterialTheme.typography.bodySmall)
            Text("Build ${leagueShort(ranked.league)}: L${fmtLevel(ranked.rank.level)} · ${ranked.rank.cp} CP", style = MaterialTheme.typography.bodySmall)
            Text(
                remaining?.let { "Expira em ~${it} min" } ?: "Expiração não fornecida pelo feed",
                style = MaterialTheme.typography.bodySmall
            )
            Text("${String.format(Locale.US, "%.6f", spawn.latitude)}, ${String.format(Locale.US, "%.6f", spawn.longitude)}", style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { openMaps(context, spawn.latitude, spawn.longitude) },
                    modifier = Modifier.weight(1f)
                ) { Text("Abrir mapa") }
                OutlinedButton(
                    onClick = { copyCoordinates(context, spawn.latitude, spawn.longitude) },
                    modifier = Modifier.weight(1f)
                ) { Text("Copiar coords") }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun openMaps(context: Context, latitude: Double, longitude: Double) {
    val geo = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
        .onFailure { openUrl(context, "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude") }
}

private fun copyCoordinates(context: Context, latitude: Double, longitude: Double) {
    val text = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Pokémon coordinates", text))
    Toast.makeText(context, "Coordenadas copiadas", Toast.LENGTH_SHORT).show()
}

@Composable
private fun IvSelector(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value.toString(), fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }, enabled = value > 0) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.toInt().coerceIn(0, 15)) },
                valueRange = 0f..15f,
                steps = 14,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onChange((value + 1).coerceAtMost(15)) }, enabled = value < 15) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun rankVerdictColor(rank: Int): Color = when {
    rank == 1 -> MaterialTheme.colorScheme.primary
    rank <= 10 -> MaterialTheme.colorScheme.tertiary
    rank <= 50 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun rankVerdict(rank: Int): String = when {
    rank == 1 -> "IV Rank #1"
    rank <= 10 -> "Elite"
    rank <= 50 -> "Excelente"
    rank <= 100 -> "Muito bom"
    rank <= 500 -> "Bom"
    else -> "Comum"
}

private fun leagueLabel(league: League) = when (league) {
    League.LITTLE -> "500 CP"
    League.GREAT -> "Great · 1500 CP"
    League.ULTRA -> "Ultra · 2500 CP"
    League.MASTER -> "Master · sem limite"
}

private fun leagueShort(league: League) = when (league) {
    League.LITTLE -> "500"
    League.GREAT -> "Great"
    League.ULTRA -> "Ultra"
    League.MASTER -> "Master"
}

private fun formatDataAge(epochMs: Long): String {
    val ageMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    val minutes = ageMs / 60_000L
    return when {
        minutes < 2 -> "agora"
        minutes < 60 -> "há ${minutes} min"
        minutes < 24 * 60 -> "há ${minutes / 60} h"
        else -> "há ${minutes / (24 * 60)} d"
    }
}

private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
private fun signedFmt(v: Double) = String.format(Locale.US, "%+.3f", v)
private fun fmtLevel(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
private fun formatProduct(v: Double) = String.format(Locale.US, "%,d", v.toLong())
