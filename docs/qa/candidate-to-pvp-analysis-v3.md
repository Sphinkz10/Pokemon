# Pokémon Companion V3 · Candidate → IV preview → Pokémon 360

**Scope:** Android UX and data integrity. These scenarios are **manual acceptance cases** and are NOT counted as passed until a device log is attached.

## User mission
1. In Pokémon GO, use the language-correct search filter to find interesting specimens.
2. Manually record the Pokémon species and its three appraisal IVs in Companion.
3. Review a temporary entry (Room Inbox), including potential duplicates and an optional IV-rank preview.
4. Explicitly confirm it to the collection, then inspect Pokémon 360.
5. Never equate IV-rank with species meta relevance or permission to transfer.

## Device acceptance matrix

| ID | Setup | Action | Expected result |
|---|---|---|---|
| C01 | New install, local Room not loaded | Open Inbox from Hoje | Loading shown; no fabricated “0 candidatos” before first Room emission |
| C02 | Truly empty Room Inbox | Open Inbox | Explain GO search is not automatic import; quick and guided actions visible |
| C03 | Empty Inbox | Tap “Registar espécie e IVs” | Direct quick entry; species and all three IVs editable |
| C03A | Fresh manual form | Inspect Attack/Defense/HP inputs | All three initially blank; save disabled until explicit values 0–15 are entered |
| C03B | Any manual entry | Clear one IV or type 16 | Save disabled when missing; values outside 0–15 are rejected without inventing replacements |
| C04 | Quick entry entered from Inbox | Android hardware/system Back | Returns to Inbox, not Import menu |
| C05 | Empty Inbox | Tap guided entry | Detailed manual form opens; Android Back returns to Inbox |
| C06 | Complete known species + 0/15/15 IV | Submit quick entry | Creates a **pending** candidate, not a permanent record |
| C07 | Pending candidate | Tap “Ver ranking de IVs por liga” | On-demand loading then rank or unavailable message; no freeze |
| C08 | Preview visible | Change Little, Great, Ultra, Master | Each selection describes IV rank for *this species and league*, not global PvP strength |
| C09 | Unknown catalog species | Preview | Missing-catalog state rather than invented rank |
| C10 | Candidate equals possible duplicate | Review | No automatic merge; action makes separate-save implication explicit |
| C11 | Pending candidate | Tap “Confirmar e analisar” | One Room transaction creates permanent exemplar and marks inbox entry reviewed |
| C12 | Delayed Room collection emission | Confirm | Never opens blank detail or wrongly redirects to list before confirmed record appears |
| C13 | Confirmed and emitted exemplar | Continue | Opens Pokémon 360 detail of the exact ID, displays original IVs, league tabs |
| C14 | Two pending candidates | Confirm first | Detail Back returns to Inbox to process remaining candidate |
| C15 | One pending candidate | Confirm last | Detail Back returns to Collection |
| C16 | Pending candidate | Discard | Removes from pending without creating permanent exemplar |
| C17 | Compact 320/360/390dp and 200% text | Open candidate card | Preview controls and confirm/discard readable; 48dp minimum targets |
| C18 | App closed between submit and confirm | Relaunch | Pending candidate remains in Inbox; no silent approval |
| C19 | Rapid league switching | Preview | No stale rank from previous selection; no UI freeze or unhandled error |
| C20 | No Pokémon GO installed | Use manual entry | Companion remains usable offline; no login or account read |

## Automated gates
- `CandidateReviewPolicyTest`: 12 JVM tests for explicit IV input, safe labels, no fabricated ranking and exact-ID Room navigation gating.
- `CollectionNavigationPolicyTest`: includes manual capture return-to-Inbox regression.
- `tests/v3_candidate_review/verify_handoff.py`: structural guard over navigation, background rank calculation, transaction, loading and warnings.
- Android Gradle unit test + APK CI required before merging.

## Unresolved limitations / release blocking
- Device acceptance C01–C20: **0 executed**.
- GO data not automatically imported. The clipboard filter is a separate, manual journey.
- Preview rank is **IV rank only**. It does not yet integrate Pokémon’s meta utility, cost, or league restrictions into one actionable “keep” verdict.
- No screenshot OCR in this delivery.
- Dataset completeness, regional app text, TalkBack, large-font sizes and performance must be verified before release.
