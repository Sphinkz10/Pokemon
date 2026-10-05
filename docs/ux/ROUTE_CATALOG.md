# Canonical Route Catalog

Visual source: Figma page `14 — App atual · igual ao código (2.0)`.
Inventory captured 2026-10-06.

## Product frames

| Figma node | Frame | Canonical area | V2 role |
|---|---|---|---|
| 297:64 | Hoje | Today | Root |
| 299:109 | Coleção | Collection | Root |
| 300:261 | Pokédex — Fase 2 | Collection | Collection subview |
| 301:384 | Pokémon 360 — Fase 2 | Collection/shared | Deep shared |
| 302:457 | Equipas | Teams | Root |
| 303:539 | Batalhas — Fase 2 | Battles | Root |
| 303:656 | Mais — Fase 2 | More | Root |
| 304:758 | Criar equipa | Teams | Builder |
| 308:789 | Pesquisa — Fase 4 | Shared | Global search |
| 308:11727 | Privacidade | More/Settings | Deep |
| 308:11790 | Registo rápido — Fase 4 | Battles | Task flow |
| 308:11841 | Bolha · Fase 4 | More/Companion | Tool |
| 308:12004 | Permissões | More/Settings | Deep |
| 308:12067 | Eventos | Planning/shared | Deep shared |
| 308:12194 | Registo detalhado — Fase 4 | Battles | Task flow |
| 308:12261 | Diagnóstico · Fase 4 | More/Settings | Deep |
| 308:12349 | Gestor de equipa — Fase 4 | Teams | Deep |
| 308:12485 | Estatísticas | More/Results | Deep |
| 308:12636 | Boas-vindas — passo 1 | Onboarding | Flow |
| 308:12682 | Histórico | Battles | Deep |
| 308:12832 | Eventos marcados | Planning/shared | Deep |
| 313:1136 | Adicionar Pokémon — Fase 3 | Collection | Import hub |
| 314:1176 | Rever lidos — Fase 3 | Collection | Review flow |
| 315:1208 | Exemplar | Collection | Deep |
| 316:1239 | Importar capturas — Fase 3 | Collection | Import flow |
| 317:1241 | Alvos de IV — Fase 3 | Collection/shared | Target tool |
| 318:1269 | Pesquisas para o jogo — Fase 3 | Collection/shared | Context tool |
| 319:1271 | Analisar IVs — Fase 3 | Collection/shared | Evaluation |
| 320:1300 | Team Lab — Fase 1 | Teams/shared | Analysis |
| 321:1384 | Meta e taças — Fase 3 | Teams | Planning/meta |
| 322:1456 | Confronto — Fase 3 | Battles/shared | Analysis |
| 323:1505 | Criar equipa — resultados | Teams | Builder result |
| 324:1573 | Detalhe de batalha — Fase 3 | Battles | Deep |
| 325:1575 | O meu meta | Battles | Learning |
| 326:1603 | Definições — Menu curto | More/Settings | Hub |
| 327:1638 | Preferências PvP — Fase 4 | More/Settings | Deep |
| 328:1669 | Backup — Fase 4 | More/Settings | Deep |
| 329:1697 | Boas-vindas — passo 2 | Onboarding | Flow |
| 331:1700 | Plano da semana | Planning/shared | Deep |
| 332:1794 | Última sessão — Fase 5 | Battles/shared | Summary |
| 333:1824 | Estado dos dados | App health/shared | Deep shared |
| 334:1853 | Radar | Tools/shared | Deep shared |

Assets/components/readme frames are intentionally excluded from product routing.

## Current implementation routers

- Root shell: `AppTab` + manual `tab` state in `MainActivity.kt`.
- Cross-feature Today routing: `moreStartRoute` + forced `tab = MORE`.
- Pokémon root detail: `selected` + `selectedReturnTab`.
- Collection: feature-local `CollectionRoute` with explicit return-route policy.
- Teams: feature-local `TeamsGoldenView`.
- Battles: feature-local `BattlesGoldenPage`.
- More: feature-local `MoreRoute`.

## Migration priority

### P1 root/shared
Today, Collection, Teams, Battles, More, Pokémon, Events, Agenda, Radar.

### P1.5 shared analyses
Matchup, Team Analysis, Data Health, Search.

### P2+ feature-local
Collection import/review/detail routes, team builder/detail routes, battle session/history/detail routes, settings/onboarding.

The route catalog is updated when a product destination is added, removed, merged or renamed.
