# Companion V3 — Before you spend resources (P0/P1)

## Purpose
The prior Inbox journey correctly compared candidate IV ranks, including catalog-linked evolutions. It did **not** provide enough evidence to advise spending Stardust, Candy, XL Candy, Elite TMs or other resources. The existing `InvestmentPlanner` correctly requires a validated versioned cost pack, a particular owned specimen and target level/build, and a resource ledger. That full pipeline is **not yet wired** into the candidate journey; this delivery deliberately **does not fake an exact budget**.

## New on-device journey
1. In Inbox, expand a candidate and select Little / Great / Ultra / Master. An **Antes de investir** short checklist shows the most urgent missing input.
2. Confirm the candidate explicitly. Pokémon 360 now exposes the complete **Checklist para investir** for that owned specimen, using the selected league.
3. Enter CP, current level and actually known moves in **Build real**. Save changes. The checklist recomputes from the persisted Room record.
4. If saved CP disagrees with CP calculated from the known species/form, IVs and level, the app says **Rever** instead of guessing which field is right.
5. Generic league checks are powered by `StandardRulesets.forLeague` and `OwnedEligibilityGate`. They can flag CP limits, Mega and Primal restrictions; they **cannot certify an event cup**.
6. Until verified costs, target build and inventory exist, the cost check remains **Falta** even if all basic input checks pass.

## Android acceptance plan — NOT executed
| ID | Scenario | Expected result |
|---|---|---|
| R01 | Manual IV-only candidate with no CP/level/moves | All missing fields explained; no fake budget |
| R02 | Candidate saved and opened in Pokémon 360 | Checklist survives handoff and is based on saved exemplar |
| R03 | Edit CP on 360 and save | Re-read saved Room record, recompute checklist |
| R04 | Edit level and save | Coherence re-evaluated with new level |
| R05 | Add fast move but no charged moves | Missing charged move remains visible |
| R06 | Add one charged move | No assumption that second move is unlocked |
| R07 | CP + level + IVs coherent | Only generic checks pass; no investment approval |
| R08 | CP + level + IVs incoherent | Review warning (not a silently changed CP) |
| R09 | Over 500 CP in Little | Generic CP restriction flagged, not a cup verdict |
| R10 | Over 1500 CP in Great | Generic CP restriction flagged |
| R11 | Normal species in Master | No invented CP limit |
| R12 | Mega specimen | Generic league ineligibility indicated |
| R13 | Primal specimen | Primal gate prevents false standard eligibility |
| R14 | Species/form missing from catalog | Species-dependent check remains unknown |
| R15 | Switch league rapidly | Latest league reflected without stale checklist |
| R16 | Load Room slowly | No accidental substitute of a guessed build |
| R17 | No validated cost pack | Stardust/Candy budget not shown |
| R18 | 320dp, 360dp, text scale 200%, TalkBack | All instructions legible; no clipped action controls |
| R19 | No verified current meta | No battle-strength or keep/transfer advice |
| R20 | Open/close preview without confirm | No ownership mutation |

## Automated evidence
- 16 JVM cases in `InvestmentReadinessPolicyTest`, including level/CP consistency, generic caps and special forms.
- Source integrity gate `tests/v3_investment_readiness/verify_readiness.py`, added to Android CI.
- Shared, modular Compose component for Inbox and Pokémon 360; no Room schema or migration changes.

## Further work before a genuine investment recommendation
- Obtain, audit and version a comprehensive `InvestmentCostPack` and real `ResourceLedger`; never default unknown balances to zero.
- Explicit user-selectable target species, level, moves and XL/Best Buddy strategy.
- Connect exact build targets to `InvestmentPlanner`; preserve `PARTIAL_UNKNOWN_COST`, `WAIT_EVENT`, `INVALID` and `MISSING_RESOURCES` states.
- Couple with **verified and dated** meta results and cup/event rulesets only if certification gates permit them.
- Test R01–R20 on Android; all 20 are currently **unexecuted** and not counted toward global 110 E2E cases.
