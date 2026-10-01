package com.rui.pvpgo

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.events.CalendarSnapshot
import com.rui.pvpgo.domain.CandidateInboxItem
import com.rui.pvpgo.domain.CollectionImportDraft
import com.rui.pvpgo.domain.CollectionImportEntryContext
import com.rui.pvpgo.domain.CollectionImportMode
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.OwnedPokemonField
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.domain.BuildStatus
import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.BattleRecord
import com.rui.pvpgo.domain.BattleSlot
import com.rui.pvpgo.domain.MatchupAdvisorResult
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.OpponentBattlePokemon
import com.rui.pvpgo.domain.OwnBattlePokemon
import com.rui.pvpgo.domain.PersonalMetaAnalytics
import com.rui.pvpgo.domain.PersonalMetaQuery
import com.rui.pvpgo.domain.PlaystyleProfile
import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.domain.SuggestedTeamRole
import com.rui.pvpgo.domain.TeamAdvisorConstraints
import com.rui.pvpgo.domain.TeamLabResult
import com.rui.pvpgo.domain.TeamLabStatus
import com.rui.pvpgo.domain.TeamMember
import com.rui.pvpgo.domain.TeamRole
import com.rui.pvpgo.domain.TeamStyleBias
import com.rui.pvpgo.engine.CollectionImportModes
import com.rui.pvpgo.engine.CollectionImportService
import com.rui.pvpgo.engine.CollectionReconciler
import com.rui.pvpgo.engine.PvpBoxClassifier
import com.rui.pvpgo.engine.RankSettings
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvpMove
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.StandardRulesets
import com.rui.pvpgo.live.LiveCompanionScreen
import com.rui.pvpgo.ui.theme.PvpColors
import com.rui.pvpgo.ui.theme.PvpSkin
import com.rui.pvpgo.ui.theme.PvpSkinPalettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class CollectionRoute { LIST, SPECIES, EXEMPLARS, COMPARE, DETAIL, IV_TARGETS, IMPORT_HOME, MANUAL_QUICK, MANUAL_GUIDED, PASTE, INBOX }

