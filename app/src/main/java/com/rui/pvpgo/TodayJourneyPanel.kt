package com.rui.pvpgo

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.ui.theme.PvpColors
import kotlinx.coroutines.flow.map

/** Saved locally, separately for each objective. No account or background tracking. */
@Composable
fun TodayJourneyPanel(
    goal: TodayGoal,
    onOpenCollection: () -> Unit,
    onContinueGoal: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) {
        context.getSharedPreferences("companion_today_journey_v1", Context.MODE_PRIVATE)
    }
    val key = "stage_" + goal.name
    var stageName by rememberSaveable(goal.name) {
        mutableStateOf(prefs.getString(key, TodayJourneyStage.COPY_FILTER.name)
            ?: TodayJourneyStage.COPY_FILTER.name)
    }
    val stage = TodayJourneyStage.fromStored(stageName)
    val scopeKey = "scope_" + goal.name
    var scopeName by rememberSaveable(goal.name) {
        mutableStateOf(prefs.getString(scopeKey, GoFilterScope.STANDARD.name)
            ?: GoFilterScope.STANDARD.name)
    }
    val scope = GoFilterScope.fromStored(scopeName)
    val suggestion = remember(goal, scope) { TodayJourneyPolicy.scopedSuggestion(goal, scope) }
    val clipboard = LocalClipboardManager.current
    val repository = remember(context) { CollectionRepository.get(context) }
    val inboxCountFlow = remember(repository) { repository.pendingInbox.map { it.size } }
    // -1 is only a loading sentinel: zero must come from the first Room emission.
    val pendingCount by inboxCountFlow.collectAsState(initial = -1)
    val inboxSummary = TodayJourneyPolicy.inboxStatus(pendingCount.takeIf { it >= 0 })

    fun updateStage(next: TodayJourneyStage) {
        stageName = next.name
        prefs.edit().putString(key, next.name).apply()
    }
    fun updateScope(next: GoFilterScope) {
        scopeName = next.name
        prefs.edit().putString(scopeKey, next.name).apply()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PvpColors.TodayCard,
        border = BorderStroke(1.dp, PvpColors.BorderDefault)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "JORNADA · FASE ${stage.index} DE 3",
                color = PvpColors.AccentSky,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                when (stage) {
                    TodayJourneyStage.COPY_FILTER -> "1 · Encontrar candidatos"
                    TodayJourneyStage.REVIEW_CANDIDATES -> "2 · Rever capturas"
                    TodayJourneyStage.NEXT_ACTION -> "3 · Preparar a próxima ação"
                },
                color = PvpColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            when (stage) {
                TodayJourneyStage.COPY_FILTER -> {
                    Text(
                        suggestion.purpose,
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PvpColors.SurfaceRaised,
                        border = BorderStroke(1.dp, PvpColors.BorderDefault)
                    ) {
                        SelectionContainer {
                            Text(
                                suggestion.filter,
                                modifier = Modifier.fillMaxWidth().padding(13.dp),
                                color = PvpColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Text(
                        if (suggestion.requiresEnglishGoTerms)
                            "Filtro com termos em inglês. Se o teu Pokémon GO estiver noutro idioma, confirma a sintaxe antes de usar."
                        else "Filtro por avaliação; os operadores numéricos não dependem de nomes de espécies.",
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        suggestion.limitation,
                        color = PvpColors.AccentAmber,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        when (scope) {
                            GoFilterScope.STANDARD -> "Pesquisa padrão · podes adaptar os critérios"
                            GoFilterScope.BROAD -> "Pesquisa mais ampla · poderá mostrar muitos resultados"
                            GoFilterScope.FOCUSED -> "Pesquisa restrita · poderá excluir bons exemplares"
                        },
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { updateScope(GoFilterScope.BROAD) },
                            enabled = TodayJourneyPolicy.canBroaden(scope),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Poucos ou zero") }
                        OutlinedButton(
                            onClick = { updateScope(GoFilterScope.FOCUSED) },
                            enabled = TodayJourneyPolicy.canRefine(goal, scope),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Demasiados") }
                    }
                    if (scope != GoFilterScope.STANDARD) {
                        OutlinedButton(
                            onClick = { updateScope(GoFilterScope.STANDARD) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Voltar ao filtro padrão") }
                    } else if (goal == TodayGoal.MASTER) {
                        Text(
                            "Master já começa por 4★; não existe refinamento genérico mais seguro. " +
                                "Podes ampliar para 3★ ou avaliar espécies individualmente.",
                            color = PvpColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(suggestion.filter))
                            android.widget.Toast.makeText(
                                context, "Filtro copiado para a área de transferência",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            updateStage(TodayJourneyPolicy.afterCopy(stage))
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Copiar filtro e continuar")
                    }
                }
                TodayJourneyStage.REVIEW_CANDIDATES -> {
                    Text(
                        "Cola o filtro na pesquisa da coleção do Pokémon GO. " +
                            "Confere os resultados no jogo, depois abre a Coleção da Companion. " +
                            "Uma pesquisa copiada não importa exemplares automaticamente.",
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        inboxSummary,
                        color = PvpColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "A Inbox local e os resultados da pesquisa no Pokémon GO são distintos. " +
                            "Assinalar a fase não confirma nem importa candidatos.",
                        color = PvpColors.AccentAmber,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = onOpenCollection,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(
                            if (TodayJourneyPolicy.hasPendingInboxReview(pendingCount.takeIf { it >= 0 }))
                                "Abrir Inbox (" + pendingCount + " pendente(s))"
                            else "Abrir Inbox da Companion"
                        )
                    }
                    OutlinedButton(
                        onClick = { updateStage(TodayJourneyPolicy.afterUserReview(stage)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Já revi os candidatos") }
                    OutlinedButton(
                        onClick = { updateStage(TodayJourneyPolicy.reset()) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Voltar ao filtro") }
                }
                TodayJourneyStage.NEXT_ACTION -> {
                    Text(
                        "Assinalaste a revisão como concluída. Isto não comprova IVs, " +
                            "elegibilidade PvP ou entrada de Pokémon na coleção.",
                        color = PvpColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (TodayJourneyPolicy.hasPendingInboxReview(pendingCount.takeIf { it >= 0 })) {
                        Text(
                            inboxSummary + " A revisão da fase não alterou esses registos.",
                            color = PvpColors.AccentAmber,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = onOpenCollection,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Rever pendentes na Inbox") }
                    }
                    Button(
                        onClick = onContinueGoal,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(
                            when (goal) {
                                TodayGoal.GREAT, TodayGoal.ULTRA, TodayGoal.MASTER ->
                                    "Abrir Equipas e confirmar a liga"
                                TodayGoal.EVENTS -> "Ver Radar de eventos"
                                else -> "Rever a Coleção"
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { updateStage(TodayJourneyPolicy.reset()) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Recomeçar jornada") }
                }
            }
        }
    }
}
