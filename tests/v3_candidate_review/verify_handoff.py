#!/usr/bin/env python3
"""V3 candidate review wiring checks; Android device acceptance remains separate."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
base = root / "app/src/main/java/com/rui/pvpgo"
core = (base / "CoreScreens.kt").read_text(encoding="utf-8")
policy = (base / "CandidateReviewPolicy.kt").read_text(encoding="utf-8")
nav = (base / "CollectionNavigationPolicy.kt").read_text(encoding="utf-8")
store = (base / "CollectionDatabase.kt").read_text(encoding="utf-8")
junit = (root / "app/src/test/java/com/rui/pvpgo/CandidateReviewPolicyTest.kt").read_text(encoding="utf-8")

checks = {
    "Inbox can start quick manual capture without leaving for import home":
        "onQuickAdd = {" in core and "manualReturnRoute = CollectionRoute.INBOX" in core,
    "Inbox can start guided manual capture":
        "onGuidedAdd = {" in core and "route = CollectionRoute.MANUAL_GUIDED" in core,
    "manual capture returns to its entry route on Android Back":
        "importOrigin = manualReturnRoute" in core and "-> importOrigin" in nav,
    "Inbox waits for first Room emission before announcing zero candidates":
        "var pendingLoaded by remember" in core and
        "pendingLoaded = true" in core and
        "if (!pendingLoaded)" in core,
    "empty Inbox explains Pokémon GO results are not imported":
        "CandidateReviewPolicy.EMPTY_GUIDANCE" in core and "não envia capturas" in policy,
    "Inbox offers on-demand rank preview per candidate":
        "if (expandedCandidateId == item.id)" in core and
        "CandidateRankPreview(" in core and
        "candidate = c" in core and
        "collectionLoaded = collectionLoaded" in core,
    "IV rank calculation is on background dispatcher":
        "withContext(Dispatchers.Default)" in core.split("private fun CandidateRankPreview(")[1].split("private fun OwnedPokemonDetailScreen(")[0],
    "IV preview uses engine rank and the original IV":
        "species, league, candidate.iv, RankSettings()" in core,
    "preview supports all four leagues":
        "SpeciesLeaguePolicy.selectable.forEach" in core.split("private fun CandidateRankPreview(")[1].split("private fun OwnedPokemonDetailScreen(")[0],
    "rank is never presented as species meta or transfer permission":
        "Não mede a força no meta" in policy and "não confirma a elegibilidade" in policy
        and "transferência" in policy,
    "manual IV capture starts unknown and requires explicit valid values":
        "var atk by remember { mutableStateOf<Int?>(null) }" in core
        and "var def by remember { mutableStateOf<Int?>(null) }" in core
        and "var hp by remember { mutableStateOf<Int?>(null) }" in core
        and "CandidateReviewPolicy.hasExplicitIvs(atk, def, hp)" in core
        and "CandidateReviewPolicy.updateIvInput(value, it)" in core,
    "new confirmation is an explicit user action":
        "repository.confirmNew(item.id)" in core and "onConfirmed(c)" in core,
    "Room confirmation is transactional and persists the owned id":
        "suspend fun confirmNew(inboxId: String)" in store
        and "db.withTransaction {" in store
        and "linkedOwnedPokemonId = owned.id" in store,
    "detail navigation waits for confirmed owned record emission":
        "awaitingConfirmedOwnedId" in core
        and "CandidateReviewPolicy.canOpenConfirmed" in core
        and "collection.mapTo(mutableSetOf()) { it.id }" in core,
    "confirm and discard buttons have full-width touch targets":
        "CandidateReviewPolicy.saveAction" in core and "heightIn(min = 48.dp)" in core,
    "new JVM test suite covers ranked, unranked and missing records":
        junit.count("@Test fun ") >= 12 and "confirmedCandidateWaitsForRoomToEmitExactOwnedId" in junit,
}
for title, passed in checks.items():
    print(("PASS" if passed else "FAIL") + " " + title)
assert all(checks.values()), "Candidate review integrity regression"
print(f"CANDIDATE_REVIEW {sum(checks.values())}/{len(checks)} PASS")