@Composable
fun CollectionModuleScreen(
    catalog: List<PokemonSpecies>,
    moves: List<PvpMove>
) {
    val context = LocalContext.current
    val repository = remember(context) { CollectionRepository.get(context) }
    val collectionScreenState = rememberSaveableStateHolder()
    var route by rememberSaveable { mutableStateOf(CollectionRoute.LIST) }
    var collection by remember { mutableStateOf<List<OwnedPokemon>>(emptyList()) }
    var pending by remember { mutableStateOf<List<CandidateInboxItem>>(emptyList()) }
    var buildPlans by remember { mutableStateOf<List<PvpBuildPlan>>(emptyList()) }
    var selectedOwnedId by remember { mutableStateOf<String?>(null) }
    var selectedSpeciesId by remember { mutableStateOf<String?>(null) }
    var compareIds by remember { mutableStateOf<Pair<String, String>?>(null) }
    var speciesLeague by remember { mutableStateOf(League.GREAT) }
    // Preserve the entry route; the detail screen can be opened from more
    // than one part of Collection, and Back should reverse that path.
    var detailReturnRoute by remember { mutableStateOf(CollectionRoute.SPECIES) }
    var compareReturnRoute by remember { mutableStateOf(CollectionRoute.EXEMPLARS) }
    var targetsReturnRoute by remember { mutableStateOf(CollectionRoute.SPECIES) }

    LaunchedEffect(repository) {
        repository.collection.collectLatest { collection = it }
    }
    LaunchedEffect(repository) {
        repository.pendingInbox.collectLatest { pending = it }
    }
    LaunchedEffect(repository) {
        repository.buildPlans.collectLatest { buildPlans = it }
    }

    when (route) {
        CollectionRoute.LIST -> collectionScreenState.SaveableStateProvider("collection-list") {
            CollectionOverviewScreen(
            catalog = catalog,
            collection = collection,
            buildPlans = buildPlans,
            pendingCount = pending.size,
            onAdd = { route = CollectionRoute.IMPORT_HOME },
            onInbox = { route = CollectionRoute.INBOX },
            onSelectOwned = { id ->
                selectedOwnedId = id
                selectedSpeciesId = collection.firstOrNull { it.id == id }?.speciesId
                route = CollectionRoute.SPECIES
            },
            onOpenIvTargets = { speciesId ->
                selectedSpeciesId = speciesId
                targetsReturnRoute = CollectionRoute.LIST
                route = CollectionRoute.IV_TARGETS
            }
            )
        }
        CollectionRoute.SPECIES -> {
            val species = catalog.firstOrNull { it.speciesId == selectedSpeciesId }
            if (species == null) {
                LaunchedEffect(selectedSpeciesId) { route = CollectionRoute.LIST }
            } else {
                val owned = collection.filter { it.speciesId == species.speciesId }
                SpeciesOverviewScreen(
                    species = species,
                    owned = owned,
                    plans = buildPlans,
                    league = speciesLeague,
                    onLeagueChange = { speciesLeague = it },
                    onBack = { route = CollectionRoute.LIST },
                    onOpenExemplars = { route = CollectionRoute.EXEMPLARS },
                    onOpenTargets = {
                        targetsReturnRoute = CollectionRoute.SPECIES
                        route = CollectionRoute.IV_TARGETS
                    },
                    onOpenOwned = { id ->
                        selectedOwnedId = id
                        detailReturnRoute = CollectionRoute.SPECIES
                        route = CollectionRoute.DETAIL
                    },
                    onCompare = { a, b ->
                        compareIds = a to b
                        compareReturnRoute = CollectionRoute.SPECIES
                        route = CollectionRoute.COMPARE
                    }
                )
            }
        }
        CollectionRoute.EXEMPLARS -> {
            val species = catalog.firstOrNull { it.speciesId == selectedSpeciesId }
            if (species == null) {
                LaunchedEffect(selectedSpeciesId) { route = CollectionRoute.LIST }
            } else {
                SpeciesExemplarsScreen(
                    species = species,
                    owned = collection.filter { it.speciesId == species.speciesId },
                    plans = buildPlans,
                    league = speciesLeague,
                    onLeagueChange = { speciesLeague = it },
                    onBack = { route = CollectionRoute.SPECIES },
                    onOpenOwned = { id ->
                        selectedOwnedId = id
                        detailReturnRoute = CollectionRoute.EXEMPLARS
                        route = CollectionRoute.DETAIL
                    },
                    onCompare = { a, b ->
                        compareIds = a to b
                        compareReturnRoute = CollectionRoute.EXEMPLARS
                        route = CollectionRoute.COMPARE
                    }
                )
            }
        }
        CollectionRoute.COMPARE -> {
            val species = catalog.firstOrNull { it.speciesId == selectedSpeciesId }
            val pair = compareIds
            val a = collection.firstOrNull { it.id == pair?.first && it.speciesId == species?.speciesId }
            val b = collection.firstOrNull { it.id == pair?.second && it.speciesId == species?.speciesId }
            if (species == null || a == null || b == null || !SpeciesCollectionPolicy.validComparison(collection, a.id, b.id)) {
                LaunchedEffect(species, pair, collection) { route = CollectionRoute.EXEMPLARS }
            } else {
                SpeciesCompareScreen(
                    species = species, first = a, second = b,
                    plans = buildPlans,
                    league = speciesLeague,
                    onLeagueChange = { speciesLeague = it },
                    onBack = { route = compareReturnRoute }
                )
            }
        }
        CollectionRoute.DETAIL -> {
            val owned = collection.firstOrNull { it.id == selectedOwnedId }
            if (owned == null) {
                LaunchedEffect(selectedOwnedId, collection) { route = CollectionRoute.LIST }
            } else {
                OwnedPokemonDetailScreen(
                    owned = owned,
                    species = catalog.firstOrNull { it.speciesId == owned.speciesId },
                    moves = moves,
                    plans = buildPlans.filter { it.ownedPokemonId == owned.id },
                    repository = repository,
                    onBack = { route = detailReturnRoute }
                )
            }
        }
        CollectionRoute.IV_TARGETS -> {
            val species = catalog.firstOrNull { it.speciesId == selectedSpeciesId }
            if (species == null) {
                LaunchedEffect(selectedSpeciesId, catalog) { route = CollectionRoute.LIST }
            } else {
                IvTargetsScreen(
                    species = species,
                    collection = collection,
                    league = speciesLeague,
                    onLeagueChange = { speciesLeague = it },
                    onBack = { route = targetsReturnRoute }
                )
            }
        }
        CollectionRoute.IMPORT_HOME -> ImportHomeScreen(
            pendingCount = pending.size,
            repository = repository,
            collection = collection,
            onBack = { route = CollectionRoute.LIST },
            onQuick = { route = CollectionRoute.MANUAL_QUICK },
            onGuided = { route = CollectionRoute.MANUAL_GUIDED },
            onPaste = { route = CollectionRoute.PASTE },
            onInbox = { route = CollectionRoute.INBOX }
        )
        CollectionRoute.MANUAL_QUICK -> ManualImportScreen(
            catalog = catalog,
            repository = repository,
            collection = collection,
            guided = false,
            onBack = { route = CollectionRoute.IMPORT_HOME },
            onSubmitted = { route = CollectionRoute.INBOX }
        )
        CollectionRoute.MANUAL_GUIDED -> ManualImportScreen(
            catalog = catalog,
            repository = repository,
            collection = collection,
            guided = true,
            onBack = { route = CollectionRoute.IMPORT_HOME },
            onSubmitted = { route = CollectionRoute.INBOX }
        )
        CollectionRoute.PASTE -> PasteImportScreen(
            repository = repository,
            collection = collection,
            onBack = { route = CollectionRoute.IMPORT_HOME },
            onSubmitted = { route = CollectionRoute.INBOX }
        )
        CollectionRoute.INBOX -> InboxScreen(
            catalog = catalog,
            repository = repository,
            collection = collection,
            pending = pending,
            onBack = { route = CollectionRoute.LIST },
            onAddAnother = { route = CollectionRoute.IMPORT_HOME }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionListScreen(
    catalog: List<PokemonSpecies>,
    collection: List<OwnedPokemon>,
    buildPlans: List<PvpBuildPlan>,
    pendingCount: Int,
    onAdd: () -> Unit,
    onInbox: () -> Unit,
    onSelectOwned: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val byId = remember(catalog) { catalog.associateBy { it.speciesId } }
    val filtered = remember(collection, query, byId) {
        if (query.isBlank()) collection else collection.filter { owned ->
            val species = byId[owned.speciesId]
            species?.name?.contains(query, ignoreCase = true) == true ||
                owned.speciesId.contains(query, ignoreCase = true) ||
                owned.nickname?.contains(query, ignoreCase = true) == true
        }
    }
    val box = remember(collection, buildPlans) { PvpBoxClassifier.build(collection, buildPlans, System.currentTimeMillis()) }
    val readyCount = box.count { entry -> entry.categories.any { it.name == "READY" } }
    val needsReview = box.count { entry -> entry.categories.any { it.name == "NEEDS_CONFIRMATION" } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Collection") }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd) { Text("+ Adicionar") } }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollectionMetric("Total", collection.size.toString(), Modifier.weight(1f))
                CollectionMetric("Ready", readyCount.toString(), Modifier.weight(1f))
                CollectionMetric("Rever", needsReview.toString(), Modifier.weight(1f))
            }
            if (pendingCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onInbox),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Review Inbox", fontWeight = FontWeight.Bold)
                            Text("$pendingCount candidato(s) aguardam confirmação.", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Abrir →", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Pesquisar na Collection") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (collection.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("A tua Collection está vazia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Adiciona o primeiro Pokémon. A importação passa sempre por reconciliação e confirmação humana.")
                        Button(onClick = onAdd) { Text("Adicionar Pokémon") }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { owned ->
                        val species = byId[owned.speciesId]
                        OwnedPokemonCard(owned, species, onClick = { onSelectOwned(owned.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OwnedPokemonCard(owned: OwnedPokemon, species: PokemonSpecies?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(owned.nickname?.takeIf { it.isNotBlank() } ?: species?.name ?: owned.speciesId, fontWeight = FontWeight.Bold)
                    if (!owned.nickname.isNullOrBlank() && species != null) Text(species.name, style = MaterialTheme.typography.labelSmall)
                }
                Text("${owned.iv.attack}/${owned.iv.defense}/${owned.iv.stamina}", fontWeight = FontWeight.SemiBold)
            }
            val details = buildList {
                owned.cp?.let { add("$it CP") }
                owned.level?.let { add("L${formatCompactLevel(it)}") }
                if (owned.isShadow) add("Shadow")
                if (owned.isShiny) add("Shiny")
                if (owned.isLucky) add("Lucky")
                if (owned.isBestBuddy) add("Best Buddy")
            }
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (owned.uncertainFields.isNotEmpty()) {
                Text("Por confirmar: ${owned.uncertainFields.joinToString { it.name.lowercase() }}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
            }
            if (owned.tags.isNotEmpty()) Text(owned.tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportHomeScreen(
    pendingCount: Int,
    repository: CollectionRepository,
    collection: List<OwnedPokemon>,
    onBack: () -> Unit,
    onQuick: () -> Unit,
    onGuided: () -> Unit,
    onPaste: () -> Unit,
    onInbox: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val clipboardText = remember {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }
    val ui = remember(pendingCount, clipboardText) {
        CollectionImportExperience.home(CollectionImportEntryContext(pendingInboxCount = pendingCount, clipboardText = clipboardText))
    }

    fun submitText(text: String, source: String) {
        scope.launch {
            busy = true
            message = null
            runCatching {
                withContext(Dispatchers.Default) {
                    CollectionImportModes.parsePasted(text, source, System.currentTimeMillis())
                }
            }.onSuccess { batch ->
                if (batch.candidates.isEmpty()) {
                    message = batch.issues.joinToString("\n") { it.message }.ifBlank { "Nenhum candidato válido encontrado." }
                } else {
                    val reconciled = CollectionImportService.reconcile(batch, collection)
                    repository.submitCandidates(CollectionImportService.toInboxItems(reconciled))
                    message = "${batch.candidates.size} candidato(s) enviado(s) para revisão."
                    onInbox()
                }
            }.onFailure { message = it.message ?: "Não foi possível ler o ficheiro." }
            busy = false
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (text.isNullOrBlank()) message = "O ficheiro está vazio ou não pôde ser lido."
                else submitText(text, "bulk-file")
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(ui.title) }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { Text(ui.subtitle, style = MaterialTheme.typography.bodyMedium) }
            ui.cards.forEach { card ->
                item(key = card.mode.name) {
                    val action: (() -> Unit)? = when (card.mode) {
                        CollectionImportMode.QUICK_MANUAL -> onQuick
                        CollectionImportMode.GUIDED -> onGuided
                        CollectionImportMode.BULK_FILE -> ({ fileLauncher.launch(arrayOf("text/*", "application/json", "text/csv")) })
                        CollectionImportMode.PASTE -> onPaste
                        CollectionImportMode.SCREENSHOT_OCR -> null
                        CollectionImportMode.REVIEW_INBOX -> onInbox
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().then(if (action != null && card.enabled && !busy) Modifier.clickable(onClick = action) else Modifier),
                        colors = if (card.recommended) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(card.title, fontWeight = FontWeight.Bold)
                                card.badge?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                            }
                            Text(card.description, style = MaterialTheme.typography.bodySmall)
                            if (card.mode == CollectionImportMode.SCREENSHOT_OCR) {
                                Text("Bloqueado até integrar captura/OCR real no Android; não são inventados resultados.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item { Text(ui.safetyNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.tertiary) } }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualImportScreen(
    catalog: List<PokemonSpecies>,
    repository: CollectionRepository,
    collection: List<OwnedPokemon>,
    guided: Boolean,
    onBack: () -> Unit,
    onSubmitted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<PokemonSpecies?>(null) }
    var atk by remember { mutableIntStateOf(0) }
    var def by remember { mutableIntStateOf(15) }
    var hp by remember { mutableIntStateOf(15) }
    var cpText by remember { mutableStateOf("") }
    var levelText by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var shadow by remember { mutableStateOf(false) }
    var purified by remember { mutableStateOf(false) }
    var shiny by remember { mutableStateOf(false) }
    var lucky by remember { mutableStateOf(false) }
    var bestBuddy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val results = remember(query, catalog) { if (query.length < 2) emptyList() else PokemonSearch.find(catalog, query, 12) }

    Scaffold(topBar = { TopAppBar(title = { Text(if (guided) "Adicionar · Guiado" else "Adicionar · Rápido") }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Text(
                    if (guided) "Espécie e IVs são obrigatórios; os restantes campos podem ficar para depois."
                    else "Só precisas da espécie e dos três IVs. CP, nível e moves ficam marcados como incertos.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (selected == null) {
                item {
                    OutlinedTextField(query, { query = it }, label = { Text("Pokémon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                items(results, key = { it.speciesId }) { species ->
                    Card(Modifier.fillMaxWidth().clickable { selected = species; query = species.name }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(species.name, fontWeight = FontWeight.Bold)
                            Text("#${species.dex} · ${species.speciesId}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(selected!!.name, fontWeight = FontWeight.Bold)
                                Text("#${selected!!.dex}", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { selected = null; query = "" }) { Text("Trocar") }
                        }
                    }
                }
                item { CompactIvSelector("Ataque", atk) { atk = it } }
                item { CompactIvSelector("Defesa", def) { def = it } }
                item { CompactIvSelector("HP", hp) { hp = it } }
                if (guided) {
                    item { OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname · opcional") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(cpText, { cpText = it.filter(Char::isDigit) }, label = { Text("CP") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(levelText, { levelText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } }, label = { Text("Nível") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    item { ToggleLine("Shadow", shadow) { shadow = it; if (it) purified = false } }
                    item { ToggleLine("Purified", purified) { purified = it; if (it) shadow = false } }
                    item { ToggleLine("Shiny", shiny) { shiny = it } }
                    item { ToggleLine("Lucky", lucky) { lucky = it } }
                    item { ToggleLine("Best Buddy", bestBuddy) { bestBuddy = it } }
                }
                item {
                    Button(
                        onClick = {
                            val species = selected ?: return@Button
                            val cp = cpText.toIntOrNull()
                            val level = levelText.replace(',', '.').toDoubleOrNull()
                            val mode = if (guided) CollectionImportMode.GUIDED else CollectionImportMode.QUICK_MANUAL
                            val sessionId = "manual-${System.currentTimeMillis()}"
                            val draft = runCatching {
                                CollectionImportDraft(
                                    sessionId = sessionId,
                                    mode = mode,
                                    speciesId = species.speciesId,
                                    ivAttack = atk,
                                    ivDefense = def,
                                    ivStamina = hp,
                                    cp = cp,
                                    level = level,
                                    nickname = nickname.trim().takeIf(String::isNotBlank),
                                    isShadow = shadow,
                                    isPurified = purified,
                                    isShiny = shiny,
                                    isLucky = lucky,
                                    isBestBuddy = bestBuddy,
                                    externalSource = "manual"
                                )
                            }.getOrElse { ex -> error = ex.message; return@Button }
                            scope.launch {
                                busy = true
                                error = null
                                runCatching {
                                    val batch = CollectionImportModes.manualBatch(draft, System.currentTimeMillis())
                                    require(batch.candidates.isNotEmpty()) { batch.issues.joinToString("; ") { it.message }.ifBlank { "Importação inválida" } }
                                    val reconciled = CollectionImportService.reconcile(batch, collection)
                                    repository.submitCandidates(CollectionImportService.toInboxItems(reconciled))
                                }.onSuccess { onSubmitted() }
                                    .onFailure { error = it.message ?: "Falha ao preparar a importação." }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "A preparar…" else "Rever antes de guardar") }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
private fun CompactIvSelector(label: String, value: Int, onValue: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Slider(value.toFloat(), { onValue(it.toInt().coerceIn(0, 15)) }, valueRange = 0f..15f, steps = 14)
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasteImportScreen(
    repository: CollectionRepository,
    collection: List<OwnedPokemon>,
    onBack: () -> Unit,
    onSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Colar CSV / JSON") }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("O conteúdo é validado e convertido em candidatos. Nada entra diretamente na Collection.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("CSV ou JSON") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 8
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        runCatching {
                            val batch = withContext(Dispatchers.Default) {
                                CollectionImportModes.parsePasted(text, "paste", System.currentTimeMillis())
                            }
                            require(batch.candidates.isNotEmpty()) { batch.issues.joinToString("; ") { it.message }.ifBlank { "Nenhum candidato válido." } }
                            val reconciled = CollectionImportService.reconcile(batch, collection)
                            repository.submitCandidates(CollectionImportService.toInboxItems(reconciled))
                        }.onSuccess { onSubmitted() }
                            .onFailure { error = it.message ?: "Formato inválido." }
                        busy = false
                    }
                },
                enabled = text.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "A validar…" else "Validar e enviar para Review Inbox") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxScreen(
    catalog: List<PokemonSpecies>,
    repository: CollectionRepository,
    collection: List<OwnedPokemon>,
    pending: List<CandidateInboxItem>,
    onBack: () -> Unit,
    onAddAnother: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val byId = remember(catalog) { catalog.associateBy { it.speciesId } }
    var actionError by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Review Inbox · ${pending.size}") }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } }) }) { padding ->
        if (pending.isEmpty()) {
            Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Não há candidatos pendentes.", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onAddAnother) { Text("Adicionar Pokémon") }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
            ) {
                actionError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(pending, key = { it.id }) { item ->
                    val c = item.candidate
                    val species = byId[c.speciesId]
                    val report = remember(item, collection) { CollectionReconciler.reconcile(c, collection) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(species?.name ?: c.speciesId, fontWeight = FontWeight.Bold)
                                Text("${c.iv.attack}/${c.iv.defense}/${c.iv.stamina}", fontWeight = FontWeight.SemiBold)
                            }
                            val known = buildList {
                                c.cp?.let { add("$it CP") }
                                c.level?.let { add("L${formatCompactLevel(it)}") }
                                if (c.isShadow) add("Shadow")
                            }
                            if (known.isNotEmpty()) Text(known.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            if (c.uncertainFields.isNotEmpty()) Text("Incerto: ${c.uncertainFields.joinToString { it.name.lowercase() }}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                            HorizontalDivider()
                            Text("Reconciliação: ${report.action.name.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
                            if (report.matches.isEmpty()) {
                                Text("Nenhum Pokémon existente parece ser o mesmo indivíduo.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                report.matches.take(3).forEach { match ->
                                    Text("Possível duplicado · ${formatConfidence(match.confidence)} · ${match.reasons.joinToString()}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("A fusão automática continua bloqueada. Confirma apenas se queres guardar este candidato como indivíduo separado.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            busyId = item.id; actionError = null
                                            runCatching { repository.confirmNew(item.id) }
                                                .onFailure { actionError = it.message }
                                            busyId = null
                                        }
                                    },
                                    enabled = busyId == null,
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (report.matches.isEmpty()) "Confirmar novo" else "Guardar separado") }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            busyId = item.id; actionError = null
                                            runCatching { repository.discardCandidate(item.id) }
                                                .onFailure { actionError = it.message }
                                            busyId = null
                                        }
                                    },
                                    enabled = busyId == null,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Descartar") }
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
private fun OwnedPokemonDetailScreen(
    owned: OwnedPokemon,
    species: PokemonSpecies?,
    moves: List<PvpMove>,
    plans: List<PvpBuildPlan>,
    repository: CollectionRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val movesById = remember(moves) { moves.associateBy { it.moveId } }
    val fastPool = remember(species, moves) { species?.fastMoveIds.orEmpty().mapNotNull(movesById::get) }
    val chargedPool = remember(species, moves) { species?.chargedMoveIds.orEmpty().mapNotNull(movesById::get) }

    var cpText by remember(owned.id, owned.updatedAtEpochMs) { mutableStateOf(owned.cp?.toString().orEmpty()) }
    var levelText by remember(owned.id, owned.updatedAtEpochMs) { mutableStateOf(owned.level?.let(::formatCompactLevel).orEmpty()) }
    var fastMoveId by remember(owned.id, owned.updatedAtEpochMs) { mutableStateOf(owned.fastMoveId) }
    var chargedMoveIds by remember(owned.id, owned.updatedAtEpochMs) { mutableStateOf(owned.chargedMoveIds) }
    var league by remember(owned.id) { mutableStateOf(League.GREAT) }
    var ownedRank by remember(owned.id, owned.iv, species, league) { mutableStateOf<com.rui.pvpgo.engine.PvPRankEntry?>(null) }
    var rankComputed by remember(owned.id, owned.iv, species, league) { mutableStateOf(false) }
    LaunchedEffect(owned.id, owned.iv, species, league) {
        ownedRank = withContext(Dispatchers.Default) {
            runCatching { species?.let { RankRepository.find(it, league, owned.iv, RankSettings()) } }.getOrNull()
        }
        rankComputed = true
    }
    var planStatus by remember(owned.id, league, plans) {
        mutableStateOf(plans.firstOrNull { it.league == league }?.status ?: BuildStatus.IDEA)
    }
    var priority by remember(owned.id, league, plans) {
        mutableIntStateOf(plans.firstOrNull { it.league == league }?.priority ?: 50)
    }
    var notes by remember(owned.id, league, plans) {
        mutableStateOf(plans.firstOrNull { it.league == league }?.notes.orEmpty())
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(league, plans) {
        val plan = plans.firstOrNull { it.league == league }
        planStatus = plan?.status ?: BuildStatus.IDEA
        priority = plan?.priority ?: 50
        notes = plan?.notes.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(owned.nickname?.takeIf(String::isNotBlank) ?: species?.name ?: owned.speciesId)
                        Text("${owned.iv.attack}/${owned.iv.defense}/${owned.iv.stamina}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = PvpColors.SurfaceCard,
                    border = BorderStroke(1.dp, PvpColors.BorderDefault)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (species != null) {
                            PokemonArtwork(species.name, Modifier.size(90.dp), shiny = owned.isShiny, form = species.form)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                owned.nickname?.takeIf(String::isNotBlank) ?: species?.name ?: owned.speciesId,
                                style = MaterialTheme.typography.titleLarge,
                                color = PvpColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "IV ${owned.iv.attack}/${owned.iv.defense}/${owned.iv.stamina} · ${owned.iv.total}/45",
                                style = MaterialTheme.typography.bodySmall,
                                color = PvpColors.TextSecondary
                            )
                            Text(
                                listOfNotNull(
                                    if (owned.isShiny) "SHINY" else null,
                                    if (owned.isFavorite) "FAVORITO" else null,
                                    if (owned.uncertainFields.isNotEmpty()) "DADOS POR CONFIRMAR" else null
                                ).joinToString(" · ").ifBlank { "Exemplar da tua coleção" },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (owned.uncertainFields.isNotEmpty()) PvpColors.StateWarning else PvpColors.BrandBlue
                            )
                        }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = PvpColors.SurfaceRaised,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PvpColors.BorderDefault)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "RANK PVP · ${SpeciesLeaguePolicy.title(league).uppercase(Locale.ROOT)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PvpColors.TextSecondary
                            )
                            Text(
                                SpeciesLeaguePolicy.statusLabel(ownedRank?.rank, !rankComputed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PvpColors.TextPrimary
                            )
                            Text(
                                "Calculado com os IV registados. Muda a liga na secção Plano PvP.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PvpColors.TextSecondary
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeciesLeaguePolicy.selectable.forEach { option ->
                        FilterChip(
                            selected = league == option,
                            onClick = { league = option },
                            label = { Text(SpeciesLeaguePolicy.shortLabel(option)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
            item {
                Text("Build real", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Regista apenas dados que confirmaste no Pokémon GO. Campos em falta continuam UNKNOWN.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        cpText,
                        { cpText = it.filter(Char::isDigit) },
                        label = { Text("CP") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        levelText,
                        { levelText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = { Text("Nível") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Text("Fast Move", fontWeight = FontWeight.Bold)
                if (fastPool.isEmpty()) Text("Movepool ainda indisponível.", style = MaterialTheme.typography.bodySmall)
            }
            items(fastPool, key = { "fast:${it.moveId}" }) { move ->
                Row(
                    Modifier.fillMaxWidth().clickable { fastMoveId = move.moveId }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = fastMoveId == move.moveId, onClick = { fastMoveId = move.moveId })
                    Column {
                        Text(move.name, fontWeight = FontWeight.SemiBold)
                        Text("${move.type} · ${move.power} dano · ${move.energyGain} energia · ${move.turns}t", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Text("Charged Moves · máximo 2", fontWeight = FontWeight.Bold)
                if (chargedPool.isEmpty()) Text("Movepool ainda indisponível.", style = MaterialTheme.typography.bodySmall)
            }
            items(chargedPool, key = { "charged:${it.moveId}" }) { move ->
                val checked = move.moveId in chargedMoveIds
                Row(
                    Modifier.fillMaxWidth().clickable {
                        chargedMoveIds = when {
                            checked -> chargedMoveIds - move.moveId
                            chargedMoveIds.size < 2 -> chargedMoveIds + move.moveId
                            else -> chargedMoveIds
                        }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            chargedMoveIds = when {
                                checked -> chargedMoveIds - move.moveId
                                chargedMoveIds.size < 2 -> chargedMoveIds + move.moveId
                                else -> chargedMoveIds
                            }
                        }
                    )
                    Column {
                        Text(move.name, fontWeight = FontWeight.SemiBold)
                        Text("${move.type} · ${move.power} dano · ${move.energyCost} energia", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        val validationError = listOfNotNull(
                            OwnedBuildValidation.cpError(cpText),
                            OwnedBuildValidation.levelError(levelText),
                            OwnedBuildValidation.chargedMovesError(chargedMoveIds)
                        ).firstOrNull()
                        if (validationError != null) {
                            message = validationError
                            return@Button
                        }
                        val cp = cpText.toIntOrNull()
                        val level = levelText.replace(',', '.').toDoubleOrNull()
                        scope.launch {
                            busy = true
                            message = null
                            runCatching {
                                val uncertain = owned.uncertainFields.toMutableSet().apply {
                                    if (cp == null) add(OwnedPokemonField.CP) else remove(OwnedPokemonField.CP)
                                    if (level == null) add(OwnedPokemonField.LEVEL) else remove(OwnedPokemonField.LEVEL)
                                    if (fastMoveId == null) add(OwnedPokemonField.FAST_MOVE) else remove(OwnedPokemonField.FAST_MOVE)
                                    if (chargedMoveIds.isEmpty()) add(OwnedPokemonField.CHARGED_MOVES) else remove(OwnedPokemonField.CHARGED_MOVES)
                                }
                                val now = System.currentTimeMillis()
                                repository.upsertOwned(
                                    owned.copy(
                                        cp = cp,
                                        level = level,
                                        fastMoveId = fastMoveId,
                                        chargedMoveIds = chargedMoveIds,
                                        secondChargedMoveUnlocked = when (chargedMoveIds.size) {
                                            0 -> null
                                            1 -> false
                                            else -> true
                                        },
                                        uncertainFields = uncertain,
                                        updatedAtEpochMs = now,
                                        lastVerifiedAtEpochMs = now
                                    )
                                )
                            }.onSuccess { message = "Build guardado na Collection." }
                                .onFailure { message = it.message ?: "Não foi possível guardar." }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "A guardar…" else "Guardar build confirmado") }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("Plano PvP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("O plano é intenção de investimento. Não altera os moves reais do Pokémon.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeciesLeaguePolicy.selectable.forEach { option ->
                        FilterChip(
                            selected = league == option,
                            onClick = { league = option },
                            label = { Text(when (option) {
                                League.GREAT -> "Great"
                                League.ULTRA -> "Ultra"
                                League.MASTER -> "Master"
                                League.LITTLE -> "Little"
                            }) }
                        )
                    }
                }
            }
            item {
                Text("Estado", fontWeight = FontWeight.SemiBold)
                Column {
                    BuildStatus.entries.forEach { status ->
                        Row(
                            Modifier.fillMaxWidth().clickable { planStatus = status }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = planStatus == status, onClick = { planStatus = status })
                            Text(status.name.replace('_', ' '))
                        }
                    }
                }
            }
            item {
                Text("Prioridade · $priority/100", fontWeight = FontWeight.SemiBold)
                Slider(priority.toFloat(), { priority = it.toInt().coerceIn(0, 100) }, valueRange = 0f..100f)
            }
            item {
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text("Notas · opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
            item {
                val existing = plans.firstOrNull { it.league == league }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            runCatching {
                                val now = System.currentTimeMillis()
                                repository.upsertBuildPlan(
                                    (existing ?: PvpBuildPlan(
                                        id = "plan:${owned.id}:${league.name.lowercase()}",
                                        ownedPokemonId = owned.id,
                                        league = league,
                                        createdAtEpochMs = now
                                    )).copy(
                                        desiredFastMoveId = fastMoveId,
                                        desiredChargedMoveIds = chargedMoveIds,
                                        status = planStatus,
                                        priority = priority,
                                        notes = notes.trim().takeIf(String::isNotBlank),
                                        updatedAtEpochMs = now
                                    )
                                )
                            }.onSuccess { message = "Plano ${league.name} guardado." }
                                .onFailure { message = it.message ?: "Não foi possível guardar o plano." }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (existing == null) "Criar plano PvP" else "Atualizar plano PvP") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarRootScreen(catalog: List<PokemonSpecies>) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<PokemonSpecies?>(null) }
    val results = remember(query, catalog) { if (query.length < 2) emptyList() else PokemonSearch.find(catalog, query, 30) }

    Scaffold(topBar = { TopAppBar(title = { Text("Radar PvP") }) }) { padding ->
        if (selected == null) {
            Column(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.height(2.dp))
                Text("Escolhe um Pokémon para procurar oportunidades Rank #1 / Top 10 / Top 50 / Top 100.")
                OutlinedTextField(query, { query = it }, label = { Text("Pokémon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.speciesId }) { species ->
                        Card(Modifier.fillMaxWidth().clickable { selected = species }) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(species.name, fontWeight = FontWeight.Bold)
                                Text("#${species.dex}")
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selected!!.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("#${selected!!.dex}", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { selected = null; query = "" }) { Text("Trocar") }
                    }
                }
                item { RadarSection(species = selected!!, rankSettings = RankSettings()) }
            }
        }
    }
}

enum class MoreRoute { HOME, EVENT, AGENDA, RADAR, MATCHUP, TEAM_LAB, BATTLE_LOG, LIVE, APPEARANCE }

@Composable
fun MoreRootScreen(
    catalog: List<PokemonSpecies>,
    moves: List<PvpMove>,
    startRoute: MoreRoute = MoreRoute.HOME,
    selectedSkin: PvpSkin = PvpSkin.DEEP,
    onSkinSelected: (PvpSkin) -> Unit = {},
    calendar: CalendarSnapshot? = null,
    onRefreshCalendar: () -> Unit = {}
) {
    var route by remember(startRoute) { mutableStateOf(startRoute) }
    when (route) {
        MoreRoute.HOME -> MoreHomeScreen(
            onRadar = { route = MoreRoute.RADAR },
            onMatchup = { route = MoreRoute.MATCHUP },
            onTeamLab = { route = MoreRoute.TEAM_LAB },
            onBattleLog = { route = MoreRoute.BATTLE_LOG },
            onLive = { route = MoreRoute.LIVE },
            onAppearance = { route = MoreRoute.APPEARANCE }
        )
        MoreRoute.EVENT -> EventOverviewScreen(
            calendar = calendar,
            onRefresh = onRefreshCalendar,
            onBack = { route = MoreRoute.HOME },
            onOpenRadar = { route = MoreRoute.RADAR }
        )
        MoreRoute.AGENDA -> AgendaOverviewScreen(
            calendar = calendar,
            onRefresh = onRefreshCalendar,
            onBack = { route = MoreRoute.HOME },
            onOpenEvent = { route = MoreRoute.EVENT },
            onOpenRadar = { route = MoreRoute.RADAR }
        )
        MoreRoute.RADAR -> RadarRootScreen(catalog = catalog)
        MoreRoute.MATCHUP -> MatchupAdvisorScreen(catalog = catalog, moves = moves, onBack = { route = MoreRoute.HOME })
        MoreRoute.TEAM_LAB -> TeamLabScreen(catalog = catalog, moves = moves, onBack = { route = MoreRoute.HOME })
        MoreRoute.BATTLE_LOG -> BattleLogScreen(catalog = catalog, onBack = { route = MoreRoute.HOME })
        MoreRoute.LIVE -> LiveCompanionScreen(onBack = { route = MoreRoute.HOME })
        MoreRoute.APPEARANCE -> AppearanceScreen(
            selectedSkin = selectedSkin,
            onChoose = onSkinSelected,
            onBack = { route = MoreRoute.HOME }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreHomeScreen(onRadar: () -> Unit, onMatchup: () -> Unit, onTeamLab: () -> Unit, onBattleLog: () -> Unit, onLive: () -> Unit, onAppearance: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Mais") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
        ) {
            item {
                Text("Pokémon PvP · Android vNext", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Abrimos apenas áreas que conseguem preservar os trust guards do motor. UNKNOWN continua melhor do que inventar um build.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onRadar)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Radar", fontWeight = FontWeight.Bold)
                            Text("Abrir →", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("O Radar passa a viver em Mais para manter a navegação principal alinhada com Hoje · Coleção · Equipas · Batalhas · Mais.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onMatchup)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Matchup Advisor", fontWeight = FontWeight.Bold)
                            Text("Abrir →", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Compara um adversário que tu defines explicitamente contra os builds completos da tua Collection. Não inventa IVs, nível ou moves.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onTeamLab)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Team Lab", fontWeight = FontWeight.Bold)
                            Text("Abrir →", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Usa apenas a tua Collection, um Ruleset persistente e um Meta Pack versionado. Coverage Score não é probabilidade de vitória.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onBattleLog)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Battle Log / My Meta", fontWeight = FontWeight.Bold)
                            Text("Abrir →", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Registo local por equipa/liga e analytics pessoais com tamanho de amostra e intervalos de confiança. Não altera o Meta Pack global.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onLive)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Live Companion", fontWeight = FontWeight.Bold)
                            Text("Abrir →", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("MediaProjection + device calibration foundation: consentimento Android, serviço foreground, cadência/gaps, bateria/térmico e staging journal. Reconhecimento/overlay continuam bloqueados até validação real.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onAppearance)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Aparência · Skins", fontWeight = FontWeight.Bold)
                            Text("Escolher →", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Deep, AMOLED, Mystic e Classic. Preferência guardada neste dispositivo.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { IntegrationStatusCard("Privacidade", "Collection fica em user.db local. Importações não alteram a Collection sem confirmação humana.", "Ativo") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceScreen(
    selectedSkin: PvpSkin,
    onChoose: (PvpSkin) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aparência") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp)
        ) {
            item {
                Text("Escolhe a tua skin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("O tema escolhido fica guardado e é restaurado quando abres a aplicação.", style = MaterialTheme.typography.bodySmall)
            }
            items(PvpSkin.entries, key = { it.storageKey }) { option ->
                val preview = PvpSkinPalettes.palette(option)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onChoose(option) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(preview.canvasStart, preview.surfaceCard, preview.brandYellow).forEach { swatch ->
                                Surface(
                                    modifier = Modifier.size(22.dp),
                                    color = swatch,
                                    shape = RoundedCornerShape(50)
                                ) {}
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(option.label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                            Text(option.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selectedSkin == option) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Text(
                    "A estrutura de temas já é funcional. Alguns gráficos e ilustrações antigas ainda têm cores próprias e serão migrados por módulo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchupAdvisorScreen(
    catalog: List<PokemonSpecies>,
    moves: List<PvpMove>,
    onBack: () -> Unit,
    initialLeague: League = League.GREAT
) {
    val context = LocalContext.current
    val repository = remember(context) { CollectionRepository.get(context) }
    var collection by remember { mutableStateOf<List<OwnedPokemon>>(emptyList()) }
    var savedTeams by remember { mutableStateOf<List<SavedTeam>>(emptyList()) }
    LaunchedEffect(repository) { repository.collection.collectLatest { collection = it } }
    LaunchedEffect(repository) { repository.savedTeams.collectLatest { savedTeams = it } }

    val scope = rememberCoroutineScope()
    val byId = remember(catalog) { catalog.associateBy { it.speciesId } }
    val movesById = remember(moves) { moves.associateBy { it.moveId } }

    var query by remember { mutableStateOf("") }
    var opponentSpecies by remember { mutableStateOf<PokemonSpecies?>(null) }
    var league by remember(initialLeague) { mutableStateOf(initialLeague) }
    var atkText by remember { mutableStateOf("") }
    var defText by remember { mutableStateOf("") }
    var hpText by remember { mutableStateOf("") }
    var levelText by remember { mutableStateOf("") }
    var fastMoveId by remember { mutableStateOf<String?>(null) }
    var chargedMoveIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var ownShields by remember { mutableIntStateOf(1) }
    var opponentShields by remember { mutableIntStateOf(1) }
    var result by remember { mutableStateOf<MatchupAdvisorResult?>(null) }
    var analyzedCollectionSnapshot by remember { mutableStateOf<List<OwnedPokemon>?>(null) }
    var showingTeamCoverage by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val searchResults = remember(query, catalog) {
        if (query.length < 2) emptyList() else PokemonSearch.find(catalog, query, 15)
    }
    val opponentFast = remember(opponentSpecies, moves) {
        opponentSpecies?.fastMoveIds.orEmpty().mapNotNull(movesById::get)
    }
    val opponentCharged = remember(opponentSpecies, moves) {
        opponentSpecies?.chargedMoveIds.orEmpty().mapNotNull(movesById::get)
    }
    val completeOwnedCount = remember(collection) {
        collection.count { it.level != null && it.fastMoveId != null && it.chargedMoveIds.isNotEmpty() }
    }
    // A recorded rating belongs to the exact collection snapshot passed to the advisor.
    // Re-import/edit/removal of a specimen must invalidate stale B03/B04 results.
    LaunchedEffect(collection, analyzedCollectionSnapshot) {
        if (result != null && analyzedCollectionSnapshot != null && analyzedCollectionSnapshot != collection) {
            result = null
            showingTeamCoverage = false
            error = "A coleção mudou. Atualiza a análise antes de consultar ratings."
        }
    }

    // B03 is a distinct screen. Results are never appended below stale configuration.
    if (result != null) {
        if (showingTeamCoverage) {
            BattleTeamCoverageScreen(
                analysis = result!!, catalog = catalog, owned = collection, savedTeams = savedTeams,
                onBack = { showingTeamCoverage = false },
                onEditScenario = { showingTeamCoverage = false; result = null }
            )
        } else {
            BattlesGoldenResultScreen(
                analysis = result!!, catalog = catalog, owned = collection,
                onEdit = { result = null }, onClose = onBack,
                onTeamCoverage = { showingTeamCoverage = true }
            )
        }
        return
    }

    Scaffold(
        containerColor = PvpColors.CanvasStart,
        topBar = {
            TopAppBar(
                title = { Text("Configurar matchup", color = PvpColors.TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PvpColors.CanvasStart),
                navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("←") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
        ) {
            item {
                Surface(color = PvpColors.SurfaceCard, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BUILD COMPLETO · $completeOwnedCount / ${collection.size}",
                            fontWeight = FontWeight.SemiBold, color = PvpColors.TextPrimary)
                        Text("Só entram builds com nível, Fast Move e Charged Move. Os restantes serão excluídos, sem suposições.",
                            color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text("1v1 com dados explícitos · não é previsão 3v3", color = PvpColors.StateWarning,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                Text("Liga", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(League.GREAT, League.ULTRA, League.MASTER).forEach { option ->
                        FilterChip(
                            selected = league == option,
                            onClick = { league = option; result = null },
                            label = { Text(when (option) {
                                League.GREAT -> "Great"
                                League.ULTRA -> "Ultra"
                                League.MASTER -> "Master"
                                League.LITTLE -> "Little"
                            }) }
                        )
                    }
                }
            }

            if (opponentSpecies == null) {
                item {
                    Text("Adversário", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        query,
                        { query = it },
                        label = { Text("Pesquisar Pokémon adversário") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                items(searchResults, key = { "opp:${it.speciesId}" }) { sp ->
                    Card(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        opponentSpecies = sp
                        query = sp.name
                        fastMoveId = null
                        chargedMoveIds = emptyList()
                        result = null
                    }, colors = CardDefaults.cardColors(containerColor = PvpColors.SurfaceCard)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sp.name, fontWeight = FontWeight.SemiBold)
                            Text("#${sp.dex}")
                        }
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PvpColors.SurfaceCard)) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(opponentSpecies!!.name, fontWeight = FontWeight.Bold)
                                Text("#${opponentSpecies!!.dex}", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = {
                                opponentSpecies = null
                                query = ""
                                fastMoveId = null
                                chargedMoveIds = emptyList()
                                result = null
                            }) { Text("Trocar") }
                        }
                    }
                }
                item {
                    Text("IVs e nível do adversário", fontWeight = FontWeight.Bold)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(atkText, { atkText = it.filter(Char::isDigit).take(2); result = null }, label = { Text("Ataque") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(defText, { defText = it.filter(Char::isDigit).take(2); result = null }, label = { Text("Defesa") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(hpText, { hpText = it.filter(Char::isDigit).take(2); result = null }, label = { Text("HP") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(levelText, { levelText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }; result = null }, label = { Text("Nível") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
                item { Text("Fast Move adversário", fontWeight = FontWeight.Bold) }
                items(opponentFast, key = { "oppfast:${it.moveId}" }) { move ->
                    Row(
                        Modifier.fillMaxWidth().clickable { fastMoveId = move.moveId; result = null },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = fastMoveId == move.moveId, onClick = { fastMoveId = move.moveId; result = null })
                        Text(move.name)
                    }
                }
                item { Text("Charged Moves adversário · 1 ou 2", fontWeight = FontWeight.Bold) }
                items(opponentCharged, key = { "oppcharged:${it.moveId}" }) { move ->
                    val checked = move.moveId in chargedMoveIds
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            chargedMoveIds = when {
                                checked -> chargedMoveIds - move.moveId
                                chargedMoveIds.size < 2 -> chargedMoveIds + move.moveId
                                else -> chargedMoveIds
                            }
                            result = null
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                chargedMoveIds = when {
                                    checked -> chargedMoveIds - move.moveId
                                    chargedMoveIds.size < 2 -> chargedMoveIds + move.moveId
                                    else -> chargedMoveIds
                                }
                                result = null
                            }
                        )
                        Text(move.name)
                    }
                }
                item {
                    Text("Shields", fontWeight = FontWeight.Bold)
                    Text("Teus", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..2).forEach { n -> FilterChip(selected = ownShields == n, onClick = { ownShields = n; result = null }, label = { Text(n.toString()) }) }
                    }
                    Text("Adversário", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..2).forEach { n -> FilterChip(selected = opponentShields == n, onClick = { opponentShields = n; result = null }, label = { Text(n.toString()) }) }
                    }
                }
                item {
                    val atk = atkText.toIntOrNull()
                    val def = defText.toIntOrNull()
                    val hp = hpText.toIntOrNull()
                    val level = levelText.replace(',', '.').toDoubleOrNull()
                    val inputsValid = atk != null && atk in 0..15 &&
                        def != null && def in 0..15 &&
                        hp != null && hp in 0..15 &&
                        level != null && level in 1.0..51.0 && level * 2 == (level * 2).toInt().toDouble() &&
                        fastMoveId != null && chargedMoveIds.isNotEmpty() && completeOwnedCount > 0

                    Button(
                        onClick = {
                            val sp = opponentSpecies ?: return@Button
                            val iv = IvSpread(atk!!, def!!, hp!!)
                            val opponent = OpponentBuild(
                                speciesId = sp.speciesId,
                                fastMoveId = fastMoveId,
                                chargedMoveIds = chargedMoveIds,
                                iv = iv,
                                level = level,
                                isShadow = sp.isShadow
                            )
                            val scenario = MatchupScenario(
                                league = league,
                                ownShields = ownShields,
                                opponentShields = opponentShields
                            )
                            val requestCollection = collection.toList()
                            scope.launch {
                                busy = true
                                error = null
                                val computed = runCatching {
                                    withContext(Dispatchers.Default) {
                                        val evaluator = LocalBattleMatchupEvaluator(catalog, moves)
                                        val advisor = CollectionMatchupAdvisor(catalog, evaluator)
                                        advisor.recommend(
                                            collection = requestCollection,
                                            opponent = opponent,
                                            scenario = scenario,
                                            // B04 needs every certified candidate that the advisor can return.
                                            // Non-returned members remain explicitly NOT EVALUATED.
                                            limit = 50,
                                            ruleset = StandardRulesets.forLeague(league)
                                        )
                                    }
                                }.onFailure { error = it.message ?: "Falha na avaliação." }.getOrNull()
                                if (requestCollection != collection) {
                                    result = null
                                    error = "A coleção foi alterada durante o cálculo. Repete a análise."
                                } else {
                                    analyzedCollectionSnapshot = requestCollection
                                    result = computed
                                }
                                busy = false
                            }
                        },
                        enabled = inputsValid && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(if (busy) "A analisar…" else "Analisar matchup") }
                    if (!inputsValid) {
                        Text("Preenche IVs 0–15, nível em passos de 0,5, Fast Move e pelo menos um Charged Move. Também precisas de pelo menos um build completo na Collection.", style = MaterialTheme.typography.labelSmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }

            result?.let { analysis ->
                item {
                    HorizontalDivider()
                    Text("Resultado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    analysis.refusalReason?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                    Text("Rota de confiança: ${analysis.certificationRoute ?: "não disponível"}", style = MaterialTheme.typography.labelSmall)
                }
                items(analysis.recommendations, key = { "rec:${it.ownedPokemonId}" }) { rec ->
                    val owned = collection.firstOrNull { it.id == rec.ownedPokemonId }
                    val name = byId[rec.speciesId]?.name ?: rec.speciesId
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("#${rec.position} · $name", fontWeight = FontWeight.Bold)
                                Text("${rec.evaluation.battleRating}/1000", fontWeight = FontWeight.SemiBold)
                            }
                            owned?.let { Text("IV ${it.iv.attack}/${it.iv.defense}/${it.iv.stamina} · ${rec.pokemonCp} CP", style = MaterialTheme.typography.bodySmall) }
                            Text("${rec.evaluation.outcome} · ${rec.evaluation.band} · ${rec.evaluation.turns} turns", style = MaterialTheme.typography.bodySmall)
                            Text(rec.why, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (analysis.exclusions.isNotEmpty()) {
                    item {
                        Text("Excluídos · ${analysis.exclusions.size}", fontWeight = FontWeight.Bold)
                        Text("Não foram simulados porque faltam dados ou um trust guard recusou o cenário.", style = MaterialTheme.typography.labelSmall)
                    }
                    items(analysis.exclusions.take(20), key = { "exc:${it.ownedPokemonId}" }) { exclusion ->
                        val name = byId[exclusion.speciesId]?.name ?: exclusion.speciesId
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(10.dp)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text("${exclusion.reason} · ${exclusion.detail}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeamLabRootScreen(
    catalog: List<PokemonSpecies>,
    moves: List<PvpMove>
) {
    val context = LocalContext.current
    val repository = remember(context) { CollectionRepository.get(context) }
    var owned by remember { mutableStateOf<List<OwnedPokemon>>(emptyList()) }
    var plans by remember { mutableStateOf<List<PvpBuildPlan>>(emptyList()) }
    var teams by remember { mutableStateOf<List<SavedTeam>>(emptyList()) }
    var league by remember { mutableStateOf(League.GREAT) }
    var style by remember { mutableStateOf(TeamStyleBias.BALANCED) }
    var anchorId by remember { mutableStateOf<String?>(null) }
    var selectedTeamId by remember { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf(TeamsGoldenView.HOME) }

    LaunchedEffect(repository) { repository.collection.collectLatest { owned = it } }
    LaunchedEffect(repository) { repository.buildPlans.collectLatest { plans = it } }
    LaunchedEffect(repository) { repository.savedTeams.collectLatest { teams = it } }

    when (view) {
        TeamsGoldenView.HOME -> TeamsGoldenHomeScreen(
            catalog = catalog, owned = owned, plans = plans, teams = teams, league = league,
            onLeagueChange = { league = it },
            onCreate = { view = TeamsGoldenView.BUILDER },
            onOpenTeam = { selectedTeamId = it; view = TeamsGoldenView.DETAIL }
        )
        TeamsGoldenView.BUILDER -> TeamsGoldenBuilderScreen(
            catalog = catalog, owned = owned, plans = plans, league = league, style = style,
            selectedAnchorId = anchorId,
            onLeagueChange = { league = it }, onStyleChange = { style = it },
            onAnchorChange = { anchorId = it },
            onBack = { view = TeamsGoldenView.HOME },
            onGenerate = { view = TeamsGoldenView.ANALYSIS }
        )
        TeamsGoldenView.DETAIL -> {
            val selected = teams.firstOrNull { it.id == selectedTeamId }
            if (selected == null) {
                LaunchedEffect(selectedTeamId, teams) { view = TeamsGoldenView.HOME }
            } else {
                TeamsGoldenDetailScreen(
                    team = selected, owned = owned, catalog = catalog, plans = plans,
                    onBack = { view = TeamsGoldenView.HOME },
                    onAdjust = {
                        league = selected.league
                        anchorId = null
                        view = TeamsGoldenView.BUILDER
                    }
                )
            }
        }
        TeamsGoldenView.ANALYSIS -> TeamLabScreen(
            catalog = catalog, moves = moves,
            onBack = { view = TeamsGoldenView.BUILDER },
            initialLeague = league, initialStyle = style, initialAnchorId = anchorId
        )
    }
}

@Composable
fun BattlesRootScreen(catalog: List<PokemonSpecies>, moves: List<PvpMove>) {
    val context = LocalContext.current
    val repository = remember(context) { CollectionRepository.get(context) }
    var owned by remember { mutableStateOf<List<OwnedPokemon>>(emptyList()) }
    var teams by remember { mutableStateOf<List<SavedTeam>>(emptyList()) }
    var battles by remember { mutableStateOf<List<BattleRecord>>(emptyList()) }
    var league by remember { mutableStateOf(League.GREAT) }
    var page by remember { mutableStateOf(BattlesGoldenPage.HOME) }
    LaunchedEffect(repository) { repository.collection.collectLatest { owned = it } }
    LaunchedEffect(repository) { repository.savedTeams.collectLatest { teams = it } }
    LaunchedEffect(repository) { repository.battleRecords.collectLatest { battles = it } }
    when (page) {
        BattlesGoldenPage.HOME -> BattlesGoldenHomeScreen(catalog, owned, teams, battles, league,
            onLeagueChange = { league = it }, onAnalyze = { page = BattlesGoldenPage.MATCHUP },
            onRecord = { page = BattlesGoldenPage.LOG }, onHistory = { page = BattlesGoldenPage.HISTORY })
        BattlesGoldenPage.MATCHUP -> MatchupAdvisorScreen(catalog, moves, onBack = { page = BattlesGoldenPage.HOME }, initialLeague = league)
        BattlesGoldenPage.LOG -> BattleLogScreen(catalog, onBack = { page = BattlesGoldenPage.HOME })
        BattlesGoldenPage.HISTORY -> BattlesGoldenHistoryScreen(catalog, teams, battles, league, onBack = { page = BattlesGoldenPage.HOME })
    }
}

private enum class BattlesGoldenPage { HOME, MATCHUP, LOG, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamLabScreen(
    catalog: List<PokemonSpecies>,
    moves: List<PvpMove>,
    onBack: (() -> Unit)?,
    initialLeague: League = League.GREAT,
    initialStyle: TeamStyleBias = TeamStyleBias.BALANCED,
    initialAnchorId: String? = null
) {
    val context = LocalContext.current
    val collectionRepository = remember(context) { CollectionRepository.get(context) }
    val referenceRepository = remember(context) { ReferencePackRepository.get(context) }
    val scope = rememberCoroutineScope()
    val speciesById = remember(catalog) { catalog.associateBy { it.speciesId } }

    var collection by remember { mutableStateOf<List<OwnedPokemon>>(emptyList()) }
    var buildPlans by remember { mutableStateOf<List<PvpBuildPlan>>(emptyList()) }
    var battleRecords by remember { mutableStateOf<List<BattleRecord>>(emptyList()) }
    var activeSummaries by remember { mutableStateOf<List<ReferencePackSummary>>(emptyList()) }
    var league by remember(initialLeague) { mutableStateOf(initialLeague) }
    var resolution by remember { mutableStateOf<ReferenceResolution?>(null) }
    var result by remember { mutableStateOf<TeamLabResult?>(null) }
    var goldenAnalysisPage by remember { mutableStateOf(TeamsGoldenAnalysisPage.RESULTS) }
    var selectedLineupIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var ownShields by remember { mutableIntStateOf(1) }
    var opponentShields by remember { mutableIntStateOf(1) }
    var onlyReady by remember { mutableStateOf(false) }
    var allowXl by remember { mutableStateOf(true) }
    var styleBias by remember(initialStyle) { mutableStateOf(initialStyle) }
    var metaBlendMode by remember { mutableStateOf(MetaBlendMode.HYBRID) }
    var lastBlend by remember { mutableStateOf<MetaBlendResult?>(null) }

    LaunchedEffect(collectionRepository) {
        collectionRepository.collection.collectLatest { collection = it }
    }
    LaunchedEffect(collectionRepository) {
        collectionRepository.buildPlans.collectLatest { buildPlans = it }
    }
    LaunchedEffect(collectionRepository) {
        collectionRepository.battleRecords.collectLatest { battleRecords = it }
    }
    LaunchedEffect(referenceRepository) {
        referenceRepository.activeSummaries.collectLatest { activeSummaries = it }
    }
    LaunchedEffect(league, activeSummaries, catalog, moves) {
        result = null
        lastBlend = null
        savedMessage = null
        resolution = if (catalog.isNotEmpty() && moves.isNotEmpty()) {
            referenceRepository.resolve(catalog, moves, league)
        } else {
            ReferenceResolution(null, "Data Pack local ainda está a carregar.")
        }
    }

    val completeOwnedCount = remember(collection) {
        collection.count { it.level != null && it.fastMoveId != null && it.chargedMoveIds.isNotEmpty() }
    }
    val readyOwnedIds = remember(buildPlans, league) {
        buildPlans.filter { it.league == league && it.status in setOf(BuildStatus.READY, BuildStatus.ACTIVE) }
            .map { it.ownedPokemonId }.toSet()
    }
    val readyCompleteCount = remember(collection, readyOwnedIds) {
        collection.count {
            it.id in readyOwnedIds && it.level != null && it.fastMoveId != null && it.chargedMoveIds.isNotEmpty()
        }
    }
    val activeSummary = activeSummaries.firstOrNull { it.league == league }
    val personalBattleCount = remember(battleRecords, league) { battleRecords.count { it.league == league } }

    // GOLDEN T04/T05/T06: presentation of the SAME certified TeamLab result.
    // Refused and insufficient candidates remain in the existing diagnostic form below.
    val readyAnalysis = result?.takeIf { it.status == TeamLabStatus.READY && it.primary != null }
    if (readyAnalysis != null) {
        TeamsGoldenAnalysisScreen(
            page = goldenAnalysisPage, result = readyAnalysis, league = league,
            owned = collection, catalog = catalog, selectedIds = selectedLineupIds,
            onBack = {
                if (goldenAnalysisPage == TeamsGoldenAnalysisPage.RESULTS) onBack?.invoke()
                else goldenAnalysisPage = TeamsGoldenAnalysisPage.RESULTS
            },
            onShowIdeal = { goldenAnalysisPage = TeamsGoldenAnalysisPage.IDEAL },
            onShowAdjust = { goldenAnalysisPage = TeamsGoldenAnalysisPage.ADJUST },
            onShowResults = { goldenAnalysisPage = TeamsGoldenAnalysisPage.RESULTS },
            onSelect = { ids ->
                if (TeamsGoldenResultPolicy.isVerified(
                    readyAnalysis.primary!!.memberOwnedPokemonIds,
                    readyAnalysis.alternatives.map { it.memberOwnedPokemonIds }, ids)) {
                    selectedLineupIds = ids
                    goldenAnalysisPage = TeamsGoldenAnalysisPage.RESULTS
                    savedMessage = null
                }
            },
            onSave = { ids ->
                val original = readyAnalysis.primary!!.memberOwnedPokemonIds
                val roles = readyAnalysis.roleSuggestions.associate { suggestion ->
                    suggestion.ownedPokemonId to when (suggestion.role) {
                        SuggestedTeamRole.LEAD -> TeamRole.LEAD.name
                        SuggestedTeamRole.SAFE_SWITCH -> TeamRole.SAFE_SWITCH.name
                        SuggestedTeamRole.CLOSER -> TeamRole.CLOSER.name
                    }
                }
                val mapped = TeamsGoldenResultPolicy.rolesForVerifiedSingleSwap(
                    original, readyAnalysis.alternatives.map { it.memberOwnedPokemonIds }, ids, roles)
                val eligible = TeamsGoldenPolicy.completeCandidates(collection).map { it.id }.toSet()
                if (mapped == null || ids.any { it !in eligible }) {
                    savedMessage = "Equipa não guardada: papéis ou exemplares não verificáveis."
                } else {
                    scope.launch {
                        val members = ids.map { TeamMember(it, TeamRole.valueOf(mapped.getValue(it))) }
                        val now = System.currentTimeMillis()
                        runCatching {
                            collectionRepository.upsertSavedTeam(SavedTeam(
                                id = UUID.randomUUID().toString(),
                                name = "Team Lab ${league.name} ${activeSummary?.sourceVersion?.take(7) ?: "snapshot"}",
                                league = league, members = members,
                                notes = "Team Lab validado · ${readyAnalysis.metaGroupId} · ${readyAnalysis.certificationRoute ?: "sem rota"}",
                                createdAtEpochMs = now
                            ))
                        }.onSuccess { savedMessage = "Equipa guardada em Room." }
                            .onFailure { savedMessage = "Não foi possível guardar: ${it.message ?: "erro local"}" }
                    }
                }
            },
            savedMessage = savedMessage
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equipas") },
                navigationIcon = {
                    if (onBack != null) TextButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
        ) {
            item {
                Text("Equipas a partir da tua Collection", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (initialAnchorId != null) {
                    val chosen = collection.firstOrNull { it.id == initialAnchorId }
                    val chosenName = chosen?.let { speciesById[it.speciesId]?.name ?: it.speciesId } ?: "indisponível"
                    Text("Pokémon base fixado: $chosenName · sujeito a validação de elegibilidade.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Usa um Meta Pack versionado + Ruleset persistente e simulações 1v1 certificadas. Coverage Score serve para ordenar cobertura; não é probabilidade de vitória 3v3.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Text("Liga", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(League.GREAT, League.ULTRA, League.MASTER).forEach { option ->
                        FilterChip(
                            selected = league == option,
                            onClick = { league = option; result = null; error = null },
                            label = { Text(when (option) {
                                League.GREAT -> "Great"
                                League.ULTRA -> "Ultra"
                                League.MASTER -> "Master"
                                else -> option.name
                            }) }
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Reference Pack", fontWeight = FontWeight.Bold)
                            Text(if (resolution?.bundle != null) "READY" else "NEEDS DATA", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        activeSummary?.let { summary ->
                            Text("${summary.sourceName} · ${summary.sourceVersion.take(12)}", style = MaterialTheme.typography.bodySmall)
                            Text("${summary.entryCount} adversários · Data ${summary.dataPackHashSha256.take(12)}", style = MaterialTheme.typography.labelSmall)
                        } ?: Text("Ainda não existe snapshot persistente para esta liga.", style = MaterialTheme.typography.bodySmall)
                        resolution?.reason?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall) }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    savedMessage = null
                                    runCatching { referenceRepository.syncPublicStandard(catalog, moves, league) }
                                        .onFailure { error = it.message ?: "Falha ao atualizar o Meta Pack." }
                                    resolution = referenceRepository.resolve(catalog, moves, league)
                                    result = null
                                    busy = false
                                }
                            },
                            enabled = catalog.isNotEmpty() && moves.isNotEmpty() && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "A atualizar…" else "Atualizar Meta Pack PvPoke") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    val rolledBack = referenceRepository.rollback(league)
                                    resolution = referenceRepository.resolve(catalog, moves, league)
                                    result = null
                                    savedMessage = if (rolledBack) "Meta Pack anterior reativado." else "Não existe snapshot anterior para rollback."
                                    busy = false
                                }
                            },
                            enabled = activeSummary != null && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Rollback para snapshot anterior") }
                        Text("A atualização resolve uma revisão única e fixa os dados dessa revisão. Ranking/score da fonte não é tratado como prevalência nem win probability.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Collection readiness", fontWeight = FontWeight.Bold)
                        Text("$completeOwnedCount build(s) completo(s) · $readyCompleteCount com plano READY/ACTIVE em ${league.name}", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Só READY/ACTIVE", fontWeight = FontWeight.SemiBold)
                                Text("Exige um PvpBuildPlan pronto nesta liga.", style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = onlyReady, onCheckedChange = { onlyReady = it; result = null })
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Permitir XL", fontWeight = FontWeight.SemiBold)
                                Text("Quando desligado, nível >40 é excluído.", style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = allowXl, onCheckedChange = { allowXl = it; result = null })
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Meta usado na análise", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetaBlendMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = metaBlendMode == mode,
                                    onClick = { metaBlendMode = mode; result = null; lastBlend = null },
                                    label = { Text(when (mode) {
                                        MetaBlendMode.GLOBAL -> "Global"
                                        MetaBlendMode.HYBRID -> "Hybrid"
                                        MetaBlendMode.PERSONAL -> "Personal"
                                    }) }
                                )
                            }
                        }
                        Text("$personalBattleCount batalha(s) locais nesta liga.", style = MaterialTheme.typography.bodySmall)
                        Text(
                            when (metaBlendMode) {
                                MetaBlendMode.GLOBAL -> "Usa apenas o Meta Pack persistente."
                                MetaBlendMode.HYBRID -> "Mistura o Meta Pack com frequência recente observada no teu Battle Log. A amostra pessoal cresce gradualmente; não substitui o global."
                                MetaBlendMode.PERSONAL -> "Usa apenas espécies observadas no teu Battle Log que também tenham build válido no Meta Pack. Pode recusar se não houver evidência suficiente."
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text("O Battle Log nunca altera reference.db; esta mistura existe apenas durante a análise.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            item {
                Text("Perfil", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamStyleBias.entries.forEach { style ->
                        FilterChip(
                            selected = styleBias == style,
                            onClick = { styleBias = style; result = null },
                            label = { Text(when (style) {
                                TeamStyleBias.BALANCED -> "Balanced"
                                TeamStyleBias.BULKY -> "Bulky"
                                TeamStyleBias.AGGRESSIVE -> "Aggressive"
                            }) }
                        )
                    }
                }
                Text("O perfil apenas reordena equipas já válidas; nunca ultrapassa Ruleset/trust guards.", style = MaterialTheme.typography.labelSmall)
            }
            item {
                Text("Shields da simulação 1v1", fontWeight = FontWeight.Bold)
                Text("Teus", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..2).forEach { n -> FilterChip(selected = ownShields == n, onClick = { ownShields = n; result = null }, label = { Text(n.toString()) }) }
                }
                Text("Adversário", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..2).forEach { n -> FilterChip(selected = opponentShields == n, onClick = { opponentShields = n; result = null }, label = { Text(n.toString()) }) }
                }
            }
            item {
                val enoughCandidates = if (onlyReady) readyCompleteCount >= 3 else completeOwnedCount >= 3
                val canAnalyze = resolution?.bundle != null && enoughCandidates && !busy
                Button(
                    onClick = {
                        val bundle = resolution?.bundle ?: return@Button
                        scope.launch {
                            busy = true
                            error = null
                            savedMessage = null
                            val analyzed = runCatching {
                                withContext(Dispatchers.Default) {
                                    val blend = PersonalMetaTeamBridge.blend(
                                        global = bundle.meta,
                                        battles = battleRecords,
                                        mode = metaBlendMode
                                    )
                                    val analysisMeta = blend.meta ?: error(blend.refusalReason ?: "Meta derivado indisponível.")
                                    val evaluator = LocalBattleMatchupEvaluator(catalog, moves)
                                    val lab = CollectionTeamLab(catalog, evaluator)
                                    val analysis = lab.analyze(
                                        collection = collection,
                                        builds = buildPlans,
                                        meta = analysisMeta,
                                        ruleset = bundle.ruleset,
                                        scenario = MatchupScenario(
                                            league = league,
                                            ownShields = ownShields,
                                            opponentShields = opponentShields
                                        ),
                                        constraints = TeamAdvisorConstraints(
                                            onlyReady = onlyReady,
                                            allowXl = allowXl,
                                            lockedOwnedPokemonIds = initialAnchorId?.let { setOf(it) }.orEmpty()
                                        ),
                                        playstyle = PlaystyleProfile(styleBias = styleBias)
                                    )
                                    analysis to blend
                                }
                            }
                            analyzed.onSuccess { (analysis, blend) ->
                                result = analysis
                                selectedLineupIds = analysis.primary?.memberOwnedPokemonIds.orEmpty()
                                goldenAnalysisPage = TeamsGoldenAnalysisPage.RESULTS
                                lastBlend = blend
                            }.onFailure { error = it.message ?: "Falha no Team Lab." }
                            busy = false
                        }
                    },
                    enabled = canAnalyze,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "A analisar…" else "Gerar equipa") }
                if (resolution?.bundle == null) {
                    Text("Primeiro ativa um Reference Pack válido para esta liga.", style = MaterialTheme.typography.labelSmall)
                } else if (!enoughCandidates) {
                    Text(if (onlyReady) "Precisas de 3 builds completos com plano READY/ACTIVE." else "Precisas de pelo menos 3 Pokémon com nível + Fast Move + Charged Move confirmados.", style = MaterialTheme.typography.labelSmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
            }
            lastBlend?.let { blend ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Proveniência da análise", fontWeight = FontWeight.Bold)
                            Text("Modo ${blend.mode} · peso pessoal ${"%.1f".format(blend.personalBlendWeight * 100)}%", style = MaterialTheme.typography.bodySmall)
                            Text("${blend.matchedOpponentObservations} observações ligadas · n efetivo recente ${"%.1f".format(blend.effectivePersonalObservations)}", style = MaterialTheme.typography.bodySmall)
                            blend.warning?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }

            result?.let { analysis ->
                item {
                    HorizontalDivider()
                    Text("Resultado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Estado: ${analysis.status} · confiança: ${analysis.certificationRoute ?: "não disponível"}", style = MaterialTheme.typography.labelSmall)
                    analysis.refusalReason?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                }
                if (analysis.status == TeamLabStatus.READY && analysis.primary != null) {
                    val primary = requireNotNull(analysis.primary)
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Equipa principal", fontWeight = FontWeight.Bold)
                                    Text("Coverage ${"%.1f".format(primary.coverage.coverageScorePercent)}%", fontWeight = FontWeight.SemiBold)
                                }
                                Text("Não é win probability.", style = MaterialTheme.typography.labelSmall)
                                primary.memberOwnedPokemonIds.forEach { ownedId ->
                                    val owned = collection.firstOrNull { it.id == ownedId }
                                    val name = owned?.let { speciesById[it.speciesId]?.name ?: it.speciesId } ?: ownedId
                                    val role = analysis.roleSuggestions.firstOrNull { it.ownedPokemonId == ownedId }
                                    Text("${role?.role ?: "ROLE?"} · $name", fontWeight = FontWeight.SemiBold)
                                    role?.let { Text(it.basis, style = MaterialTheme.typography.labelSmall) }
                                }
                                Text("Meta avaliado: ${"%.1f".format(primary.coverage.evaluatedCoveragePercent)}% · ameaças ${"%.1f".format(primary.coverage.threatMetaWeight)} weight", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    analysis.why?.let { why ->
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("Porque esta equipa", fontWeight = FontWeight.Bold)
                                    Text(why.summary)
                                    why.strengths.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                                    why.risks.take(4).forEach { Text("Risco · $it", style = MaterialTheme.typography.bodySmall) }
                                    why.caveats.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                                }
                            }
                        }
                    }
                    analysis.coverageMatrix?.let { matrix ->
                        val threats = matrix.rows.filter { it.state.name.contains("THREAT") }.sortedByDescending { it.metaWeight }.take(8)
                        if (threats.isNotEmpty()) {
                            item { Text("Principais ameaças", fontWeight = FontWeight.Bold) }
                            items(threats, key = { "threat:${it.metaEntryKey}" }) { row ->
                                val name = speciesById[row.opponentSpeciesId]?.name ?: row.opponentSpeciesId
                                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(name)
                                        Text("${row.state} · ${row.bestBattleRating ?: "?"}/1000", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    if (analysis.alternatives.isNotEmpty()) {
                        item { Text("Alternativas", fontWeight = FontWeight.Bold) }
                        items(analysis.alternatives, key = { "alt:${it.kind}" }) { alt ->
                            val names = alt.memberOwnedPokemonIds.map { id ->
                                collection.firstOrNull { it.id == id }?.let { owned -> speciesById[owned.speciesId]?.name ?: owned.speciesId } ?: id
                            }
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("${alt.kind} · Coverage ${"%.1f".format(alt.coverageScorePercent)}%", fontWeight = FontWeight.SemiBold)
                                    Text(names.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                    Text(alt.reason, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                val rolesByOwned = analysis.roleSuggestions.associate { suggestion ->
                                    val role = when (suggestion.role) {
                                        SuggestedTeamRole.LEAD -> TeamRole.LEAD
                                        SuggestedTeamRole.SAFE_SWITCH -> TeamRole.SAFE_SWITCH
                                        SuggestedTeamRole.CLOSER -> TeamRole.CLOSER
                                    }
                                    suggestion.ownedPokemonId to role
                                }
                                val members = primary.memberOwnedPokemonIds.mapNotNull { id -> rolesByOwned[id]?.let { TeamMember(id, it) } }
                                if (members.size == 3) {
                                    scope.launch {
                                        val now = System.currentTimeMillis()
                                        collectionRepository.upsertSavedTeam(
                                            SavedTeam(
                                                id = UUID.randomUUID().toString(),
                                                name = "Team Lab ${league.name} ${activeSummary?.sourceVersion?.take(7) ?: "snapshot"}",
                                                league = league,
                                                members = members,
                                                notes = "Coverage ${"%.1f".format(primary.coverage.coverageScorePercent)}%; source ${activeSummary?.sourceName ?: "unknown"} ${activeSummary?.sourceVersion ?: "unknown"}",
                                                createdAtEpochMs = now
                                            )
                                        )
                                        savedMessage = "Equipa guardada localmente."
                                    }
                                } else {
                                    savedMessage = "Não foi possível mapear os três papéis; equipa não guardada."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Guardar equipa") }
                    }
                } else if (analysis.status == TeamLabStatus.INSUFFICIENT_CANDIDATES) {
                    item { Text("Não há três candidatos elegíveis suficientes. Vê os excluídos abaixo e completa os builds necessários.", color = MaterialTheme.colorScheme.tertiary) }
                }
                if (analysis.exclusions.isNotEmpty()) {
                    item {
                        Text("Excluídos · ${analysis.exclusions.size}", fontWeight = FontWeight.Bold)
                        Text("Nenhum candidato é descartado silenciosamente.", style = MaterialTheme.typography.labelSmall)
                    }
                    items(analysis.exclusions.take(30), key = { "tl-exc:${it.ownedPokemonId}:${it.reason}" }) { exclusion ->
                        val name = speciesById[exclusion.speciesId]?.name ?: exclusion.speciesId
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(10.dp)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text("${exclusion.reason} · ${exclusion.detail}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class BattleHubMode { LOG, META }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BattleLogScreen(
    catalog: List<PokemonSpecies>,
    onBack: (() -> Unit)?
) {
    val context = LocalContext.current
    val repository = remember(context) { CollectionRepository.get(context) }
    val scope = rememberCoroutineScope()
    val speciesById = remember(catalog) { catalog.associateBy { it.speciesId } }

    var collection by remember { mutableStateOf<List<OwnedPokemon>>(emptyList()) }
    var savedTeams by remember { mutableStateOf<List<SavedTeam>>(emptyList()) }
    var battles by remember { mutableStateOf<List<BattleRecord>>(emptyList()) }
    var mode by remember { mutableStateOf(BattleHubMode.LOG) }
    var selectedTeamId by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf(BattleOutcome.WIN) }
    var opponentLead by remember { mutableStateOf<PokemonSpecies?>(null) }
    var opponentSwitch by remember { mutableStateOf<PokemonSpecies?>(null) }
    var opponentCloser by remember { mutableStateOf<PokemonSpecies?>(null) }
    var ratingBefore by remember { mutableStateOf("") }
    var ratingAfter by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var metaLeague by remember { mutableStateOf(League.GREAT) }

    LaunchedEffect(repository) { repository.collection.collectLatest { collection = it } }
    LaunchedEffect(repository) { repository.savedTeams.collectLatest { teams ->
        savedTeams = teams
        if (selectedTeamId == null || teams.none { it.id == selectedTeamId }) selectedTeamId = teams.firstOrNull()?.id
    } }
    LaunchedEffect(repository) { repository.battleRecords.collectLatest { battles = it } }

    val selectedTeam = savedTeams.firstOrNull { it.id == selectedTeamId }
    val teamMembersById = remember(collection) { collection.associateBy { it.id } }
    val snapshot = remember(battles, metaLeague) {
        PersonalMetaAnalytics.snapshot(battles, PersonalMetaQuery(league = metaLeague))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batalhas") },
                navigationIcon = {
                    if (onBack != null) TextButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == BattleHubMode.LOG, onClick = { mode = BattleHubMode.LOG }, label = { Text("Registar") })
                    FilterChip(selected = mode == BattleHubMode.META, onClick = { mode = BattleHubMode.META }, label = { Text("My Meta") })
                }
            }

            if (mode == BattleHubMode.LOG) {
                item {
                    Text("Registo local", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("O adversário pode ficar parcial/UNKNOWN. O registo nunca preenche espécies ou moves que não observaste.", style = MaterialTheme.typography.bodySmall)
                }
                if (savedTeams.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Ainda não tens uma equipa guardada", fontWeight = FontWeight.Bold)
                                Text("Guarda uma equipa no Team Lab para começares a registar batalhas com os três slots bem definidos.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    item {
                        Text("A tua equipa", fontWeight = FontWeight.Bold)
                        savedTeams.take(12).forEach { team ->
                            val names = team.members.map { member ->
                                teamMembersById[member.ownedPokemonId]?.let { owned -> speciesById[owned.speciesId]?.name ?: owned.speciesId } ?: "?"
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedTeamId = team.id; saveMessage = null },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedTeamId == team.id, onClick = { selectedTeamId = team.id; saveMessage = null })
                                Column(Modifier.weight(1f)) {
                                    Text(team.name, fontWeight = FontWeight.SemiBold)
                                    Text("${team.league.name} · ${names.joinToString(" · ")}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    item {
                        Text("Resultado", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BattleOutcome.entries.forEach { option ->
                                FilterChip(selected = outcome == option, onClick = { outcome = option }, label = { Text(option.name) })
                            }
                        }
                    }
                    item {
                        Text("Adversário · opcional além do que observaste", fontWeight = FontWeight.Bold)
                        BattleOpponentPicker("Lead", catalog, opponentLead) { opponentLead = it }
                        BattleOpponentPicker("Switch", catalog, opponentSwitch) { opponentSwitch = it }
                        BattleOpponentPicker("Closer", catalog, opponentCloser) { opponentCloser = it }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ratingBefore,
                                onValueChange = { ratingBefore = it.filter(Char::isDigit).take(5) },
                                label = { Text("Rating antes") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = ratingAfter,
                                onValueChange = { ratingAfter = it.filter(Char::isDigit).take(5) },
                                label = { Text("Rating depois") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            label = { Text("Tags · separadas por vírgula") },
                            placeholder = { Text("set-1, noite, teste-equipa") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notas") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                val team = selectedTeam ?: return@Button
                                val own = team.members.map { member ->
                                    OwnBattlePokemon(
                                        ownedPokemonId = member.ownedPokemonId,
                                        slot = when (member.role) {
                                            TeamRole.LEAD -> BattleSlot.LEAD
                                            TeamRole.SAFE_SWITCH -> BattleSlot.SWITCH
                                            TeamRole.CLOSER -> BattleSlot.CLOSER
                                        }
                                    )
                                }
                                val opponents = buildList {
                                    opponentLead?.let { add(OpponentBattlePokemon(it.speciesId, BattleSlot.LEAD, isShadow = it.isShadow)) }
                                    opponentSwitch?.let { add(OpponentBattlePokemon(it.speciesId, BattleSlot.SWITCH, isShadow = it.isShadow)) }
                                    opponentCloser?.let { add(OpponentBattlePokemon(it.speciesId, BattleSlot.CLOSER, isShadow = it.isShadow)) }
                                }
                                val tagSet = tags.split(',').map(String::trim).filter(String::isNotBlank).toSet()
                                val record = BattleRecord(
                                    id = UUID.randomUUID().toString(),
                                    league = team.league,
                                    outcome = outcome,
                                    playedAtEpochMs = System.currentTimeMillis(),
                                    ownTeamId = team.id,
                                    ownPokemon = own,
                                    opponentPokemon = opponents,
                                    ratingBefore = ratingBefore.toIntOrNull(),
                                    ratingAfter = ratingAfter.toIntOrNull(),
                                    notes = notes.trim().takeIf(String::isNotBlank),
                                    tags = tagSet
                                )
                                scope.launch {
                                    repository.upsertBattleRecord(record)
                                    opponentLead = null
                                    opponentSwitch = null
                                    opponentCloser = null
                                    ratingBefore = ""
                                    ratingAfter = ""
                                    notes = ""
                                    tags = ""
                                    saveMessage = "Batalha guardada localmente."
                                }
                            },
                            enabled = selectedTeam != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Guardar batalha") }
                        saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (battles.isNotEmpty()) {
                        item {
                            HorizontalDivider()
                            Text("Histórico recente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        items(battles.take(30), key = { "battle:${it.id}" }) { battle ->
                            val teamName = savedTeams.firstOrNull { it.id == battle.ownTeamId }?.name ?: "Equipa não identificada"
                            val opponents = battle.opponentPokemon.map { speciesById[it.speciesId]?.name ?: it.speciesId }
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${battle.outcome} · ${battle.league.name}", fontWeight = FontWeight.Bold)
                                        Text(formatBattleDate(battle.playedAtEpochMs), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(teamName, style = MaterialTheme.typography.bodySmall)
                                    Text(if (opponents.isEmpty()) "Adversário: UNKNOWN" else "Vs ${opponents.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                                    if (battle.ratingBefore != null || battle.ratingAfter != null) {
                                        Text("Rating ${battle.ratingBefore ?: "?"} → ${battle.ratingAfter ?: "?"}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    battle.notes?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                                    TextButton(onClick = { scope.launch { repository.deleteBattleRecord(battle.id) } }) { Text("Apagar registo") }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text("My Meta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Analisa apenas o teu Battle Log local. Não altera o Meta Pack global e não faz afirmações causais sobre mudanças de equipa.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(League.GREAT, League.ULTRA, League.MASTER).forEach { option ->
                            FilterChip(selected = metaLeague == option, onClick = { metaLeague = option }, label = { Text(option.name) })
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${snapshot.summary.total} batalhas · ${snapshot.summary.wins}W ${snapshot.summary.losses}L ${snapshot.summary.draws}D", fontWeight = FontWeight.Bold)
                            Text("Win rate observada ${"%.1f".format(snapshot.summary.winRate.proportion * 100)}%", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "IC 95% ${"%.1f".format(snapshot.summary.winRate.lower * 100)}–${"%.1f".format(snapshot.summary.winRate.upper * 100)}% · confiança ${snapshot.summary.winRate.confidence}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("Taxa observada ≠ probabilidade futura. Amostras pequenas ficam explicitamente marcadas.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (snapshot.signals.isNotEmpty()) {
                    item { Text("Sinais", fontWeight = FontWeight.Bold) }
                    items(snapshot.signals, key = { "signal:${it.type}:${it.subjectId}" }) { signal ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${signal.type} · n=${signal.evidenceCount}", fontWeight = FontWeight.SemiBold)
                                Text(signal.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (snapshot.opponentLeads.isNotEmpty()) {
                    item { Text("Leads adversários", fontWeight = FontWeight.Bold) }
                    items(snapshot.opponentLeads.take(15), key = { "lead:${it.speciesId}" }) { lead ->
                        val name = speciesById[lead.speciesId]?.name ?: lead.speciesId
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name)
                                Text("${lead.encounters} encontros · ${"%.0f".format(lead.winRate.proportion * 100)}% W", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (snapshot.teams.isNotEmpty()) {
                    item { Text("Equipas observadas", fontWeight = FontWeight.Bold) }
                    items(snapshot.teams.take(12), key = { "teamperf:${it.teamKey}" }) { teamStats ->
                        val teamName = savedTeams.firstOrNull { it.id == teamStats.teamKey }?.name ?: teamStats.teamKey
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(teamName, fontWeight = FontWeight.SemiBold)
                                Text("${teamStats.battles} batalhas · ${teamStats.wins}W ${teamStats.losses}L ${teamStats.draws}D", style = MaterialTheme.typography.bodySmall)
                                Text("Observado ${"%.1f".format(teamStats.winRate.proportion * 100)}% · ${teamStats.winRate.confidence}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (snapshot.sessions.isNotEmpty()) {
                    item {
                        Text("Sessões", fontWeight = FontWeight.Bold)
                        Text("Uma nova sessão começa após >90 min sem batalhas.", style = MaterialTheme.typography.labelSmall)
                    }
                    items(snapshot.sessions.takeLast(10).reversed(), key = { "session:${it.sessionIndex}:${it.startedAtEpochMs}" }) { session ->
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Sessão ${session.sessionIndex} · ${session.battles} batalhas", fontWeight = FontWeight.SemiBold)
                                Text("${session.wins}W ${session.losses}L ${session.draws}D · ${formatBattleDate(session.startedAtEpochMs)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (snapshot.summary.total == 0) {
                    item { Text("Ainda não há batalhas nesta liga. Regista algumas no separador Registar.", color = MaterialTheme.colorScheme.tertiary) }
                }
            }
        }
    }
}

@Composable
private fun BattleOpponentPicker(
    label: String,
    catalog: List<PokemonSpecies>,
    selected: PokemonSpecies?,
    onSelected: (PokemonSpecies?) -> Unit
) {
    var query by remember(label, selected?.speciesId) { mutableStateOf(selected?.name.orEmpty()) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (selected != null && !it.equals(selected.name, ignoreCase = true)) onSelected(null)
            },
            label = { Text(label) },
            placeholder = { Text("Pokémon ou deixar UNKNOWN") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (selected != null) TextButton(onClick = { onSelected(null); query = "" }) { Text("×") }
            }
        )
        if (selected == null && query.length >= 2) {
            PokemonSearch.find(catalog, query, 5).forEach { candidate ->
                Surface(
                    Modifier.fillMaxWidth().clickable { onSelected(candidate); query = candidate.name },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(candidate.name, modifier = Modifier.padding(9.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatBattleDate(epochMs: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun IntegrationStatusCard(title: String, detail: String, status: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatCompactLevel(level: Double): String = if (level % 1.0 == 0.0) level.toInt().toString() else "%.1f".format(level)
private fun formatConfidence(value: Double): String = "%.0f%%".format(value * 100.0)
