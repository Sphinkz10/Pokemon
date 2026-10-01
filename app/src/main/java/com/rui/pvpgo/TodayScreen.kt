package com.rui.pvpgo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rui.pvpgo.events.CalendarSnapshot
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.ui.theme.PvpColors
import com.rui.pvpgo.ui.theme.PvpRadius
import com.rui.pvpgo.ui.theme.PvpSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TodayBlue: Color get() = PvpColors.AccentSky
private val TodayBlueDeep: Color get() = PvpColors.AccentDeep
private val TodayAmber: Color get() = PvpColors.AccentAmber
private val TodayGreen: Color get() = PvpColors.AccentGreen
private val TodayRed: Color get() = PvpColors.AccentRed
private val TodayCard: Color get() = PvpColors.TodayCard
private val TodayCardRaised: Color get() = PvpColors.TodayCardRaised
private val TodayBorder: Color get() = PvpColors.TodayBorder

@Composable
fun TodayScreen(
    calendar: CalendarSnapshot?,
    catalog: List<PokemonSpecies>,
    loading: Boolean,
    onSelectPokemon: (PokemonSpecies) -> Unit,
    onExploreEvent: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpenAgenda: () -> Unit,
    onSearch: () -> Unit
) {
    val byName = remember(catalog) {
        catalog.groupBy { it.name.lowercase(Locale.ROOT) }
    }
    fun species(name: String): PokemonSpecies? =
        byName[name.lowercase(Locale.ROOT)]?.firstOrNull()
            ?: catalog.firstOrNull { it.name.startsWith(name, ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TodayHeader(onSearch = onSearch) }
        item {
            EventHeroCard(
                calendar = calendar,
                loading = loading,
                onExplore = onExploreEvent
            )
        }
        item {
            TodayCatalogStatus(
                speciesCount = catalog.size,
                loading = loading,
                onOpenCollection = onSearch
            )
        }
        item {
            TodaySectionHeader(
                title = "ESPÉCIES DE REFERÊNCIA",
                action = "Ver radar ↗",
                onAction = onOpenRadar
            )
        }
        item {
            CarbinkPriorityCard(
                enabled = species("Carbink") != null,
                onClick = { species("Carbink")?.let(onSelectPokemon) }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniPriorityCard(
                    modifier = Modifier.weight(1f),
                    species = "Vulpix",
                    reason = "Analisar IV · PvP",
                    action = "VER PERFIL ↗",
                    actionColor = TodayAmber,
                    art = ArtKind.VULPIX,
                    enabled = species("Vulpix") != null,
                    onClick = { species("Vulpix")?.let(onSelectPokemon) }
                )
                MiniPriorityCard(
                    modifier = Modifier.weight(1f),
                    species = "Paras",
                    reason = "Consultar espécie",
                    action = "VER PERFIL ↗",
                    actionColor = TodayGreen,
                    art = ArtKind.PARAS,
                    enabled = species("Paras") != null,
                    onClick = { species("Paras")?.let(onSelectPokemon) }
                )
            }
        }
        item {
            TodaySectionHeader(
                title = "AGENDA",
                action = "Agenda ↗",
                onAction = onOpenAgenda
            )
        }
        item { TodayTimelineCard(calendar) }
    }
}

@Composable
private fun TodayHeader(onSearch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Hoje",
                style = MaterialTheme.typography.headlineLarge,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PvpColors.TextPrimary
            )
            Text(
                text = todayDateLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = PvpColors.TextSecondary
            )
        }
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onSearch),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0B1728),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263B57))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("⌕", color = PvpColors.TextSecondary, fontSize = 27.sp)
            }
        }
    }
}

