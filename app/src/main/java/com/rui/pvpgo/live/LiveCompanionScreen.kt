package com.rui.pvpgo.live

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rui.pvpgo.ui.theme.PvpColors
import com.rui.pvpgo.CollectionRepository
import com.rui.pvpgo.PokemonArtwork
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.SavedTeam
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CO01–CO05 GOLDEN visual structure. This is a Capture/Manual HUD, NOT a working
 * battle scene recognizer. Screenshots of tactical advice in Figma are examples,
 * not trusted observations; never render them as live suggestions here.
 */
@Composable
fun LiveCompanionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { LiveObservationRepository.get(context) }
    val collectionRepo = remember(context) { CollectionRepository.get(context) }
    var savedTeams by remember { mutableStateOf<List<SavedTeam>>(emptyList()) }
    var ownedPokemon by remember { mutableStateOf<List<OwnedPokemon>>(emptyList()) }
    var teamSelection by remember { mutableStateOf(CompanionTeamSelection()) }
    var manualOpponent by remember { mutableStateOf(CompanionManualOpponent()) }
    var opponentDraft by remember { mutableStateOf("") }
    var runtime by remember { mutableStateOf(MediaProjectionFrameBus.state.value) }
    var recentSessions by remember { mutableStateOf<List<CaptureSessionEntity>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(CompanionQuickLog()) }
    var qualityClock by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { MediaProjectionFrameBus.state.collectLatest { runtime = it } }
    LaunchedEffect(repository) { repository.recentSessions.collectLatest { recentSessions = it } }
    LaunchedEffect(collectionRepo) { collectionRepo.savedTeams.collectLatest { savedTeams = it } }
    LaunchedEffect(collectionRepo) { collectionRepo.collection.collectLatest { ownedPokemon = it } }
    LaunchedEffect(runtime.sessionId) {
        manual = manual.forSession(runtime.sessionId)
        teamSelection = teamSelection.forSession(runtime.sessionId)
        manualOpponent = manualOpponent.forSession(runtime.sessionId)
        opponentDraft = ""
    }
    LaunchedEffect(runtime.status, runtime.sessionId) {
        while (runtime.status == CaptureRuntimeStatus.CAPTURING) {
            qualityClock = System.currentTimeMillis()
            delay(1000)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            message = null
            MediaProjectionCaptureService.start(context, result.resultCode, result.data!!)
        } else {
            message = "O Android não autorizou esta sessão. Nada foi capturado."
        }
    }
    val capture = CompanionCapturePolicy.present(runtime.status.name, runtime.frameCount, runtime.error)
    val signal = FrameQualityReadiness.assess(runtime.frameQuality, qualityClock,
        runtime.status == CaptureRuntimeStatus.CAPTURING)
    val options = remember(savedTeams, ownedPokemon) { CompanionManualTeamPolicy.options(savedTeams, ownedPokemon) }
    // A Room deletion or an edited team clears obsolete manual IDs, never resurrecting old choices.
    LaunchedEffect(options) { teamSelection = teamSelection.validated(options) }
    val verifiedSelection = teamSelection.validated(options)
    val chosenTeam = options.firstOrNull { it.id == verifiedSelection.selectedTeamId }
    val active = chosenTeam?.members?.firstOrNull { it.ownedPokemonId == verifiedSelection.activeOwnedPokemonId }
    val notesEnabled = runtime.status == CaptureRuntimeStatus.CAPTURING
    val accent = if (capture.attention) PvpColors.StateWarning else PvpColors.BrandBlue

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("BATALHA AO VIVO", color = PvpColors.BrandYellow,
                        style = MaterialTheme.typography.labelMedium)
                    Text("Live Companion", color = PvpColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Observação experimental · não automatizada", color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Fechar", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            CompanionPanel(border = accent) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("ESTADO DA CAPTURA", color = accent,
                        style = MaterialTheme.typography.labelMedium)
                    Text(runtime.status.name, color = accent, style = MaterialTheme.typography.labelSmall)
                }
                Text(capture.title, color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                Text(capture.detail, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                if (capture.attention) {
                    Text("Não uses esta telemetria para tomar decisões táticas automáticas.",
                        color = PvpColors.StateWarning, style = MaterialTheme.typography.bodySmall)
                }
                if (capture.canStart) {
                    Button(
                        onClick = {
                            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            launcher.launch(manager.createScreenCaptureIntent())
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Autorizar e iniciar captura") }
                }
                if (capture.canStop) {
                    OutlinedButton(
                        onClick = { MediaProjectionCaptureService.stop(context) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Parar captura") }
                }
                message?.let { Text(it, color = PvpColors.StateWarning, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompanionMetric(Modifier.weight(1f), "TU", active?.displayName ?: "Não identificado",
                    if (active == null) "HP e equipa desconhecidos" else "Manual · HP desconhecido")
                CompanionMetric(Modifier.weight(1f), "RIVAL", manualOpponent.enteredName ?: "Não identificado",
                    if (manualOpponent.enteredName == null) "Espécie/HP desconhecidos" else "Manual · espécie não verificada")
            }
        }
        item {
            CompanionPanel(border = if (signal.transportStable) PvpColors.BrandBlue else PvpColors.BorderDefault) {
                Text("QUALIDADE DA CAPTURA · NÃO É DETEÇÃO DE BATALHA",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(signal.title, color = PvpColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium)
                Text(signal.detail, color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
                Text("Frames consecutivos: ${runtime.frameQuality.stableFrameStreak}/12 · " +
                    "anomalias: ${runtime.frameQuality.anomalyCount}",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            CompanionPanel(border = PvpColors.BrandBlue) {
                Text("AGORA · SEM ANÁLISE TÁTICA", color = PvpColors.BrandBlue,
                    style = MaterialTheme.typography.labelMedium)
                Text("À espera de dados fiáveis", style = MaterialTheme.typography.titleLarge,
                    color = PvpColors.TextPrimary)
                Text("A captura mede frames, rotação e desempenho. Ainda não reconhece trocas, energia, shields ou Pokémon. Ações rápidas abaixo são apontamentos manuais, nunca recomendações do motor.",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompanionTag("Energia: —")
                    CompanionTag("Shields: sem leitura")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("REGISTO RÁPIDO", color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium)
                Text("MANUAL · temporário", color = PvpColors.BrandBlue,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompanionQuickButton("SHIELD", "rival usou", notesEnabled, Modifier.weight(1f)) {
                        manual = manual.append(ManualCompanionEvent.OPPONENT_SHIELD, System.currentTimeMillis())
                    }
                    CompanionQuickButton("SWAP", "rival trocou", notesEnabled, Modifier.weight(1f)) {
                        manual = manual.append(ManualCompanionEvent.OPPONENT_SWAP, System.currentTimeMillis())
                    }
                    CompanionQuickButton("CARG.", "rival atacou", notesEnabled, Modifier.weight(1f)) {
                        manual = manual.append(ManualCompanionEvent.OPPONENT_CHARGED, System.currentTimeMillis())
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompanionQuickButton("EU SH.", "usei shield", notesEnabled, Modifier.weight(1f)) {
                        manual = manual.append(ManualCompanionEvent.OWN_SHIELD, System.currentTimeMillis())
                    }
                    CompanionQuickButton("KO", "rival caiu", notesEnabled, Modifier.weight(1f)) {
                        manual = manual.append(ManualCompanionEvent.OPPONENT_KO, System.currentTimeMillis())
                    }
                    CompanionQuickButton("CORR.", "desfazer último", manual.notes.isNotEmpty(), Modifier.weight(1f)) {
                        manual = manual.undo()
                    }
                }
            }
        }
        item {
            CompanionPanel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("APONTAMENTOS MANUAIS", style = MaterialTheme.typography.labelMedium,
                        color = PvpColors.TextSecondary)
                    if (manual.notes.isNotEmpty()) {
                        TextButton(onClick = { manual = manual.clear() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Limpar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text(
                    "Shields anotados · rival ${manual.opponentShieldsNoted}/2 · tu ${manual.ownShieldsNoted}/2",
                    color = PvpColors.TextPrimary, style = MaterialTheme.typography.bodyMedium
                )
                Text("KO anotados: ${manual.opponentKoNoted}/3 · observações: ${manual.notes.size}",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                manual.notes.takeLast(3).asReversed().forEach { note ->
                    Text("• ${note.kind.label} · ${formatCaptureTime(note.atEpochMs)}",
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text("Estas notas só existem nesta sessão no ecrã. Não entram no Battle Engine, não identificam espécies e não criam um BattleRecord.",
                    color = PvpColors.StateWarning, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text("RIVAL · NOTA MANUAL", color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium)
            CompanionPanel {
                OutlinedTextField(
                    value = opponentDraft,
                    onValueChange = { opponentDraft = it.take(48) },
                    label = { Text("Nome do rival (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { manualOpponent = manualOpponent.enter(opponentDraft) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Indicar rival") }
                    OutlinedButton(onClick = { manualOpponent = manualOpponent.clear(); opponentDraft = "" },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Limpar") }
                }
                Text("Nome apenas indicado por ti; não existe identificação visual, verificação de espécie, HP, energia ou movimentos.",
                    color = PvpColors.StateWarning, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text("TUA EQUIPA · CONFIGURAÇÃO MANUAL", color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium)
            CompanionPanel {
                Text("Equipa introduzida por ti — não detetada", color = PvpColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium)
                if (options.isEmpty()) {
                    Text("Ainda não tens equipas guardadas. Cria uma em Equipas e volta aqui.",
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Escolhe uma equipa guardada", color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium)
                    options.take(12).forEach { team ->
                        OutlinedButton(
                            enabled = team.selectable,
                            onClick = { teamSelection = teamSelection.chooseTeam(team.id, options) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text((if (team.id == chosenTeam?.id) "✓  " else "") +
                                "${team.name} · ${team.leagueName}",
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        team.disabledReason?.let { reason ->
                            Text("${team.name}: $reason", color = PvpColors.StateWarning,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (chosenTeam != null) {
                        Text("POKÉMON ATIVO · escolha manual", color = PvpColors.BrandBlue,
                            style = MaterialTheme.typography.labelMedium)
                        chosenTeam.members.forEach { member ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PokemonArtwork(member.speciesId, Modifier.heightIn(min = 44.dp).weight(0.20f))
                                FilterChip(
                                    modifier = Modifier.weight(0.80f).heightIn(min = 48.dp),
                                    selected = active?.ownedPokemonId == member.ownedPokemonId,
                                    onClick = { teamSelection = teamSelection.chooseActive(member.ownedPokemonId, options) },
                                    label = { Text("${member.displayName} · ${member.role.name}", maxLines = 2) }
                                )
                            }
                        }
                        Text(if (active == null) "Pokémon ativo não indicado."
                            else "Indicado manualmente: ${active.displayName}. HP, energia e shields continuam desconhecidos.",
                            style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
                        TextButton(onClick = { teamSelection = teamSelection.chooseTeam(null, options) },
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Limpar seleção") }
                    } else {
                        Text("Não há seleção ativa. A captura nunca identifica automaticamente a tua equipa.",
                            color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Estes dados não entram no Battle Engine, não criam BattleRecord e não sobrevivem ao fecho deste ecrã.",
                    color = PvpColors.StateWarning, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            CompanionPanel {
                Text("DIAGNÓSTICO DO DISPOSITIVO", color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium)
                Text("${runtime.frameCount} frames · ${formatObservedFps(runtime.averageFps)}",
                    color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text("Maior intervalo ${runtime.maxFrameGapMs} ms · ${runtime.rotationChanges} mudanças de rotação",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("Bateria ${runtime.batteryPercent?.let { "$it%" } ?: "—"} · estado térmico ${thermalStatusLabel(runtime.thermalStatus)}",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                runtime.lastFrame?.let { last ->
                    Text("Último frame ${last.width}×${last.height} · ${last.rotationDegrees}° · ${formatCaptureTime(last.capturedAtEpochMs)}",
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            CompanionPanel {
                Text("PRIVACIDADE / ESTADO TÉCNICO", color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium)
                Text("MediaProjection pede autorização por sessão. Os frames são descartados após leitura; staging.db contém apenas metadados e checkpoints de captura.",
                    color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("Pendente: identificação de espécies, animações/ataques, energia, decisões 3v3 e overlay. Precisa de corpus e testes num Android real.",
                    color = PvpColors.StateWarning, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (recentSessions.isNotEmpty()) {
            item { Text("SESSÕES RECENTES", color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium) }
            items(recentSessions.take(5), key = { "session:${it.id}" }) { session ->
                CompanionPanel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(session.status, color = PvpColors.TextPrimary,
                            style = MaterialTheme.typography.labelMedium)
                        Text(formatCaptureTime(session.startedAtEpochMs), color = PvpColors.TextSecondary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${session.frameCount} frames · ${session.width?.let { "${it}×${session.height ?: 0}" } ?: "dimensões desconhecidas"}",
                        color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    session.stopReason?.let { Text(it, color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun CompanionPanel(
    border: Color = PvpColors.BorderDefault,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = PvpColors.SurfaceCard,
        border = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun CompanionMetric(modifier: Modifier, title: String, value: String, meta: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = PvpColors.SurfaceCard,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = PvpColors.BrandBlue, style = MaterialTheme.typography.labelMedium)
            Text(value, color = PvpColors.TextPrimary, style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompanionTag(label: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = PvpColors.SurfaceInput,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = PvpColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CompanionQuickButton(
    title: String, detail: String, enabled: Boolean, modifier: Modifier, action: () -> Unit
) {
    OutlinedButton(
        onClick = action, enabled = enabled,
        modifier = modifier.heightIn(min = 66.dp),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatCaptureTime(epochMs: Long): String =
    SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(epochMs))

private fun formatObservedFps(value: Double?): String =
    value?.let { String.format(Locale.US, "%.1f fps", it) } ?: "fps não disponível"

private fun thermalStatusLabel(status: Int?): String = when (status) {
    null -> "n/d"
    0 -> "normal"
    1 -> "ligeiro"
    2 -> "moderado"
    3 -> "elevado"
    4 -> "crítico"
    5 -> "emergência"
    6 -> "desligamento"
    else -> "#$status"
}
