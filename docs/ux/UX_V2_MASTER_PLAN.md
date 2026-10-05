# Pokémon Companion — UX V2 Master Plan

Status: ACTIVE
Scope: Figma page `14 — App atual · igual ao código (2.0)` + Android implementation
Branch: `ux-v2-navigation-foundation`

## Product promise

The app should behave as a companion, not a toolbox:

> Know what the player has, understand what is happening, recommend the next useful action, and expose deeper tools without making the user hunt for them.

## Non-negotiable quality gates

1. **Orientation** — every screen explains where the user is and preserves the origin of the journey.
2. **Discoverability** — high-value actions must be reachable contextually; `Mais` may be a directory but not the only discovery path.
3. **Decision-first hierarchy** — answer "what should I do?" before exposing diagnostics and detail.
4. **No dead ends** — every reachable state supports completion, continuation, back, cancel, retry, or recovery.
5. **State resilience** — loading, empty, stale, offline, partial, error and permission-denied states are intentional product states.
6. **Semantic design system** — action, selection, status, focus and outline roles are not encoded by one overloaded physical color.
7. **Evidence** — a feature is not done because it compiles; it needs automated evidence and, for core journeys, observed user evidence.

## Core mental model

| Area | Product contract |
|---|---|
| Hoje | Tell me what is most useful now. |
| Coleção | What do I have, and is it worth keeping/improving? |
| Equipas | Who should I play with? |
| Batalhas | Play, log and learn from real battles. |
| Mais | Secondary tools, planning, configuration and diagnostics. |

## Eight critical user journeys

1. Decide whether to keep a Pokémon just caught.
2. Find which Pokémon is missing for a league/cup.
3. Build a team from the owned collection.
4. Understand repeated losses to a common opponent.
5. Create a Radar alert for a PvP target.
6. Import screenshots and resolve uncertain readings.
7. Log a battle quickly during a session.
8. Finish a session and choose the next improvement.

## Delivery phases

| Phase | Goal | Exit gate |
|---|---|---|
| P0 | Canonical route/state inventory | Every product screen has owner, entry, exit and evidence source. |
| P1 | Navigation foundation | Root navigation is typed; deep journeys preserve origin; manual tab teleport is removed. |
| P2 | UDF/state holders | Feature UI can render from explicit immutable state. |
| P3 | IA + vocabulary | Core tasks have one canonical destination and terminology. |
| P4 | Design System 2.0 | Semantic roles + contrast/touch/focus tests across all skins. |
| P5 | Hoje 2.0 | First viewport answers now / next / urgent. |
| P6 | Coleção | Progressive disclosure + status taxonomy + preserved filter context. |
| P7 | Equipas | Builder → results → analysis → save is fully reversible. |
| P8 | Batalhas | Session/log/history/learning are coherent and fast. |
| P9 | Secondary ecosystem | Radar/events/bubble/settings are discoverable in context. |
| P10 | Resilience | State matrix and No Dead End gate pass. |
| P11 | Accessibility/adaptive | TalkBack, Switch Access, font scale, insets and compact widths pass. |
| P12 | Performance/offline | Critical journeys benchmarked; offline and recovery are measured. |
| P13 | Product proof | Automated regression + qualitative/quantitative UX validation. |

## Implementation order

Do not redesign feature screens on top of the current root router.

1. Navigation contract and typed destinations.
2. Root navigation migration.
3. Cross-feature deep destinations (Events, Agenda, Radar, Pokémon).
4. State holders and route-local saved state.
5. IA/vocabulary changes.
6. Visual hierarchy and semantic design system.
7. Feature-by-feature redesign.
8. failure/recovery states.
9. accessibility/performance.
10. user validation.

## Definition of done for a feature

A feature is complete only when applicable gates are evidenced:

- happy path;
- empty state;
- loading state;
- offline/stale state;
- recoverable error;
- destructive action recovery/confirmation;
- permission denial path;
- visible/system Back;
- state restoration after recreation;
- TalkBack semantics;
- touch targets;
- compact-width layout;
- all supported skins;
- screenshot regression;
- performance on a representative data set;
- route test;
- no invented/fake user data presented as real.

## Current high-severity UX findings

- Today/Plan/Events/Radar/Data Health overlap in "what should I do?" responsibility.
- Today currently navigates to secondary tools by changing the selected root tab to More.
- High-value features are discoverable primarily through More.
- Team/result analysis paths do not consistently expose contextual Back.
- Collection status badges mix data quality, readiness, competitive quality and traits.
- Repeated concepts use inconsistent labels.
- Some physical colors represent too many semantic roles.
- failure/offline/permission states are not covered systematically.

This document is the master implementation contract. Detailed route, state and vocabulary contracts live beside it.
