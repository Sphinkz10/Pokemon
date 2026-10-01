package com.rui.pvpgo.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.ui.theme.PvpColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun dateLabel(ms: Long): String = Instant.ofEpochMilli(ms)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale("pt", "PT")))

@Composable
private fun TitleBack(title: String, subtitle: String, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = onBack) { Text("‹ Voltar") }
        Text(title, style = MaterialTheme.typography.headlineLarge, color = PvpColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PvpColors.TextSecondary)
    }
}

@Composable
private fun DataProvenance(snapshot: CalendarSnapshot?, onRefresh: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PvpColors.SurfaceRaised,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                when {
                    snapshot == null -> "A obter calendário…"
                    snapshot.stale -> "ATENÇÃO · dados em cache/desatualizados"
                    snapshot.isAvailable -> "Atualizado em ${dateLabel(snapshot.fetchedAtMs!!)}"
                    else -> "Sem ligação à fonte"
                },
                color = if (snapshot?.stale == false) PvpColors.StateSuccess else PvpColors.StateWarning,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Leek Duck · fonte comunitária NÃO oficial. Os eventos podem mudar. Confirma sempre as informações na aplicação Pokémon GO.",
                style = MaterialTheme.typography.bodySmall,
                color = PvpColors.TextSecondary
            )
            if (snapshot?.error != null) {
                Text(snapshot.error, color = PvpColors.StateWarning, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRefresh) { Text("Atualizar agora ↻") }
        }
    }
}

@Composable
private fun EventRow(item: PokemonGoCalendarEvent, inProgress: Boolean) {
    val opener = LocalUriHandler.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { opener.openUri(item.articleUrl) },
        shape = RoundedCornerShape(14.dp),
        color = PvpColors.SurfaceCard,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                if (inProgress) "EM CURSO" else item.category.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelSmall,
                color = if (inProgress) PvpColors.StateSuccess else PvpColors.BrandBlue,
                fontWeight = FontWeight.Bold
            )
            Text(item.title, color = PvpColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                "${dateLabel(item.startMs)} — ${dateLabel(item.endMs)}",
                color = PvpColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Text("Ver origem ↗", color = PvpColors.BrandBlue, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Live category grouping; excludes all events with invalid dates and URLs. */
@Composable
fun CommunityEventScreen(
    calendar: CalendarSnapshot?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenRadar: () -> Unit
) {
    val active = calendar?.active().orEmpty().take(14)
    val upcoming = calendar?.upcoming().orEmpty().take(25)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TitleBack("Eventos", "Atuais e próximos · Portugal / hora local", onBack) }
        item { DataProvenance(calendar, onRefresh) }
        if (active.isNotEmpty()) {
            item { Text("EM CURSO (${active.size})", color = PvpColors.StateSuccess, fontWeight = FontWeight.Bold) }
            items(active, key = { it.articleUrl + ":" + it.startMs }) { event -> EventRow(event, true) }
        }
        if (upcoming.isNotEmpty()) {
            item { Text("PRÓXIMOS (${upcoming.size})", color = PvpColors.TextPrimary, fontWeight = FontWeight.Bold) }
            items(upcoming, key = { it.articleUrl + ":" + it.startMs }) { event -> EventRow(event, false) }
        }
        if (active.isEmpty() && upcoming.isEmpty()) item {
            Text(
                "Não existem eventos disponíveis no intervalo consultado. Usa Atualizar ou verifica a ligação à Internet.",
                color = PvpColors.TextSecondary
            )
        }
        item { Button(onClick = onOpenRadar) { Text("Abrir Radar PvP") } }
    }
}

@Composable
fun CommunityAgendaScreen(
    calendar: CalendarSnapshot?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenEvent: () -> Unit,
    onOpenRadar: () -> Unit
) {
    val now = System.currentTimeMillis()
    val entries = (calendar?.active(now).orEmpty() + calendar?.upcoming(now).orEmpty())
        .distinctBy { it.articleUrl + it.startMs }
        .take(32)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TitleBack("Agenda", "Eventos por ordem temporal · hora local", onBack) }
        item { DataProvenance(calendar, onRefresh) }
        if (entries.isEmpty()) item {
            Text("Nenhum evento sincronizado para apresentar.", color = PvpColors.TextSecondary)
        }
        items(entries, key = { it.articleUrl + ":" + it.startMs }) { event ->
            EventRow(event, event.startMs <= now && event.endMs > now)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenEvent) { Text("Todos os eventos") }
                TextButton(onClick = onOpenRadar) { Text("Radar PvP") }
            }
        }
    }
}
