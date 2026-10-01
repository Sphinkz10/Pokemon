package com.rui.pvpgo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvpBoxClassifier
import com.rui.pvpgo.ui.components.PvpSearchField
import com.rui.pvpgo.ui.theme.PvpColors
import java.util.Locale

private enum class CollectionViewTab(val label: String) {
    COLLECTION("Minha coleção"), POKEDEX("Pokédex")
}

private enum class CollectionQuickFilter(val label: String) {
    ALL("Todos"), PVP("PvP"), HUNDO("100%"), SHINY("Shiny"), TARGETS("Targets")
}

private val CollectionBlue: Color get() = PvpColors.AccentSky
private val CollectionGreen: Color get() = PvpColors.AccentGreen
private val CollectionAmber: Color get() = PvpColors.AccentAmber
private val CollectionCard: Color get() = PvpColors.TodayCard
private val CollectionBorder: Color get() = PvpColors.BorderDefault

@Composable
fun CollectionOverviewScreen(
    catalog: List<PokemonSpecies>,
    collection: List<OwnedPokemon>,
    buildPlans: List<PvpBuildPlan>,
    pendingCount: Int,
    onAdd: () -> Unit,
    onInbox: () -> Unit,
    onSelectOwned: (String) -> Unit,
    onOpenIvTargets: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(CollectionViewTab.COLLECTION) }
    var filter by remember { mutableStateOf(CollectionQuickFilter.ALL) }
    var sortByPriority by remember { mutableStateOf(true) }
    var generation by remember { mutableStateOf("Todas") }
    var selectedNational by remember { mutableStateOf<NationalDexEntry?>(null) }
    val context = LocalContext.current.applicationContext
    var national by remember { mutableStateOf<NationalDexLoad?>(null) }
    // Lazy-load the national catalog only when Pokédex is opened. Battle data stays in PvPoke.
    LaunchedEffect(tab, catalog) {
        if (tab == CollectionViewTab.POKEDEX) {
            national = NationalDexRepository.load(context, catalog)
            national?.let { PokemonArtworkIndex.updateNationalDex(it.entries) }
        }
    }


    val speciesById = remember(catalog) { catalog.associateBy { it.speciesId } }
    val ownedBySpecies = remember(collection) { collection.groupBy { it.speciesId } }
    val planByOwned = remember(buildPlans) {
        buildPlans.groupBy { it.ownedPokemonId }.mapValues { (_, plans) -> plans.maxByOrNull { it.priority } }
    }
    val pvpBoxByOwned = remember(collection, buildPlans) {
        PvpBoxClassifier.build(collection, buildPlans, System.currentTimeMillis()).associateBy { it.ownedPokemonId }
    }

    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val collectionItems = remember(collection, normalizedQuery, filter, speciesById, pvpBoxByOwned) {
        collection.filter { owned ->
            val species = speciesById[owned.speciesId]
            val matchesQuery = normalizedQuery.isBlank() ||
                species?.name?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true ||
                owned.nickname?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true ||
                owned.speciesId.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                species?.dex?.toString() == normalizedQuery.removePrefix("#").trimStart('0').ifBlank { "0" }
            val box = pvpBoxByOwned[owned.id]
            val matchesFilter = when (filter) {
                CollectionQuickFilter.ALL -> true
                CollectionQuickFilter.PVP -> box?.categories?.isNotEmpty() == true
                CollectionQuickFilter.HUNDO -> owned.iv.attack == 15 && owned.iv.defense == 15 && owned.iv.stamina == 15
                CollectionQuickFilter.SHINY -> owned.isShiny
                CollectionQuickFilter.TARGETS -> planByOwned.containsKey(owned.id)
            }
            matchesQuery && matchesFilter
        }.let { base ->
            if (sortByPriority) {
                base.sortedWith(compareByDescending<OwnedPokemon> { planByOwned[it.id]?.priority ?: 0 }
                    .thenBy { speciesById[it.speciesId]?.dex ?: Int.MAX_VALUE })
            } else {
                base.sortedWith(compareBy<OwnedPokemon> { speciesById[it.speciesId]?.name ?: it.speciesId }
                    .thenBy { it.cp ?: 0 })
            }
        }
    }
    val nationalItems = remember(national, normalizedQuery, generation) {
        val entries = national?.entries ?: emptyList()
        NationalDexPolicy.filter(entries, normalizedQuery, generation)
    }
    // For some forms PvPoke has multiple entries per National Dex; only base forms
    // are eligible to open IV targets from the national listing.
    val ownedCountsByDex = remember(catalog, ownedBySpecies) {
        catalog.groupBy { it.dex }.mapValues { (_, variants) ->
            variants.sumOf { ownedBySpecies[it.speciesId]?.size ?: 0 }
        }
    }
    val pvpByDex = remember(catalog) {
        catalog.filter { PokemonArtworkSources.supportedBaseForm(it.form) && it.dex > 0 }
            .associateBy { it.dex }
    }


    // Route to a national species profile irrespective of PvPoke battle coverage.
    // The list state (query, generation and position) remains alive on Back.
    selectedNational?.let { entry ->
        val battleSpecies = pvpByDex[entry.dex]
        val firstOwnedId = battleSpecies?.let { species ->
            ownedBySpecies[species.speciesId]?.firstOrNull()?.id
        }
        NationalDexDetailScreen(
            entry = entry,
            ownedCount = ownedCountsByDex[entry.dex] ?: 0,
            battleAvailable = battleSpecies != null,
            indexSource = national?.status ?: "Pokédex Nacional · fonte por confirmar",
            onBack = { selectedNational = null },
            onOpenIvTargets = battleSpecies?.let { species ->
                { selectedNational = null; onOpenIvTargets(species.speciesId) }
            },
            onOpenOwned = firstOwnedId?.let { ownedId ->
                { selectedNational = null; onSelectOwned(ownedId) }
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CollectionHeader(
                pendingCount = pendingCount,
                onAdd = onAdd
            )
        }
        item {
            CollectionTabs(tab = tab, onSelect = { next -> tab = next })
        }
        if (tab == CollectionViewTab.COLLECTION) {
            item { CollectionStatsStrip(collection) }
        }
        item {
            PvpSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = when (tab) {
                    CollectionViewTab.COLLECTION -> "Procurar na tua coleção…"
                    CollectionViewTab.POKEDEX -> "Procurar no Pokédex…"
                }
            )
        }
        if (pendingCount > 0) {
            item { PendingReviewCard(pendingCount, onInbox) }
        }
        if (tab != CollectionViewTab.POKEDEX) {
            item { CollectionFilterRow(filter, onSelect = { filter = it }) }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (choice in listOf("Todas", "G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9", "Novas")) {
                        FilterChip(
                            selected = generation == choice,
                            onClick = { generation = choice },
                            label = { Text(choice) },
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
            }
        }

        val attentionItems = collectionItems.filter { owned ->
            val categories = pvpBoxByOwned[owned.id]?.categories?.map { it.name }.orEmpty()
            owned.uncertainFields.isNotEmpty() || "NEEDS_CONFIRMATION" in categories || "IMPORTANT" in categories
        }
        val attention = attentionItems.firstOrNull()
        if (tab == CollectionViewTab.COLLECTION && attention != null) {
            val categories = pvpBoxByOwned[attention.id]?.categories?.map { it.name }.orEmpty()
            val attentionReason = when {
                attention.uncertainFields.isNotEmpty() || "NEEDS_CONFIRMATION" in categories -> "Há dados por confirmar neste exemplar."
                "IMPORTANT" in categories -> "Este exemplar tem um objetivo PvP relevante por concluir."
                else -> "Revê este exemplar e o respetivo objetivo PvP."
            }
            item {
                CollectionAttentionCard(
                    owned = attention,
                    species = speciesById[attention.speciesId],
                    count = attentionItems.size,
                    reason = attentionReason,
                    onClick = { onSelectOwned(attention.id) }
                )
            }
        }

        when (tab) {
            CollectionViewTab.COLLECTION -> {
                item {
                    CollectionSectionHeader(
                        "POKÉMON",
                        if (sortByPriority) "Ordenar ↗" else "A–Z ↗",
                        onAction = { sortByPriority = !sortByPriority }
                    )
                }
                if (collectionItems.isEmpty()) {
                    item {
                        CollectionEmptyState(
                            title = if (collection.isEmpty()) "A tua coleção está vazia" else "Sem resultados",
                            detail = if (collection.isEmpty()) "Adiciona o primeiro Pokémon. A importação continua protegida por reconciliação e confirmação humana." else "Experimenta outro nome ou remove o filtro atual.",
                            action = if (collection.isEmpty()) "ADICIONAR POKÉMON" else null,
                            onAction = onAdd
                        )
                    }
                } else {
                    items(collectionItems, key = { it.id }) { owned ->
                        CollectionOwnedRow(
                            owned = owned,
                            species = speciesById[owned.speciesId],
                            plan = planByOwned[owned.id],
                            categories = pvpBoxByOwned[owned.id]?.categories?.map { it.name }.orEmpty().toSet(),
                            onClick = { onSelectOwned(owned.id) }
                        )
                    }
                }
            }
            CollectionViewTab.POKEDEX -> {
                item {
                    CollectionSectionHeader("POKÉDEX NACIONAL", "${nationalItems.size} visíveis")
                }
                item {
                    Text(
                        national?.status ?: "A carregar índice nacional…",
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (nationalItems.isEmpty()) {
                    item {
                        CollectionEmptyState(
                            title = if (national == null) "A carregar Pokédex…" else "Sem espécies encontradas",
                            detail = if (national == null) "A obter o catálogo nacional por páginas." else "Experimenta outra pesquisa; no modo offline os dados podem estar incompletos.",
                            action = null, onAction = {}
                        )
                    }
                }
                items(nationalItems, key = { it.dex }) { entry ->
                    val battleSpecies = pvpByDex[entry.dex]
                    val ownedCount = ownedCountsByDex[entry.dex] ?: 0
                    NationalDexCollectionRow(
                        entry = entry,
                        ownedCount = ownedCount,
                        hasBattleData = battleSpecies != null,
                        onClick = { selectedNational = entry }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionStatsStrip(owned: List<OwnedPokemon>) {
    val speciesCount = remember(owned) { owned.map { it.speciesId }.distinct().size }
    val shinyCount = remember(owned) { owned.count { it.isShiny } }
    val hundoCount = remember(owned) { owned.count { it.iv.total == 45 } }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple("EXEMPLARES", owned.size.toString(), PvpColors.BrandBlue),
            Triple("ESPÉCIES", speciesCount.toString(), PvpColors.AccentSky),
            Triple("SHINY", shinyCount.toString(), PvpColors.AccentAmber),
            Triple("IV 100%", hundoCount.toString(), PvpColors.StateSuccess)
        ).forEach { (label, value, accent) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CollectionCard,
                border = BorderStroke(1.dp, CollectionBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(label, color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(value, color = accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CollectionHeader(pendingCount: Int, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Coleção",
                color = PvpColors.TextPrimary,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "O que tens e o que vale melhorar${if (pendingCount > 0) " · $pendingCount por rever" else ""}",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Surface(
            modifier = Modifier.size(48.dp).clickable(onClick = onAdd),
            shape = RoundedCornerShape(14.dp),
            color = PvpColors.SurfaceRaised,
            border = BorderStroke(1.dp, PvpColors.BorderDefault)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", color = CollectionBlue, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CollectionTabs(tab: CollectionViewTab, onSelect: (CollectionViewTab) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PvpColors.CanvasMiddle,
        border = BorderStroke(1.dp, CollectionBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CollectionViewTab.entries.forEach { item ->
                val active = tab == item
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) PvpColors.AccentDeep else Color.Transparent)
                        .clickable { onSelect(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.label,
                        color = if (active) PvpColors.TextPrimary else PvpColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingReviewCard(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = PvpColors.SurfaceRaised,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PRECISA DE ATENÇÃO", color = CollectionAmber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("$count candidato(s) aguardam confirmação", color = PvpColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
            Text("REVER ›", color = CollectionAmber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CollectionFilterRow(filter: CollectionQuickFilter, onSelect: (CollectionQuickFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CollectionQuickFilter.entries.forEach { item ->
            val active = filter == item
            Surface(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable { onSelect(item) },
                shape = RoundedCornerShape(10.dp),
                color = if (active) PvpColors.AccentDeep else CollectionCard,
                border = BorderStroke(1.dp, if (active) PvpColors.BrandBlue else CollectionBorder)
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(item.label, color = if (active) CollectionBlue else PvpColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CollectionSectionHeader(title: String, meta: String, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = PvpColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            meta,
            color = CollectionBlue,
            style = MaterialTheme.typography.labelSmall,
            modifier = if (onAction != null) Modifier.heightIn(min = 44.dp).clickable(onClick = onAction).padding(vertical = 13.dp) else Modifier
        )
    }
}

@Composable
private fun CollectionAttentionCard(
    owned: OwnedPokemon,
    species: PokemonSpecies?,
    count: Int,
    reason: String,
    onClick: () -> Unit
) {
    val name = species?.name ?: owned.speciesId
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = PvpColors.SurfaceRaised,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PRECISA DE ATENÇÃO", color = PvpColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(count.toString(), color = CollectionBlue, style = MaterialTheme.typography.labelMedium)
            }
            Text("$name pode melhorar", color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(
                reason,
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "VER EXEMPLAR  ›",
                modifier = Modifier.heightIn(min = 44.dp).padding(vertical = 13.dp),
                color = CollectionBlue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CollectionOwnedRow(
    owned: OwnedPokemon,
    species: PokemonSpecies?,
    plan: PvpBuildPlan?,
    categories: Set<String>,
    onClick: () -> Unit
) {
    val name = owned.nickname?.takeIf { it.isNotBlank() } ?: species?.name ?: owned.speciesId
    val actualSpecies = species?.name ?: owned.speciesId
    val isHundo = owned.iv.attack == 15 && owned.iv.defense == 15 && owned.iv.stamina == 15
    val status = when {
        owned.uncertainFields.isNotEmpty() || "NEEDS_CONFIRMATION" in categories -> CollectionStatus("REVER", CollectionAmber)
        isHundo -> CollectionStatus("HUNDO", CollectionAmber)
        "READY" in categories -> CollectionStatus("PRONTO", CollectionGreen)
        "IMPORTANT" in categories -> CollectionStatus("FORTE", CollectionBlue)
        "CANDIDATE" in categories -> CollectionStatus("CANDIDATO", PvpColors.BrandBlue)
        else -> CollectionStatus("OK", PvpColors.TextSecondary)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CollectionCard,
        border = BorderStroke(1.dp, CollectionBorder)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PokemonArtwork(actualSpecies, Modifier.size(54.dp), shiny = owned.isShiny, form = species?.form)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val league = plan?.league?.let(::leagueShort)
                val meta = buildList {
                    owned.cp?.let { add("CP $it") }
                    add("${owned.iv.attack}/${owned.iv.defense}/${owned.iv.stamina}")
                    league?.let(::add)
                }.joinToString(" · ")
                Text(meta, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!owned.nickname.isNullOrBlank() && species != null) {
                    Text(species.name, color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = status.color.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, status.color.copy(alpha = 0.34f))
            ) {
                Text(
                    status.label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = status.color,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NationalDexCollectionRow(
    entry: NationalDexEntry,
    ownedCount: Int,
    hasBattleData: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CollectionCard,
        border = BorderStroke(1.dp, CollectionBorder)
    ) {
        Row(Modifier.padding(10.dp).heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
            PokemonArtwork(entry.name, Modifier.size(54.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = PvpColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("#${entry.dex.toString().padStart(4,'0')} · ${if (hasBattleData) "Dados PvP disponíveis" else "Sem análise PvP validada"}",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (ownedCount > 0) Text("TENS $ownedCount", color = CollectionGreen,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Abrir ›", color = CollectionBlue, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PokedexCollectionRow(species: PokemonSpecies, ownedCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CollectionCard,
        border = BorderStroke(1.dp, CollectionBorder)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PokemonArtwork(species.name, Modifier.size(54.dp), form = species.form)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(species.name, color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("#${species.dex} · ${species.types.joinToString(" / ")}", color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            val label = if (ownedCount > 0) "TENS $ownedCount" else "SEM EXEMPLAR"
            val color = if (ownedCount > 0) CollectionGreen else PvpColors.TextSecondary
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CollectionEmptyState(title: String, detail: String, action: String?, onAction: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CollectionCard,
        border = BorderStroke(1.dp, CollectionBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                Text(
                    action,
                    modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onAction).padding(vertical = 13.dp),
                    color = CollectionBlue,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class CollectionStatus(val label: String, val color: Color)

private fun leagueShort(league: League): String = when (league) {
    League.LITTLE -> "LL"
    League.GREAT -> "GL"
    League.ULTRA -> "UL"
    League.MASTER -> "ML"
}
