# Pokémon Companion v3 — Target levels and verified-cost boundary (PR #15)

## Delivered
- In Pokémon 360, the owner can choose a **target level** for an already-saved specimen. No preset target is fabricated.
- Computes projected CP with the real math engine and reports the number of half-level increments from the recorded current level.
- A result requires a matching catalog species/form, a registered CP and current level, coherent recorded CP vs calculated CP, a half-level target at least as large as the current level and no higher than 50.
- Generic Little / Great / Ultra / Master league limits are checked with the existing fail-closed OwnedEligibilityGate. This is not a seasonal cup check.
- Unsaved edits in the Build real form show a warning: only persisted data are simulated. No Room writes from the simulator.
- **New InvestmentCostQuoteGate** is a usable pure integration bridge to the existing engine InvestmentPlanner. If and only if a validated InvestmentCostPack, explicit Inventory Ledger, valid target, complete costs and a non-invalid plan are present, a future caller may expose an exact cost. Missing pack and missing inventory are **different** blocked states.
- This UI does **not yet have** an installed real InvestmentCostPack or a ResourceLedger: both are deliberately passed as null, not an invented pack or empty inventory. It displays “Sem pack de custos ...” rather than arbitrary Stardust/Candy prices.
- No edits to the persisted Collection schema, current Saved Team builds or IV-rank tables.

## Android manual acceptance scenarios (all NOT executed)
| ID | Action | Pass condition |
|---|---|---|
| T01 | Open 360 for a specimen with no recorded CP | Target simulator explains missing data |
| T02 | Open 360 for a specimen with no recorded level | No computed target / cost quote |
| T03 | Enter target 30 with current level 20.5 | Correct count of 19 half-level increments |
| T04 | Enter 30,5 with Portuguese comma | Accepted |
| T05 | Enter 30.5 with decimal point | Accepted |
| T06 | Enter 23.3, negative or above 50 | Target rejected |
| T07 | Attempt target below current level | Clearly explains level cannot be reduced |
| T08 | Save wrong CP for otherwise known level | Projection blocks on inconsistent CP |
| T09 | Correct recorded CP and save | New projection succeeds from Room update |
| T10 | Edit CP/level but leave unsaved | Simulator requests saving; no use of unsaved fields |
| T11 | Switch Great / Ultra / Master rapidly | Feasibility reflects latest league |
| T12 | Exceed 500 CP in Little | Generic cap blocks target |
| T13 | Exceed 1500 CP in Great | Generic cap blocks target |
| T14 | No cap in Master | Doesn't falsely impose 2500 CP |
| T15 | Target Mega or Primal | Generic standard rules reject |
| T16 | No level target entered | Explanation, no guessed default |
| T17 | Target equals current level | Zero increments, not a zero-Candy claim |
| T18 | Eligible target but no cost pack | Show explicitly unknown cost, no numeric budget |
| T19 | Existing Build real has unsaved edits | Save warning replaces projection |
| T20 | Large font, 320/360dp, TalkBack | Input label and warnings usable |
| T21 | Device process recreated | Target selection behaves predictably |
| T22 | Confirmed Pokémon 360 navigation | No unexpected mutation or disconnection |
| T23 | Cost pack forged (JVM) | Rejected by hash validation |
| T24 | Cost pack incomplete (JVM) | Exact total suppressed |
| T25 | Known cost pack + explicit resource inventory (JVM) | Real InvestmentPlanner produces verifiable quote |
| T26 | Inventory absent (JVM) | Never replaced with ResourceLedger() |
| T27 | Player has known 0 inventory (JVM) | Exact cost retained but shortages identified |
| T28 | Compare two different versions of test cost pack (JVM) | Different cost results, same IVs |

## Trust and release gates
- [x] Documented module boundary and unit fixtures created; CI completion must be verified on actual PR commit.
- [ ] Full Android build + relevant policy JVM tests green.
- [ ] T01–T22 on-device cases tested; T23–T28 have JVM coverage but no real device acceptance.
- [ ] Install a complete independently verified dated cost pack and a nullable saved user resource ledger.
- [ ] Introduce versioned user-selected goal builds (level, species evolution, fast/charged move plan and Best Buddy strategy).
- [ ] Verify resource calculations on an actual Pokémon GO account without scraping credentials.
- [ ] Validate external event/cup rules and season meta before claiming that an investment is useful.

No exact resource prices have been asserted for Pokémon GO in this PR; the fixture's values in tests are deliberately synthetic.
