# UX State Matrix

The state model is part of the product, not an implementation afterthought.

Legend: R = required, C = conditional, — = not applicable.

| Feature | Loading | Empty | Partial | Stale | Offline | Error | Permission denied | Success/recovery |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Hoje | R | R | R | R | R | R | C | R |
| Events/Agenda | R | R | R | R | R | R | — | R |
| Collection | R | R | R | C | C | R | C | R |
| Pokédex | R | R | R | R | R | R | — | R |
| Import | R | R | R | — | — | R | C | R |
| Pokémon/Evaluation | R | C | R | C | C | R | — | R |
| Teams | R | R | R | C | C | R | — | R |
| Team analysis | R | R | R | R | R | R | — | R |
| Battles | R | R | R | — | — | R | — | R |
| Battle log | C | R | R | — | — | R | — | R |
| Radar | R | R | R | R | R | R | R | R |
| Bubble | C | C | R | — | — | R | R | R |
| Backup/restore | R | C | R | — | — | R | C | R |
| Settings | C | C | C | — | — | R | C | R |

## Required recovery language

A state must answer three questions:

1. **What happened?**
2. **What can I still trust/use?**
3. **What can I do next?**

Examples:

- Offline Events: "Não conseguimos atualizar. Estás a ver dados guardados de [timestamp]." Actions: Retry / Continue offline.
- Radar feed down: show target configuration and last successful check; do not present "no spawns" as if it were fresh truth.
- Import ambiguity: keep confirmed items, isolate uncertain items, allow correction/retry without restarting the import.
- Permission denied: explain why, offer Continue without feature; after permanent denial offer Open settings.
- Backup failure: original local data remains authoritative; never replace it with a partially restored database.

## No Dead End gate

Every reachable state must expose at least one legitimate transition:

- continue;
- complete;
- back;
- cancel;
- retry;
- change input;
- open settings;
- restore previous data.

A screen that only explains failure without a legitimate next action fails the gate.
