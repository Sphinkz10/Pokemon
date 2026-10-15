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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
internal fun CollectionOverviewScreen(
    catalog: List<PokemonSpecies>,
    collection: List<OwnedPokemon>,
    buildPlans: List<PvpBuildPlan>,
    pendingCount: Int,
    onAdd: () -> Unit,
    onInbox: () -> Unit,
    onSelectOwned: (String) -> Unit,
    onOpenIvTargets: (String) -> Unit,
    onOpenIvTargetsForLeague: ((String, League) -> Unit)? = null,
    onStartPlan: ((String, League) -> Unit)? = null,
    resourceStoreOverride: UserResourceStore? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(CollectionViewTab.COLLECTION) }
    var filter by rememberSaveable { mutableStateOf(CollectionQuickFilter.ALL) }
    var guidedStep by rememberSaveable { mutableStateOf(CollectionGuideStep.ALL) }
    var pvpCompareLeague by rememberSaveable { mutableStateOf(League.GREAT) }
    var pvpCompareStage by rememberSaveable { mutableStateOf(PvpGuideStage.ALL) }
    var sortByPriority by rememberSaveable { mutableStateOf(true) }
    var generation by rememberSaveable { mutableStateOf("Todas") }
    var selectedDex by rememberSaveable { mutableStateOf<Int?>(null) }
    val collectionScroll = rememberLazyListState()
    val nationalScroll = rememberLazyListState()
    val context = LocalContext.current.applicationContext
    val resourceStore = remember(context, resourceStoreOverride) {
        resourceStoreOverride ?: UserResourceStore.get(context)
    }
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
    // Keep saved plans distinct from available Pokémon: no false 'plan created'
    // state. The user must explicitly open the 360 and press Save.
    val unplannedCandidates = remember(collection, planByOwned, speciesById) {
        collection.filter { owned ->
            !planByOwned.containsKey(owned.id) && speciesById.containsKey(owned.speciesId)
        }.sortedWith(compareBy<OwnedPokemon> { speciesById[it.speciesId]?.name ?: it.speciesId }
            .thenBy { it.id })
    }
    val pvpBoxByOwned = remember(collection, buildPlans) {
        PvpBoxClassifier.build(collection, buildPlans, System.currentTimeMillis()).associateBy { it.ownedPokemonId }
    }

    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val collectionItems = remember(collection, normalizedQuery, filter, guidedStep, pvpCompareLeague, pvpCompareStage, speciesById, pvpBoxByOwned, planByOwned) {
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
            val matchesCompareStage = guidedStep != CollectionGuideStep.COMPARE ||
                PvpGuidePolicy.matches(owned, species, pvpCompareLeague, pvpCompareStage)
            matchesQuery && matchesFilter && matchesCompareStage && CollectionGuidePolicy.matches(
                guidedStep, owned,
                speciesExistsInCatalog = species != null,
                hasSavedPlan = planByOwned.containsKey(owned.id)
            )
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
    val selectedNational = national?.entries?.firstOrNull { it.dex == selectedDex }
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
            onBack = { selectedDex = null },
            onOpenIvTargets = battleSpecies?.let { species ->
                { selectedDex = null; onOpenIvTargets(species.speciesId) }
            },
            onOpenOwned = firstOwnedId?.let { ownedId ->
                { selectedDex = null; onSelectOwned(ownedId) }
            }
        )
        return
    }

    // Every guided filter combination must have a practical escape route.
    // Reset only collection-level selection; preserve the user's saved Pokémon,
    // chosen league preference, sorting choice and independent Pokédex state.
    val recoverEmptySelection: () -> Unit = {
        query = ""
        filter = CollectionQuickFilter.ALL
        guidedStep = CollectionGuideStep.ALL
        pvpCompareStage = PvpGuideStage.ALL
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("collection-overview-scroll"),
        state = if (tab == CollectionViewTab.POKEDEX) nationalScroll else collectionScroll,
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
            item {
                CollectionJourneyGuide(
                    selected = guidedStep,
                    visibleCount = collectionItems.size,
                    onSelect = { guidedStep = it; filter = CollectionQuickFilter.ALL },
                    compareLeague = pvpCompareLeague,
                    compareStage = pvpCompareStage,
                    onCompareLeague = { pvpCompareLeague = it },
                    onCompareStage = { pvpCompareStage = it },
                    hasSavedPokemon = collection.isNotEmpty(),
                    onRecoverEmptySelection = recoverEmptySelection
                )
            }
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

        // Show an actionable list even if no plan has ever been created.
        // Saved plans continue to use the separate real-data filter below.
        if (tab == CollectionViewTab.COLLECTION && guidedStep == CollectionGuideStep.PLAN) {
            item {
                Column(Modifier.fillMaxWidth().testTag("collection-plan-entry"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("COMEÇAR UM PLANO PvP", color = CollectionBlue,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (unplannedCandidates.isEmpty())
                            "Todos os exemplares disponíveis já têm um plano, ou falta a espécie no catálogo."
                        else "${unplannedCandidates.size} por planear · escolhe um exemplar para definir o objetivo da liga.",
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Abrir o editor não gasta Stardust, Candy ou TMs. O plano só é criado depois de o guardares no Pokémon 360.",
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(unplannedCandidates, key = { "plan:${it.id}" }) { owned ->
                TextButton(
                    onClick = {
                        if (onStartPlan != null) onStartPlan(owned.id, pvpCompareLeague)
                        else onSelectOwned(owned.id)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("collection-plan-start-${owned.id}")
                ) {
                    Text("Criar plano · ${owned.nickname?.takeIf(String::isNotBlank) ?: speciesById[owned.speciesId]?.name ?: owned.speciesId} · ${SpeciesLeaguePolicy.title(pvpCompareLeague)} →")
                }
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
                            detail = if (collection.isEmpty()) "Adiciona o primeiro Pokémon. A importação continua protegida por reconciliação e confirmação humana." else "Os filtros não encontraram exemplares nesta seleção. Isto não avalia a força nem o rank IV dos teus Pokémon.",
                            action = if (collection.isEmpty()) "ADICIONAR POKÉMON" else "LIMPAR SELEÇÃO",
                            onAction = if (collection.isEmpty()) onAdd else recoverEmptySelection
                        )
                    }
                } else {
                    items(collectionItems, key = { it.id }) { owned ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CollectionOwnedRow(
                                owned = owned,
                                species = speciesById[owned.speciesId],
                                plan = planByOwned[owned.id],
                                categories = pvpBoxByOwned[owned.id]?.categories?.map { it.name }.orEmpty().toSet(),
                                onClick = { onSelectOwned(owned.id) }
                            )
                            if (guidedStep == CollectionGuideStep.PLAN) {
                                val savedPlan = planByOwned[owned.id]
                                if (savedPlan != null) {
                                    val species = speciesById[owned.speciesId]
                                    val familyId = species?.familyId?.takeIf(String::isNotBlank)
                                        ?: species?.speciesId
                                    val resources = familyId?.let(resourceStore::load)
                                        ?: UserResourceBalances()
                                    val nextAction = SavedPlanNextActionPolicy.next(
                                        owned, species, savedPlan, resources
                                    )
                                    Column(
                                        Modifier.fillMaxWidth().testTag("collection-plan-next-${owned.id}"),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text("Ação seguinte · ${nextAction.title}",
                                            color = CollectionBlue,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.labelMedium)
                                        Text(nextAction.explanation,
                                            color = PvpColors.TextSecondary,
                                            style = MaterialTheme.typography.bodySmall)
                                        TextButton(
                                            onClick = { onSelectOwned(owned.id) },
                                            modifier = Modifier.heightIn(min = 44.dp)
                                                .testTag("collection-plan-open-next-${owned.id}")
                                        ) {
                                            Text("Resolver no Pokémon 360 →")
                                        }
                                    }
                                }
                            }
                            if (guidedStep == CollectionGuideStep.COMPARE &&
                                pvpCompareStage != PvpGuideStage.ALL
                            ) {
                                Text(
                                    PvpGuidePolicy.assess(
                                        owned, speciesById[owned.speciesId], pvpCompareLeague
                                    ).reason,
                                    color = PvpColors.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (CollectionGuidePolicy.canOpenIvComparison(
                                    guidedStep, speciesById.containsKey(owned.speciesId)
                                )
                            ) {
                                TextButton(
                                    onClick = {
                                        val selectedLeague = pvpCompareLeague
                                        if (onOpenIvTargetsForLeague != null) {
                                            onOpenIvTargetsForLeague(owned.speciesId, selectedLeague)
                                        } else {
                                            onOpenIvTargets(owned.speciesId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                        .heightIn(min = 44.dp)
                                        .testTag("collection-guide-open-iv-${owned.id}")
                                ) {
                                    Text("Comparar IVs · ${SpeciesLeaguePolicy.title(pvpCompareLeague)} →")
                                }
                            }
                        }
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
                        onClick = { selectedDex = entry.dex }
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
private fun CollectionJourneyGuide(
    selected: CollectionGuideStep,
    visibleCount: Int,
    onSelect: (CollectionGuideStep) -> Unit,
    compareLeague: League,
    compareStage: PvpGuideStage,
    onCompareLeague: (League) -> Unit,
    onCompareStage: (PvpGuideStage) -> Unit,
    hasSavedPokemon: Boolean,
    onRecoverEmptySelection: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "AVALIAR A MINHA COLEÇÃO · POR ETAPAS",
            color = PvpColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CollectionGuideStep.entries.forEach { step ->
                FilterChip(
                    selected = selected == step,
                    onClick = { onSelect(step) },
                    label = { Text(step.title) },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("collection-guide-${step.name.lowercase()}")
                )
            }
        }
        Text(
            selected.description,
            modifier = Modifier.testTag("collection-guide-explanation"),
            color = PvpColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        if (selected == CollectionGuideStep.COMPARE || selected == CollectionGuideStep.PLAN) {
            Text(if (selected == CollectionGuideStep.PLAN) "Liga do plano" else "Liga", color = CollectionBlue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                League.entries.forEach { option ->
                    FilterChip(
                        selected = option == compareLeague,
                        onClick = { onCompareLeague(option) },
                        label = { Text(leagueShort(option)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .testTag("pvp-compare-league-${option.name.lowercase()}")
                    )
                }
            }
        }
        if (selected == CollectionGuideStep.COMPARE) {
            Text("Verificação e perfil IV (opcional)", color = CollectionBlue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PvpGuideStage.entries.forEach { option ->
                    FilterChip(
                        selected = option == compareStage,
                        onClick = { onCompareStage(option) },
                        label = { Text(option.title) },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .testTag("pvp-compare-stage-${option.name.lowercase()}")
                    )
                }
            }
            Text(compareStage.explanation,
                modifier = Modifier.testTag("pvp-compare-explanation"),
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall)
            Text("Filtra o CP atual, não o ranking IV. Em IV Targets confirma a liga antes de comparar ranks.",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "$visibleCount ${if (visibleCount == 1) "exemplar" else "exemplares"} nesta seleção",
            modifier = Modifier.testTag("collection-guide-count"),
            color = CollectionBlue,
            style = MaterialTheme.typography.labelMedium
        )
        if (hasSavedPokemon && visibleCount == 0) {
            Text(
                "Nenhum exemplar corresponde a esta seleção. Isto não é uma avaliação da qualidade PvP.",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("collection-guide-empty-explanation")
            )
            TextButton(
                onClick = onRecoverEmptySelection,
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("collection-guide-reset-empty")
            ) { Text("Limpar filtros e mostrar todos") }
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
        modifier = Modifier.fillMaxWidth().testTag("owned-row-${owned.id}").clickable(onClick = onClick),
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