@Composable
private fun EventHeroCard(
    calendar: CalendarSnapshot?,
    loading: Boolean,
    onExplore: () -> Unit,
    actionLabel: String = "VER AGENDA"
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(218.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF173652), Color(0xFF0C1C30), Color(0xFF0B1726))
                )
            )
            .border(1.dp, TodayBorder, shape)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color(0xFF315A73).copy(alpha = 0.36f))
        )
        PokemonArtwork(
            speciesName = "Carbink",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp, top = 12.dp)
                .size(118.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "●  AGENDA NÃO SINCRONIZADA",
                    color = TodayAmber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (loading) "A carregar" else "Sem feed",
                    color = PvpColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Column(Modifier.width(210.dp)) {
                Text(
                    text = "Eventos\nPokémon GO",
                    color = PvpColors.TextPrimary,
                    fontSize = 28.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Horários ainda por confirmar",
                    color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallBonus("Bónus: —", TodayAmber)
                SmallBonus("Shiny: —", Color(0xFF99D9D3))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .clickable(onClick = onExplore),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = actionLabel,
                    color = TodayBlue,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("↗", color = TodayBlue, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun SmallBonus(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2A465B).copy(alpha = 0.78f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TodaySectionHeader(
    title: String,
    action: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = PvpColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = action,
            color = TodayBlue,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onAction)
                .padding(vertical = 13.dp)
        )
    }
}

@Composable
private fun CarbinkPriorityCard(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF15314A), Color(0xFF0E2236))))
            .border(1.dp, Color(0xFF4AA8D5), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PokemonArtwork("Carbink", Modifier.size(86.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "EXEMPLO DE IV  ·  PvP",
                color = TodayBlue,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "Carbink",
                color = PvpColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Consultar IV · Great League",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF23546D)
            ) {
                Text(
                    "VER POKÉMON ↗",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color(0xFF9DE7FF),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MiniPriorityCard(
    modifier: Modifier,
    species: String,
    reason: String,
    action: String,
    actionColor: Color,
    art: ArtKind,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(108.dp)
            .clip(shape)
            .background(TodayCard)
            .border(1.dp, Color(0xFF263C55), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(11.dp)
    ) {
        PokemonArtwork(
            speciesName = species,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(70.dp)
        )
        Column(Modifier.fillMaxSize()) {
            Text(
                species,
                color = PvpColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                reason,
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = actionColor.copy(alpha = 0.16f)
            ) {
                Text(
                    action,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = actionColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TodayCatalogStatus(speciesCount: Int, loading: Boolean, onOpenCollection: () -> Unit) {
    Surface(
        color = TodayCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263C55))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("POKÉDEX NACIONAL", color = TodayBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (loading) "A carregar espécies…" else "$speciesCount espécies disponíveis",
                    color = PvpColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Contagem do catálogo carregado, não da tua coleção.",
                    color = PvpColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box(
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onOpenCollection),
                contentAlignment = Alignment.Center
            ) {
                Text("Abrir ›", color = TodayBlue, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TodayTimelineCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TodayCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263C55))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Sem calendário oficial sincronizado",
                color = PvpColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Não apresentamos horas, eventos ou bónus como ativos sem uma fonte verificada. Consulta a agenda para saber o estado da integração.",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TimelineRow(time: String, title: String, detail: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = TodayCardRaised
        ) {
            Text(
                time,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("•", color = accent, fontSize = 17.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = PvpColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                detail,
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private enum class ArtKind { CARBINK, VULPIX, PARAS }

@Composable
private fun PokemonArt(kind: ArtKind, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (kind) {
            ArtKind.CARBINK -> drawCarbink()
            ArtKind.VULPIX -> drawVulpix()
            ArtKind.PARAS -> drawParas()
        }
    }
}

private fun DrawScope.drawCarbink() {
    val w = size.width
    val h = size.height
    drawCircle(Color(0xFF24465E), radius = w * 0.45f, center = Offset(w * 0.52f, h * 0.56f))
    fun crystal(cx: Float, cy: Float, s: Float, color: Color) {
        val p = Path().apply {
            moveTo(cx, cy - s)
            lineTo(cx + s * 0.65f, cy + s * 0.2f)
            lineTo(cx, cy + s)
            lineTo(cx - s * 0.65f, cy + s * 0.2f)
            close()
        }
        drawPath(p, color)
    }
    crystal(w * 0.34f, h * 0.27f, w * 0.17f, Color(0xFF66D7FF))
    crystal(w * 0.52f, h * 0.24f, w * 0.15f, Color(0xFFB2F3FF))
    crystal(w * 0.70f, h * 0.31f, w * 0.17f, Color(0xFF3DB7E8))
    val body = Path().apply {
        moveTo(w * 0.24f, h * 0.50f)
        lineTo(w * 0.39f, h * 0.35f)
        lineTo(w * 0.67f, h * 0.38f)
        lineTo(w * 0.82f, h * 0.55f)
        lineTo(w * 0.70f, h * 0.80f)
        lineTo(w * 0.39f, h * 0.82f)
        lineTo(w * 0.20f, h * 0.65f)
        close()
    }
    drawPath(body, Color(0xFF91ABC5))
    drawCircle(Color(0xFFE9F6FF), w * 0.24f, Offset(w * 0.52f, h * 0.59f))
    crystal(w * 0.53f, h * 0.59f, w * 0.12f, Color(0xFF19BCE7))
    drawCircle(Color(0xFF20344C), w * 0.025f, Offset(w * 0.43f, h * 0.55f))
    drawCircle(Color(0xFF20344C), w * 0.025f, Offset(w * 0.63f, h * 0.55f))
}

private fun DrawScope.drawVulpix() {
    val w = size.width
    val h = size.height
    drawCircle(Color(0xFF243B4A), radius = w * 0.43f, center = Offset(w * 0.58f, h * 0.60f))
    val leftEar = Path().apply {
        moveTo(w * 0.39f, h * 0.30f); lineTo(w * 0.43f, h * 0.04f); lineTo(w * 0.57f, h * 0.32f); close()
    }
    val rightEar = Path().apply {
        moveTo(w * 0.59f, h * 0.28f); lineTo(w * 0.71f, h * 0.07f); lineTo(w * 0.74f, h * 0.38f); close()
    }
    drawPath(leftEar, Color(0xFFE98B3A)); drawPath(rightEar, Color(0xFFE98B3A))
    drawCircle(Color(0xFFE98B3A), radius = w * 0.25f, center = Offset(w * 0.56f, h * 0.56f))
    drawCircle(Color(0xFFFFC56A), radius = w * 0.15f, center = Offset(w * 0.52f, h * 0.68f))
    drawCircle(Color(0xFF38261E), radius = w * 0.025f, center = Offset(w * 0.49f, h * 0.53f))
    drawCircle(Color(0xFF38261E), radius = w * 0.025f, center = Offset(w * 0.64f, h * 0.53f))
    drawCircle(Color(0xFFFFB14C), radius = w * 0.11f, center = Offset(w * 0.31f, h * 0.42f))
    drawCircle(Color(0xFFFFD477), radius = w * 0.08f, center = Offset(w * 0.29f, h * 0.34f))
}

private fun DrawScope.drawParas() {
    val w = size.width
    val h = size.height
    drawCircle(Color(0xFF263C4A), radius = w * 0.43f, center = Offset(w * 0.57f, h * 0.62f))
    drawCircle(Color(0xFFF05E4F), radius = w * 0.18f, center = Offset(w * 0.50f, h * 0.30f))
    drawCircle(Color(0xFFF05E4F), radius = w * 0.15f, center = Offset(w * 0.72f, h * 0.34f))
    drawCircle(Color.White, radius = w * 0.035f, center = Offset(w * 0.45f, h * 0.27f))
    drawCircle(Color.White, radius = w * 0.03f, center = Offset(w * 0.57f, h * 0.34f))
    drawCircle(Color.White, radius = w * 0.025f, center = Offset(w * 0.75f, h * 0.29f))
    drawCircle(Color(0xFFFFA24F), radius = w * 0.23f, center = Offset(w * 0.58f, h * 0.61f))
    drawCircle(Color(0xFF55301D), radius = w * 0.025f, center = Offset(w * 0.51f, h * 0.58f))
    drawCircle(Color(0xFF55301D), radius = w * 0.025f, center = Offset(w * 0.66f, h * 0.58f))
    drawRoundRect(Color(0xFFFFA24F), topLeft = Offset(w * 0.22f, h * 0.67f), size = Size(w * 0.23f, h * 0.08f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
    drawRoundRect(Color(0xFFFFA24F), topLeft = Offset(w * 0.69f, h * 0.67f), size = Size(w * 0.23f, h * 0.08f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
}

private fun todayDateLabel(): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("pt", "PT"))
    return LocalDate.now().format(formatter).lowercase(Locale("pt", "PT"))
}

@Composable
fun EventOverviewScreen(
    onBack: () -> Unit,
    onOpenRadar: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { DetailHeader(title = "Eventos", subtitle = "Estado real das fontes", onBack = onBack) }
        item {
            Surface(
                color = TodayCard,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TodayBorder)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SEM FONTE DE EVENTOS", color = TodayAmber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Eventos por confirmar", color = PvpColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "A aplicação ainda não está ligada a um calendário oficial verificado. Por segurança, não apresenta contagens decrescentes, bónus ou horários inventados.",
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item { TodaySectionHeader("EXPLORAR", "Abrir radar ↗", onOpenRadar) }
        item {
            Text(
                "No Radar podes consultar os teus targets. A informação só deve ser tratada como em tempo real quando a fonte tiver sido validada.",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun AgendaOverviewScreen(
    onBack: () -> Unit,
    onOpenEvent: () -> Unit,
    onOpenRadar: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { DetailHeader(title = "Agenda", subtitle = todayDateLabel(), onBack = onBack) }
        item { TodayTimelineCard() }
        item { TodaySectionHeader("FONTES", "Ver eventos ↗", onOpenEvent) }
        item {
            Text(
                "Não há eventos confirmados para apresentar. Só mostraremos datas quando a fonte de calendário estiver integrada e sincronizada.",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item { TodaySectionHeader("TARGETS", "Abrir radar ↗", onOpenRadar) }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(48.dp).clickable(onClick = onBack),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0B1728),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263B57))
        ) {
            Box(contentAlignment = Alignment.Center) { Text("‹", color = PvpColors.TextPrimary, fontSize = 30.sp) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PvpColors.TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EventInfoCard(modifier: Modifier, title: String, detail: String, accent: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = TodayCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263C55))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EventTargetRow(name: String, detail: String, art: ArtKind, accent: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TodayCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263C55))
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PokemonArtwork(name, Modifier.size(54.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(detail, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text("↗", color = accent, fontSize = 18.sp)
        }
    }
}

@Composable
private fun AgendaRow(
    time: String,
    title: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = TodayCardRaised) {
            Text(
                time,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PvpColors.TextPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, color = PvpColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = PvpColors.TextSecondary, fontSize = 20.sp)
    }
}

@Composable
fun PokemonArtwork(speciesName: String, modifier: Modifier = Modifier, shiny: Boolean = false, form: String? = null) {
    RemotePokemonArtwork(speciesName, modifier, shiny, form, fallback = {
        // While offline or while an alternate form is unresolved, never fabricate an official sprite.
        when {
            shiny || !PokemonArtworkSources.supportedBaseForm(form) -> GenericPokemonArtwork(speciesName, modifier)
            speciesName.equals("Carbink", ignoreCase = true) -> PokemonArt(ArtKind.CARBINK, modifier)
            speciesName.equals("Vulpix", ignoreCase = true) -> PokemonArt(ArtKind.VULPIX, modifier)
            speciesName.equals("Paras", ignoreCase = true) -> PokemonArt(ArtKind.PARAS, modifier)
            else -> GenericPokemonArtwork(speciesName, modifier)
        }
    })
}

@Composable
private fun GenericPokemonArtwork(speciesName: String, modifier: Modifier = Modifier) {
    val palette = listOf(
        Color(0xFF3E7191), Color(0xFF6D5D9B), Color(0xFF3A8A76),
        Color(0xFF9A6546), Color(0xFF536B9B), Color(0xFF8E5579)
    )
    val bg = palette[(speciesName.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg.copy(alpha = 0.32f))
            .border(1.dp, bg.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(29.dp)) {
            val middle = Offset(size.width / 2, size.height / 2)
            drawCircle(Color(0xFF9CC4D8), radius = size.minDimension * 0.45f)
            drawRect(Color(0xFF2A516B), topLeft = Offset(0f, size.height * 0.45f),
                size = Size(size.width, size.height * 0.10f))
            drawCircle(Color(0xFF2A516B), radius = size.minDimension * 0.20f, center = middle)
            drawCircle(Color(0xFFE5F3FF), radius = size.minDimension * 0.13f, center = middle)
        }
    }
}
