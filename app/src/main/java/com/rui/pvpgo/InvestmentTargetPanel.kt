package com.rui.pvpgo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rui.pvpgo.domain.InvestmentPlanStatus
import com.rui.pvpgo.domain.InvestmentTarget
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies

/**
 * A concrete, interactive step towards a real cost estimate.
 * Uses only confirmed, persisted specimen data; edits in the Build real form
 * must be saved first. Cost reference is community-reviewed and opt-in.
 */
@Composable
internal fun InvestmentTargetPanel(
    owned: OwnedPokemon,
    species: PokemonSpecies?,
    league: League,
    hasPendingBuildChanges: Boolean,
    resourceStoreOverride: UserResourceStore? = null
) {
    val context = LocalContext.current
    // Injectable store keeps Compose UI tests isolated from real user balances.
    val resourceStore = remember(context, resourceStoreOverride) {
        resourceStoreOverride ?: UserResourceStore.get(context)
    }
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
    // Default off: a dated community reference is not a live official price.
    var allowCommunityEstimate by rememberSaveable(owned.id) { mutableStateOf(false) }
    val reference = remember { CommunityNormalPowerUpPack.pack }
    val supported = remember(species, owned) {
        species != null &&
            CommunityNormalPowerUpPack.isSupportedNormalPokemon(species.speciesId, species.form) &&
            !species.isMega && !owned.isLucky && !owned.isShadow && !owned.isPurified
    }
    val referenceAvailable = remember { CommunityNormalPowerUpPack.authenticatedReference() }
    val suggestedCapTarget = remember(owned, species, league) {
        LeagueCapTargetPolicy.suggest(owned, species, league)
    }
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
    // Accept only the independently pinned, source-reviewed baseline pack,
    // and only when the user explicitly opts in on an eligible normal form.
    val selectedPack = reference.takeIf {
        allowCommunityEstimate && referenceAvailable && supported
    }
    val quote = remember(owned, target, simulation, knownLedger, selectedPack) {
        InvestmentCostQuoteGate.evaluate(
            owned = owned,
            simulation = simulation,
            catalog = listOfNotNull(species),
            movesById = emptyMap(),
            pack = selectedPack,
            ledger = knownLedger,
            target = target
        )
    }
    val affordable = remember(
        owned, species, league, hasPendingBuildChanges, allowCommunityEstimate,
        knownLedger, selectedPack, suggestedCapTarget
    ) {
        if (hasPendingBuildChanges) AffordableLevelResult(
            AffordableLevelStatus.NO_ELIGIBLE_TARGET,
            "Guarda primeiro as alterações ao Build real; os recursos não alteram dados por guardar."
        ) else AffordableLevelPolicy.advise(
            owned = owned, species = species, league = league,
            consented = allowCommunityEstimate,
            pack = selectedPack, ledger = knownLedger,
            suggestion = suggestedCapTarget
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
                    familyLabel = species?.name ?: familyId,
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
                modifier = Modifier.fillMaxWidth().testTag("investment-level-input")
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().testTag("cap-target-button"),
                enabled = !hasPendingBuildChanges && suggestedCapTarget.canApply,
                onClick = {
                    suggestedCapTarget.targetLevel?.let {
                        targetText = LeagueCapTargetPolicy.displayLevel(it)
                    }
                }
            ) {
                Text(
                    if (league == League.MASTER) "Usar nível 50 (sem limite de CP)"
                    else "Usar nível máximo para esta liga"
                )
            }
            Text(
                if (hasPendingBuildChanges)
                    "Guarda as alterações ao Build real antes de sugerir um nível."
                else suggestedCapTarget.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = if (suggestedCapTarget.canApply) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
            )
            Text(
                LeagueCapTargetPolicy.DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasPendingBuildChanges && suggestedCapTarget.canApply &&
                referenceAvailable && supported
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = allowCommunityEstimate,
                        modifier = Modifier.testTag("community-estimate-consent"),
                        onCheckedChange = { allowCommunityEstimate = it }
                    )
                    Text(
                        "Usar tabela comunitária revista em 15/10/2026 (não oficial)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider()
            Text(
                "Até onde chegam os meus recursos?",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                affordable.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = if (affordable.canApply) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasPendingBuildChanges && affordable.canApply) {
                val calculated = requireNotNull(affordable.affordablePlan)
                val calculatedLevel = requireNotNull(affordable.affordableLevel)
                Text(
                    "Nível alcançável: ${LeagueCapTargetPolicy.displayLevel(calculatedLevel)} · " +
                        "${affordable.affordableCp} CP · ${affordable.affordableSteps} incrementos",
                    modifier = Modifier.testTag("affordable-level-result"),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Custo acumulado estimado: ${calculated.costs.stardust} Stardust · " +
                        "${familyId?.let { calculated.costs.candyByFamily[it] } ?: 0} Candy · " +
                        "${familyId?.let { calculated.costs.xlCandyByFamily[it] } ?: 0} XL Candy.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (affordable.nextStepShortages.isNotEmpty()) {
                    Text(
                        "Para o próximo incremento faltam: " +
                            affordable.nextStepShortages.joinToString(" · ") {
                                "${it.amount ?: "?"} ${it.kind.name.replace('_', ' ')}"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (affordable.goalShortages.isNotEmpty()) {
                    Text(
                        "Até ao limite da liga faltam: " +
                            affordable.goalShortages.joinToString(" · ") {
                                "${it.amount ?: "?"} ${it.kind.name.replace('_', ' ')}"
                            },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    onClick = {
                        targetText = LeagueCapTargetPolicy.displayLevel(calculatedLevel)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("affordable-target-button")
                ) {
                    Text("Usar o nível que consigo pagar")
                }
            }
            Text(
                AffordableLevelPolicy.LIMITATION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        "Estimativa de recursos · só power-ups",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (!referenceAvailable) {
                        Text(
                            "Referência comunitária inválida ou incompleta. Cálculo bloqueado.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (!supported) {
                        Text(
                            "Este exemplar requer regras de custos especiais não certificadas. " +
                                "Sem orçamento normal aplicável.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        if (!allowCommunityEstimate) "A estimativa fica desativada até a autorizares."
                        else quote.explanation,
                        style = MaterialTheme.typography.bodySmall
                    )
                    val result = quote.plan
                    if (quote.canDisplayExactAmounts && result != null && familyId != null) {
                        val costs = result.costs
                        Text(
                            "Estimativa de power-ups: ${costs.stardust} Stardust · " +
                                "${costs.candyByFamily[familyId] ?: 0} Candy · " +
                                "${costs.xlCandyByFamily[familyId] ?: 0} XL Candy.",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (result.missingResources.isNotEmpty()) {
                            val missing = result.missingResources.joinToString(" · ") {
                                "${it.amount ?: "?"} ${it.kind.name.replace('_', ' ')}"
                            }
                            Text(
                                "Em falta segundo os saldos que registaste: $missing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (result.status == InvestmentPlanStatus.READY_NOW) {
                            Text(
                                "Saldos registados suficientes para este objetivo de power-ups. " +
                                    "Não significa que valha a pena investir.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        CommunityNormalPowerUpPack.LIMITATIONS,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Fontes: ${CommunityNormalPowerUpPack.SOURCE_URL} e Pokémon GO Hub.",
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
