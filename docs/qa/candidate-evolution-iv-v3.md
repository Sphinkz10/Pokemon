# Companion V3 · Candidate evolution IV opportunities

## User journey
Open **Hoje → Coleção → Inbox → Ver ranking de IVs por liga** for a manually registered candidate. The preview now shows (i) existing same-species IV rank comparison, and (ii) a *separate* compact section of catalog-linked future evolution targets, each ranked against locally owned Pokémon that can reach **that same target species/form**.

This is IV-stat evidence, not a battle-meta score, an investment quote, eligibility confirmation or a save/transfer order. The user must still confirm the candidate manually before it is added to Collection.

## Scope and safety
- Only explicit `evolutionSpeciesIds` graph edges in the current catalog count. An inferred shared family ID is **not** enough.
- Exclude unreleased and Mega target entries from the short preview.
- Candidate IVs are evaluated as the target species' hypothetical evolved stats for the currently selected Little / Great / Ultra / Master league. Do not compare rank numbers across different target species.
- Only count registered comparison specimens whose current species/form has a catalog-linked path to that target. Unknown species do not count.
- Cap preview at 8 targets per expansion. If more targets exist, explain how many were omitted and recommend inspecting other evolutions individually.
- No automatic transfer or merge, no resource estimates, no assumptions of available Candies, stones/items, buddy conditions, actual evolution legality, movesets or up-to-date meta relevance.
- Rank computation remains on a background coroutine and only runs when a candidate is expanded.

## Android device acceptance — 0 executed
| ID | Setup / Action | Expected |
|---|---|---|
| E01 | Candidate with two documented evolutionary targets | Both target forms appear as independent IV-rank comparisons |
| E02 | Candidate with branching evolutions | Only known reachable branches appear |
| E03 | Own an earlier-stage specimen that could reach target | Its IV rank for the **target form** contributes to best-owned |
| E04 | Own a Pokémon in a sibling branch | Sibling is not counted as reachable target |
| E05 | Own an evolved Pokémon already at target | It is counted for exactly that target |
| E06 | Own unknown catalog species | No fabricated rank or relation |
| E07 | Catalog missing evolution link | No invented target displayed |
| E08 | Form is unreleased or Mega | Not shown in the quick target section |
| E09 | Candidate target has no valid IV rank | Display unavailable; no favorable comparison |
| E10 | All comparable ranks unavailable | Explain incomplete, not inferior or superior |
| E11 | Candidate rank better than owned best for one target | Explicitly scoped explanation; no comparisons across targets |
| E12 | Candidate rank worse for another target | Still no transfer advice, no meta-value claim |
| E13 | Switch between four leagues rapidly | Data is recalculated for the new league without stale rows |
| E14 | Empty / still-loading Room | No false “zero other specimens” conclusion |
| E15 | More than 8 catalog targets | Limit evaluation and show omitted-target count |
| E16 | Large fonts on 320/360dp Android | No clipped key actions, accessible league chooser |
| E17 | Unexpanded Inbox candidate | No evolution scan until user requests |
| E18 | Confirm candidate after preview | Explicit approval and existing transaction/handoff to Pokémon 360 preserved |

## Release gates
- [ ] Android CI unit suite green (12 comparator tests + 8 explanation tests)
- [ ] Structural integrity gate green
- [ ] E01–E18 manual tests recorded with device/build versions
- [ ] Verified meta, eligibility, cost and evolution conditions integrated only when actual data sources permit

**All E01–E18 are unexecuted**. No claim of Pokémon GO account connectivity or a verified final APK.
