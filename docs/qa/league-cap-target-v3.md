# Pokémon Companion v3 — One-tap maximum league CP target (PR #18)

## User problem and feature
A beginner should not need to guess 20–40 half-level targets in order to find the highest CP-compatible power-up level for their own Pokémon. This delivery adds a one-tap **Usar nível máximo para esta liga** button directly below the manual target field in Pokémon 360. Master League says **Usar nível 50 (sem limite de CP)**.

Only a click replaces the *local, unsaved target-level input*. This button does **not** modify CP, level, IVs, moves, collection, resource inventory or saved plans, and cannot automatically enable a community cost estimate.

## Rules and safety
- Match the exact species/form currently recorded in the collection. CP and level must be manually saved in Build real and agree with IVs and current species stats.
- Reuse `InvestmentTargetSimulator.simulate` for the owned specimen **before and after** choosing the mathematical level; never bypass standard league eligibility gates.
- For Little/Great/Ultra, use `PokemonMath.highestLevelAtOrBelowCap` from the **current level** through 50 in steps of 0.5; Master has no CP cap and uses 50.
- If the Pokémon is already over its league cap, show that it **cannot de-level** and disable the button. Incomplete CP/level, inconsistent CP and mismatched catalog form likewise disable it.
- If the specimen's level exceeds 50, do not fabricate Best Buddy/51-level support. Mega, Primal or unreleased forms remain blocked by the standard eligibility gate.
- Existing unsigned or incomplete cost packs, inventory unknowns, Lucky/Shadow/Purified limitations, community source consent and `InvestmentCostQuoteGate` remain unchanged.
- This is a **CP-limit convenience choice**, not a recommended competitive build: current move/meta/ruleset and evolution viability are separate questions.

## Examples
- Great League: choose the highest half-level where the CP of the exact specimen does not exceed 1,500, with no suggestion to de-level.
- Ultra League: same calculation under 2,500 CP.
- Little League: same calculation under 500 CP, but individual cups require additional rules that this helper does not certify.
- Master League: level 50 is the supported maximum; it is not a promise that powering up is worth the resources.
- A specimen already at the maximum returns **zero new power-up steps** without encouraging extra spending.

## Evidence
- 20 new Kotlin/JVM unit tests in `LeagueCapTargetPolicyTest`.
- `tests/v3_league_cap_target/verify_target.py` checks 21 production/source safety and integration invariants.
- The Android debug CI is responsible for Kotlin compilation, unit tests, signature check and production of an APK.
- **No device tests were executed** by adding this code. Existing global Android acceptance count remains 0/110.

## Manual device acceptance (all unexecuted)
| ID | Case | Expected |
|---|---|---|
| A01 | Open 360 with saved valid Great build | Maximum target button shows a legal value |
| A02 | Press maximum Great target | Only manual target field changes; no Room update |
| A03 | Switch to Ultra | Suggested level and projected CP updated |
| A04 | Switch to Little | Enforce 500 CP cap |
| A05 | Switch to Master | Level 50 and no invented CP cap |
| A06 | Already above Little CP cap | Suggestion disabled with cannot-de-level explanation |
| A07 | No saved CP | Disabled; asks for data |
| A08 | No saved level | Disabled; asks for data |
| A09 | Mismatched CP vs species/IV/level | Disabled; asks to correct |
| A10 | Mismatched catalog species/form | Disabled |
| A11 | Mega or Primal | Not wrongly certified for standard league |
| A12 | Saved level 50.5 / 51 | No simulated de-leveling or unsupported target |
| A13 | User editing unsaved Build real | Disabled pending explicit save |
| A14 | Tap maximum then override level manually | Typed manual target still works |
| A15 | Cost reference initially off | Auto-target does not activate cost estimates |
| A16 | Cost reference accepted + all resources known | Existing planner estimates upgrade only |
| A17 | A Pokémon already at its cap-compatible maximum | Zero increments; no "must invest" claim |
| A18 | 320dp/360dp, 200% font, TalkBack | Call-to-action and caveat remain legible |

## Next
Implement a second optional suggestion, **highest level affordable with the resources explicitly saved**, using the fully verified cost gate, with no meta endorsement. Separately, audited GO meta/cup/evolution/move data and Android E2E are still required.
