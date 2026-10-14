# Pokémon Companion v3 — GO search language QA

**Status:** manual acceptance pending. Passing a build does not prove the GO search returns candidates.

## Why this matters
The Companion has **no access to Pokémon GO inventory**. It only copies a curated candidate search string. A string that produces zero results can reflect game language, genuine lack of matches, or a narrow filter. Never infer that no eligible Pokémon exist or suggest mass transfers from a search result.

## Official sources
- English: https://niantic.helpshift.com/hc/en/6-pokemon-go/faq/1486-searching-filtering-your-pokemon-inventory/
- Português (Brasil): https://niantic.helpshift.com/hc/pt-br/6-pokemon-go/faq/1486-searching-filtering-your-pokemon-inventory/

## Automated contract
- `TodayGoSearchLanguageTest`: six objectives × three scopes; exact PT-BR strings, legacy English unchanged, unknown-preference fallback, preserved star ratings and warnings.
- Existing `TodayJourneyPolicyTest` and `TodayGoFilterScopeTest` remain mandatory.
- The integrity gate requires the language picker to be wired to the query and persisted in SharedPreferences.
- No locale-based auto-import or automatic appraisal/transfer actions.

## Android device acceptance checklist (not yet executed)
Test on a real device with both Pokémon GO languages and a collection containing a known match (where possible).

| ID | Scenario | Expected |
|---|---|---|
| L01 | First install, open Hoje → Grande | English selected, original filter displayed |
| L02 | Select Português (BR) | Filter becomes `0-1ataque&3-4defesa&3-4ps` |
| L03 | Tap Copiar filtro e continuar | Clipboard contains exact localized string; journey moves to review |
| L04 | Paste in Pokémon GO in pt-BR | Query accepted; known match appears if one exists |
| L05 | Return to filter, tap Poucos ou zero | Portuguese broad scope: `0-2ataque&2-4defesa&2-4ps` |
| L06 | Tap Demasiados twice | Moves broad → standard → focused, no skipped level |
| L07 | Tap Voltar ao filtro padrão | Standard filter restored |
| L08 | Switch to English | Query becomes `0-1attack&3-4defense&3-4hp` |
| L09 | Paste in Pokémon GO in English | Query accepted; known match appears if one exists |
| L10 | Switch to Eventos with PT-BR selected | Standard `idade0-7`; broad `idade0-30`; focused `idade0-1` |
| L11 | Switch to Master | `4*` remains unchanged; standard cannot narrow further |
| L12 | Switch to Coleção | `3*,4*`, broad `0-4*`, focused `4*` |
| L13 | Change goal after adjusting a scope | Each goal retains its own stage/scope; language remains shared |
| L14 | Force-stop and reopen the Companion | Language and per-goal stage/scope persist |
| L15 | Empty Companion Inbox; Pokémon GO contains captures | UI distinguishes local Inbox from GO results; no false zero-collection claim |
| L16 | Test compact Android display (≤ 360dp), large fonts, TalkBack | Language controls readable, tappable and not clipped; 48dp touch target |
| L17 | Switch language without copying | Must not advance the journey or alter Room, collection or teams |
| L18 | Review candidate manually and proceed | No automatic IV rank or eligibility approval, no Pokémon GO login |
| L19 | GO using another Portuguese regional setting | Verify exact search vocabulary before claiming compatibility |
| L20 | Copy same search with known zero matches | Clear warning: zero matches does not mean no useful PvP candidates |

## Release gates
- [ ] Android CI build and policy unit tests green for this PR
- [ ] All L01–L20 passed or recorded with a bug identifier
- [ ] Tests in both supported GO languages on a device
- [ ] Verify no clipped layout/accessibility regressions
- [ ] Do not count Android E2E coverage until tests run
