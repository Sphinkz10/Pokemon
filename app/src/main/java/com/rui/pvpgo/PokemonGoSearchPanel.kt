package com.rui.pvpgo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.ui.theme.PvpColors

/**
 * Companion to the existing one-and-only collection guide.
 * It copies a query to the Android clipboard; it never imports, transfers,
 * evaluates a true IV rank or edits the player's Pokémon GO account.
 */
@Composable
internal fun PokemonGoSearchPanel(league: League) {
    val context = LocalContext.current
    var preset by rememberSaveable(league) {
        mutableStateOf(PokemonGoSearchPolicy.suggestedFor(league))
    }
    var language by rememberSaveable { mutableStateOf(PokemonGoQueryLanguage.EN) }
    var lastCopied by remember { mutableStateOf<String?>(null) }
    val query = PokemonGoSearchPolicy.build(preset, language)

    Column(
        modifier = Modifier.fillMaxWidth().testTag("go-search-panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "PESQUISAR CANDIDATOS NO POKÉMON GO",
            color = PvpColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "1 · Escolhe um perfil. 2 · Copia a pesquisa. " +
                "3 · Abre Pokémon GO → Pokémon → Lupa e cola. " +
                "4 · Confirma a avaliação e volta aqui para comparar IVs por espécie e liga.",
            color = PvpColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PokemonGoSearchPreset.entries.forEach { option ->
                FilterChip(
                    selected = option == preset,
                    onClick = { preset = option; lastCopied = null },
                    label = { Text(option.label) },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("go-search-preset-${option.name.lowercase()}")
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PokemonGoQueryLanguage.entries.forEach { option ->
                FilterChip(
                    selected = option == language,
                    onClick = { language = option; lastCopied = null },
                    label = { Text(option.title) },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("go-search-language-${option.name.lowercase()}")
                )
            }
        }
        Text(preset.explanation, color = PvpColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall)
        SelectionContainer {
            Text(
                text = query,
                modifier = Modifier.fillMaxWidth().testTag("go-search-query"),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = PvpColors.AccentSky,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as? ClipboardManager
                val safe = PokemonGoSearchPolicy.isNonDestructive(query)
                if (clipboard != null && safe) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Pesquisa Pokémon GO", query))
                    lastCopied = query
                } else {
                    lastCopied = null
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("go-search-copy"),
            enabled = PokemonGoSearchPolicy.isNonDestructive(query)
        ) { Text("Copiar pesquisa para Pokémon GO") }
        if (lastCopied == query) {
            Text(
                "Copiado. Cola na pesquisa da tua coleção no Pokémon GO.",
                modifier = Modifier.testTag("go-search-copied"),
                color = PvpColors.AccentGreen,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            "ATENÇÃO: são grupos aproximados de IV, não IVs exatos, rank PvP, " +
                "evolução ou desempenho em combate. Alguns excelentes candidatos " +
                "ficam fora destes filtros. Não transfiras Pokémon por este resultado. " +
                "Se o idioma do jogo for diferente, experimenta EN ou PT.",
            color = PvpColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("go-search-safety")
        )
    }
}
