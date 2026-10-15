# Pokémon Companion v3 · Power-ups with a dated community cost pack (PR #17)

## Product behavior
In Collection → Pokémon 360 → **Simular nível objetivo**, the user now has an **optional, off-by-default** community price reference after a valid CP projection. It is active only for normal Pokémon with no exceptional pricing and when the user chooses to use the dated source. A saved **fully-known seven-field inventory** from PR #16 is also required.

If the source/pack is invalid, the player has not opted in, any inventory field is missing, the form is unsupported, the specimen is Lucky/Shadow/Purified, or the level/CP doesn't pass the existing gate, **do not display an amount**. Distinguish "unknown" from a real zero.

When eligible, call the existing `InvestmentPlanner.plan` with the pinned pack and saved ResourceLedger. Show (a) the base-normal **power-up-only** estimate in Stardust, regular Candy, XL Candy, (b) real shortages by resource from the planner. This is never an official or live price and never an investment recommendation.

## Source boundaries
Reviewed on **2026-10-15**, community, not a verified game-master snapshot:
- [PokeTools power-up calculator](https://www.poketools.com/pokemon-go-power-up-calculator)
- [Pokémon GO Hub XL Candy guide, 2026-08-27](https://pokemongohub.net/post/guide/xl-candy-guide-how-to-get-power-up-costs-and-mechanics/)
- [Pokémon GO Hub historical normal power-up table](https://pokemongohub.net/post/guide/guide-to-power-up-costs-in-pokemon-go/)

All 98 half-level power-up steps from 1.0→50.0 are pinned by a deterministic content hash. Normal cross-checks:
| Levels | Stardust | Regular Candy | XL Candy |
| --- | ---: | ---: | ---: |
| 1 → 40 | 270000 | 304 | 0 |
| 20 → 40 | 225000 | 248 | 0 |
| 30 → 40 | 150000 | 182 | 0 |
| 40 → 50 | 250000 | 0 | 296 |
| 20 → 50 | 475000 | 248 | 296 |
| 30 → 50 | 400000 | 182 | 296 |

**Excluded:** Eternatus exceptional Candy rules; Lucky, Shadow/Purified discounts or surcharges; Mega/Primal forms; special transformations; evolution Candy/items; second Charged Move fees; TMs/move change and legacy/event restrictions; 50.5/51 Best Buddy boosts; actual PvP utility; guaranteed eligibility for special cups. A standard power-up table should never be described as an exhaustive build budget.

Source hash only verifies the **reviewed local fixture**: it is not a signature proving that Pokémon GO servers charge these amounts today. The opt-in UI warns that the price reference may be stale.

## Tests
- 19 Kotlin/JVM cases: price bands, level-40 switch, independent reference cumulative totals, pinned hash, family inventory, shortage calculation, unsupported form controls, source attribution.
- Structural trust verifier `tests/v3_community_costs/verify_reference.py`.
- Existing checks covering null CP/level, item condition, investment gates, unknown inventory and Room must still pass.
- No new database migration; no network permission/API requirement.

## Acceptance on Android — all **unexecuted**
| ID | Scenario | Expected |
| --- | --- | --- |
| C01 | Fresh Pokémon 360 for saved normal specimen | Opt-in disabled by default |
| C02 | CP/level incomplete | No misleading budget |
| C03 | CP vs level+IV mismatch | No budget |
| C04 | Above Great CP cap | Budget blocked for that league target |
| C05 | Switch between four leagues | Reference opt-in does not bypass constraints |
| C06 | Regular 20→40, all 7 resource balances known | Display 225000 Stardust + 248 Candy, source caveat |
| C07 | Regular 40→50 | Display 250000 Stardust + 296 XL Candy, source caveat |
| C08 | Regular 20→20 | Display zero upgrade cost, not false meta approval |
| C09 | No Candy recorded | Unknown inventory / no cost quote |
| C10 | Explicit zero Candy | Show actual Candy shortage |
| C11 | Resource balances enough | Explain eligible for power-up prices only |
| C12 | Unsaved build edits | No quote until real build saved |
| C13 | Lucky specimen | Never apply standard Stardust estimate |
| C14 | Shadow or Purified | No standard cost approximation |
| C15 | Mega/Primal forms | No normal price reference |
| C16 | Eternatus | Exceptional Candy rules require separate source |
| C17 | Altered pack SHA/test data | Reference validation refuses quotes |
| C18 | 320/360dp at 200% font, TalkBack | Checkbox, caveat, totals and shortages legible |

## Next to ship
Verify real Lucky/Shadow/Purified and exceptional species modifiers from a dated and independently auditable game master; link trusted evolution/move costs with proper user-selected build. Then include time-versioned PvP meta and cup rules for utility ranking, not only IV rank. All Android/E2E gates remain outstanding.
