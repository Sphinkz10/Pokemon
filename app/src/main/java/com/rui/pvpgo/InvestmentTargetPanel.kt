package com.rui.pvpgo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.domain.InvestmentTarget
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies

/**
 * A concrete, interactive step towards a real cost estimate.
 * Uses only confirmed, persisted specimen data; edits in the Build real form
 * must be saved first. No pack/ledger is wired to this UI yet.
 */
@Composable
internal fun InvestmentTargetPanel(
    owned: OwnedPokemon,
    species: PokemonSpecies?,
    league: League,
    hasPendingBuildChanges: Boolean
) {
    val context = LocalContext.current
    val resourceStore = remember(context) { UserResourceStore.get(context) }
    // Costing stays blocked if catalog identity/family cannot be determined.
    val familyId = species?.takeIf { it.speciesId == owned.speciesId }
        ?.let { it.familyId?.takeIf(String::isNotBlank) ?: it.speciesId }
    var savedResources by remember(resourceStore, familyId) {
        mutableStateOf(familyId?.let(resourceStore::load) ?: UserResourceBalances())
    }
    val knownLedger = remember(savedResources, familyId) {
        familyId?.let { UserResourcePolicy.completeLedgerOrNull(savedResources, it) }
    }
    var targetText by rememberSaveable(owned.id, league.name) { mutableStateOf("") }
    val simulation = remember(owned, species, league, targetText) {
        InvestmentTargetSimulator.simulate(owned, species, league, targetText)
    }
    val target = simulation.targetLevel
        ?.takeIf { simulation.canRequestCosting }
        ?.let { level ->
            InvestmentTarget(
                ownedPokemonId = owned.id,
                league = league,
                targetSpeciesId = owned.speciesId,
                targetLevel = level
            )
        }
    // A verified cost pack is still missing; only explicit, complete saved
    // resources can cross into the engine ResourceLedger.
    val quote = remember(owned, target, simulation, knownLedger) {
        InvestmentCostQuoteGate.evaluate(
            owned = owned,
            simulation = simulation,
            catalog = listOfNotNull(species),
            movesById = emptyMap(),
            pack = null,
            ledger = knownLedger,
            target = target
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (familyId != null) {
                UserResourcePanel(
                    familyId = familyId,
                    familyLabel = species.name,
                    saved = savedResources,
                    store = resourceStore,
                    onSaved = { savedResources = it }
                )
            } else {
                Text(
                    "Os recursos por família só podem ser registados após confirmar a espécie e forma.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("Simular nível objetivo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Nível atual: ${owned.level?.toString() ?: "por confirmar"} · " +
                    "Liga: ${SpeciesLeaguePolicy.shortLabel(league)} · " +
                    "limite: ${SpeciesLeaguePolicy.cpLimitLabel(league)}",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = targetText,
                onValueChange = { value ->
                    if (value.length <= 5 && value.all { it.isDigit() || it == '.' || it == ',' }) {
                        targetText = value
                    }
                },
                label = { Text("Nível objetivo (1–50)") },
                placeholder = { Text("Ex.: 35,5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (hasPendingBuildChanges) {
                Text(
                    "Guarda primeiro as alterações do Build real. A projeção só usa os dados já guardados.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    simulation.message,
                    color = if (simulation.status in setOf(
                        TargetSimulationStatus.INVALID_CURRENT_DATA,
                        TargetSimulationStatus.INVALID_LEVEL,
                        TargetSimulationStatus.BASE_LEAGUE_BLOCKED
                    )) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (simulation.canRequestCosting) {
                    HorizontalDivider()
                    Text(
                        "Orçamento · ${quote.explanation}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                InvestmentTargetSimulator.SAFETY_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
