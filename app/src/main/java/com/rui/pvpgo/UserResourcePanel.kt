package com.rui.pvpgo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Player-controlled private ledger. Blank means unknown, "0" means a
 * deliberate zero. Saves are explicit and immediately persisted locally.
 */
@Composable
internal fun UserResourcePanel(
    familyId: String,
    familyLabel: String,
    saved: UserResourceBalances,
    store: UserResourceStore,
    onSaved: (UserResourceBalances) -> Unit
) {
    var expanded by rememberSaveable(familyId) { mutableStateOf(false) }
    var inputs by remember(familyId, saved) {
        mutableStateOf(UserResourcePolicy.inputsFrom(saved))
    }
    var feedback by remember(familyId) { mutableStateOf<String?>(null) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Os meus recursos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${saved.knownCount}/7 saldos registados · Candy de $familyLabel",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (saved.knownCount == 7)
                    "Inventário registado. Confirma os valores no Pokémon GO antes de investir."
                else "Os saldos em falta continuam desconhecidos, não são considerados zero.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = { expanded = !expanded; feedback = null },
                modifier = Modifier.fillMaxWidth().testTag("resource-toggle")
            ) { Text(if (expanded) "Fechar edição de recursos" else "Registar ou atualizar recursos") }
            if (expanded) {
                ResourceBalanceField.entries.forEach { field ->
                    OutlinedTextField(
                        value = inputs[field].orEmpty(),
                        onValueChange = { raw ->
                            // One atomic map update per field; other fields do not reset.
                            if (raw.length <= 9 && raw.all(Char::isDigit)) {
                                inputs = inputs + (field to raw)
                                feedback = null
                            }
                        },
                        label = { Text(field.label) },
                        supportingText = {
                            Text("Vazio = desconhecido · 0 = zero confirmado")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("resource-input-${field.name.lowercase()}")
                    )
                }
                Button(
                    onClick = {
                        val parsed = UserResourcePolicy.balancesFrom(inputs)
                        if (parsed == null) {
                            feedback = "Há um saldo inválido. Revê os números introduzidos."
                        } else {
                            runCatching { store.save(familyId, parsed) }
                                .onSuccess {
                                    onSaved(parsed)
                                    feedback = "Recursos guardados apenas neste dispositivo."
                                    expanded = false
                                }
                                .onFailure {
                                    feedback = "Falha ao guardar os recursos. Tenta novamente."
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("resource-save")
                ) { Text("Guardar recursos") }
            }
            feedback?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                UserResourcePolicy.SCOPE_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
