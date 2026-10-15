# Pokémon Companion V3 — PR32: Comparing IVs of owned Pokémon

## Gap
The Collection PvP guide already selects a league and takes the user to real IV Targets. IV Targets previously displayed only the best-owned IV rank, so a non-expert could not decide which actual owned specimen to inspect and could mistake a target CP for current CP.

## Implemented
- All saved specimens of the selected species are ordered by theoretical stat-product IV rank and stable ID; results from other species/forms are excluded.
- Three specimen cards by default, with an option to reveal every specimen. LazyColumn supports long collections.
- Each card shows nickname, original IVs, current saved CP/level (question mark when unknown), theoretical IV rank and a truthful caveat from existing PvpGuidePolicy.
- Missing or conflicting CP/level requires review. Above-cap specimens cannot be lowered to the requested general CP cap by a power-up. In-range specimens still have no meta, cup, moves or affordability guarantee.
- Action "Abrir Pokémon 360" opens exactly the chosen saved specimen and Back returns to IV Targets at the same league; callback checks saved ID and species.
- The hero explicitly calls ranking "Melhor IV potencial" and its CP "CP alvo", never the saved specimen CP.
- None of these actions modifies the collection, Stardust/Candy or the Pokémon GO game.

## Required gates
- Four new Android Compose tests: meaningful owned ranking, unknown CP safety, reveal all/open exact specimen, no inactive action when callback unavailable.
- One actual root-app journey: Collection guide Ultra → IV Targets → saved Pokémon 360 → Back → IV Targets Ultra, unchanged Room collection.
- Previously verified 65 Android instrumented cases remain required. New minimum 70/70 real emulator JUnit cases with 0 failures/skips, plus green APK/JVM workflow.

## Limits
Stat-product rank is not a competitive meta recommendation; move availability, cup eligibility, event data, investment resources and device UX still need separate validation. The 110 historical global E2E scenarios are not promoted automatically from these five automated cases.
