# Pokémon Companion v3 — Candidate decision audit

**Design goal:** a player should understand whether the candidate's *IV spread* is better than the closest comparable saved Pokémon, without wrongly concluding that a weak meta species is valuable or that lower IV rank means safe to transfer.

## Evidence displayed
- The candidate's exact IV rank for the selected league, if the cached rank engine supports the species.
- Comparison with the **best ranked existing specimen of the same speciesId/form** in locally loaded Room Collection. Excludes the candidate's own ID.
- Evidence states: loading, unavailable species/rank, no comparable saved specimen, saved specimens with unavailable ranks, better/equal/worse IV rank.
- A next step written cautiously, plus explicit unknowns: actual battle meta strength, investment cost, evolutions, eligibility, CP/moves readiness.
- No Pokémon GO account access, login, imports, ranking guesses, investment guesses or transfer automation.

## Validation on Android (not executed)
| ID | Scenario | Pass condition |
|---|---|---|
| D01 | Room collection loading | Comparison waits, no false “no comparables” |
| D02 | Catalog species missing | Unavailable message, no invented rank |
| D03 | Rank calculation unavailable | Unavailable message, no approval |
| D04 | One candidate, no saved specimen of same species/form | Neutral no-comparison message, no meta claims |
| D05 | Two saved specimens, best existing rank #200, candidate #50 | Explains candidate has better rank IV for selected league |
| D06 | Best rank #50, candidate #50 | Explains equal rank and need to check CP/moves |
| D07 | Best rank #10, candidate #500 | Explains an existing specimen has better *IV* rank, does not recommend transfer |
| D08 | Saved specimen rank unavailable | Comparison incomplete, not “new is better” |
| D09 | Switch Little/Great/Ultra/Master quickly | No stale rank from previous league, no freeze |
| D10 | Two candidate cards in Inbox | Expanded comparison uses correct IVs and specimen ID |
| D11 | Open/close card then reenter | No unexpected permanent database mutation |
| D12 | Preview followed by Confirm | User action required, then Room transaction and Pokémon 360 |
| D13 | Accessibility on 360dp and large font | Long caution text readable, league chips scroll, buttons usable |
| D14 | Shadow and normal specimens | Comparison text scoped to IVs only; does not assert equal battle strength |
| D15 | Species with evolutions | Clearly states evolutions were not included in direct comparison |
| D16 | No CP, level, moves or stardust data | No cost numbers or readiness conclusions invented |

## Completion gates
- [x] Pure, tested policy for explanation states added to repo (pending CI).
- [x] Existing Room and RankRepository reused; no migration.
- [ ] Android unit suite and structural CI green.
- [ ] D01–D16 device acceptance, including large fonts and loading.
- [ ] Extend to evolution-aware comparison, verified seasonal meta evidence and exact resource costs only when those data are trustworthy.

The comparison is intended as *an informed reason to review*, not a keep/discard order.
