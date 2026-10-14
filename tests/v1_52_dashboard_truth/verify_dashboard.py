#!/usr/bin/env python3
"""Dashboard integrity gate: no fabricated events, catalog counts or local player priorities."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
today = (root / "app/src/main/java/com/rui/pvpgo/TodayScreen.kt").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/rui/pvpgo/MainActivity.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
event_screens = (root / "app/src/main/java/com/rui/pvpgo/events/EventCalendarScreens.kt").read_text(encoding="utf-8")
checks = {
    "catalogue count comes from loaded species": "speciesCount = catalog.size" in today,
    "catalogue count displayed": "$speciesCount espécies disponíveis" in today,
    "event feed has attribution": "Leek Duck" in today,
    "event page includes missing-feed state": "Sem ligação à fonte" in event_screens,
    "agenda includes empty state": "Nenhum evento sincronizado" in event_screens,
    "catalogue has accessible action": "onOpenCollection = onSearch" in today,
    "event and agenda can be opened from home": "onExploreEvent =" in main and "onOpenAgenda =" in main,
    "versionCode at least 52": int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1)) >= 52,
    "versionName V1.52+": re.search(r'versionName\s*=\s*"1\.(\d+)\.', gradle) is not None,
    "old invented live event removed": "Harvest Festival" not in today,
    "old invented countdown removed": "3h 42m" not in today,
    "fixed Spotlight Hour removed": "Spotlight Hour" not in today,
    "fixed Raid Hour removed": "Raid Hour" not in today,
    "home priorities come from all three Room streams": (
        "combine(repository.collection, repository.pendingInbox, repository.savedTeams)" in today
        and "TodayActionPolicy.decide(" in today
    ),
    "home waits for loaded Room data rather than inventing an empty collection": (
        "actionsFlow.collectAsState(initial = emptyList())" in today
        and "A preparar as tuas ações" in today
    ),
    "home action cards navigate to existing Collection, Teams and Radar": (
        "TodayDestination.COLLECTION -> onSearch()" in today
        and "TodayDestination.TEAMS -> onOpenTeams()" in today
        and "TodayDestination.RADAR -> onOpenRadar()" in today
    ),
    "home no longer displays fixed species as personal priorities": (
        "ESPÉCIES DE REFERÊNCIA" not in today
        and "CarbinkPriorityCard(" not in today.split("private fun TodayActionCard")[0]
    ),
    "goal picker shows four distinct objectives and keeps accessible chips": (
        "TodayGoalPicker(selectedGoal)" in today
        and "FilterChip(" in today
        and "TodayGoal.GREAT" in today
        and "TodayGoal.ULTRA" in today
        and "TodayGoal.MASTER" in today
        and "TodayGoal.IVS" in today
        and "TodayGoal.COLLECTION" in today
        and "TodayGoal.EVENTS" in today
        and "heightIn(min = 48.dp)" in today
    ),
    "goal selection is persisted locally and recomputes Room actions": (
        'getSharedPreferences("companion_today_goal_v1"' in today
        and 'getString("goal", TodayGoal.COLLECTION.name)' in today
        and 'putString("goal", nextGoal.name).apply()' in today
        and "remember(repository, calendar, selectedGoal)" in today
    ),
    "guided journey is visible after choosing objective": (
        "TodayJourneyPanel(" in today
        and "goal = selectedGoal" in today
        and "onOpenCollection = onOpenCandidateInbox" in today
        and "onOpenCandidateInbox = {" in main
        and "inboxRequestCounter = guidedInboxRequest" in main
        and "route = CollectionRoute.INBOX" in
        (root / "app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text(encoding="utf-8")
    ),
    "GO copy journey keeps progress per goal and no silent import": (
        'getSharedPreferences("companion_today_journey_v1"' in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and '"stage_" + goal.name' in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and "clipboard.setText(AnnotatedString(suggestion.filter))" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and "Já revi os candidatos" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
    ),
    "journey Inbox status is driven by observed Room emissions": (
        "repository.pendingInbox.map { it.size }" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and "collectAsState(initial = -1)" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and "inboxStatus(pendingCount.takeIf { it >= 0 })" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and "A Inbox local e os resultados da pesquisa no Pokémon GO são distintos" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
    ),
    "manual broadening/refinement is visible with reversible standard option": (
        "TodayJourneyPolicy.scopedSuggestion(goal, scope)" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and "Poucos ou zero" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and "Demasiados" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and "Voltar ao filtro padrão" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
        and 'putString(scopeKey, next.name).apply()' in
        (root / "app/src/main/java/com/rui/pvpgo/TodayJourneyPanel.kt").read_text(encoding="utf-8")
    ),
    "new action policy warns rather than asserting battle readiness": (
        "elegibilidade PvP ainda terá de ser confirmada" in
        (root / "app/src/main/java/com/rui/pvpgo/TodayActionPolicy.kt").read_text(encoding="utf-8")
    ),
}
for description, passed in checks.items():
    print(("PASS" if passed else "FAIL") + " " + description)
assert all(checks.values()), "V1.52 dashboard data-integrity policy failed"
print(f"{sum(checks.values())}/{len(checks)} PASS")
