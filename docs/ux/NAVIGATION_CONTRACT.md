# Navigation Contract — UX V2

## Invariants

1. Root destinations are **Hoje, Coleção, Equipas, Batalhas, Mais**.
2. Tapping a root destination is an explicit context switch.
3. Opening a deep destination is **not** an implicit context switch.
4. Back reverses the actual journey, not the feature's canonical ownership.
5. A deep screen may have a canonical owner and still preserve its origin.
6. Bottom navigation must never "teleport" to another selected item merely because a screen is implemented in that feature package.
7. Visible Back and Android system Back must agree.
8. User-entered state survives a temporary context switch and process recreation when losing it would be surprising.

## Root destinations

| Destination | Product meaning |
|---|---|
| Today | Current recommendation/priority |
| Collection | Owned Pokémon + Pokédex |
| Teams | Build/edit/test teams |
| Battles | Session/log/history/learning |
| More | Directory/settings/secondary tools |

## Shared deep destinations

These are intentionally not root tabs.

| Destination | Canonical owner | Allowed origins |
|---|---|---|
| Pokémon profile | Collection | Today, Collection, Teams, Battles, Radar/Search |
| Events | Planning | Today, More |
| Agenda | Planning | Today, More |
| Radar | Tools | Today, Collection, Teams, More |
| Matchup/Confronto | Battles | Pokémon, Teams, Battles |
| Evaluation | Collection | Pokémon, Bubble, Import |
| Team analysis | Teams | Team builder/results, Battles recommendation |
| Battle detail | Battles | History, Last session |
| Data health | App health | Today, Collection, More |

## Back policy examples

- `Hoje → Eventos → Radar → Back` = Eventos, then Hoje.
- `Hoje → Pokémon → Back` = Hoje with previous scroll/state.
- `Coleção(query/filter/scroll) → exemplar → avaliação → Back → exemplar → Back` = same Collection query/filter/scroll.
- `Equipas → criar → resultados → análise → alterar opções` = returns to builder with previous choices intact.
- `Batalhas → histórico → detalhe → Back` = same history filters and scroll.
- Deep link to Pokémon without prior in-app origin uses a safe default owner (Hoje) and still provides a visible Back/up exit.

## Bottom navigation policy

- Root screen: visible and selected.
- Deep screen: may remain visible, but selected item reflects **journey origin**, not implementation package.
- Focus/task flows may hide bottom navigation when switching tabs would risk accidental loss; those flows must expose explicit cancel/back.
- Selecting another root tab is always user-initiated and preserves restorable state of the previous tab.

## Migration policy

### Legacy state to remove from root shell

- manual `tab` switching as navigation;
- `moreStartRoute` as cross-feature routing;
- `selectedReturnTab` for Pokémon;
- ad-hoc root `when(tab)` as the product navigation graph.

Feature-local routers can temporarily remain while their feature is migrated, but must obey the same Back contract.

## P1 acceptance tests

1. Root tab selection is represented by Navigation back stack state.
2. Today → Event does not select More.
3. Today → Radar does not select More.
4. Today → Pokémon → Back returns to Today.
5. Explicit tap on More selects More.
6. Root tab switching restores previous UI state.
7. Invalid Pokémon route has a recoverable UI and Back.
8. System Back and visible Back reach the same previous destination.
9. Navigation destinations use typed Kotlin routes, not string concatenation.
10. Existing Collection navigation regression tests remain green.
