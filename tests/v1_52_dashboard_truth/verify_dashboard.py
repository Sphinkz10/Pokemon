#!/usr/bin/env python3
"""V1.52 CI gate: avoid representing illustrative event/IV data as real."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
today = (root / "app/src/main/java/com/rui/pvpgo/TodayScreen.kt").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/rui/pvpgo/MainActivity.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
checks = {
    "catalogue count comes from loaded species": "speciesCount = catalog.size" in today,
    "catalogue count displayed": "$speciesCount espécies disponíveis" in today,
    "event feed is explicitly unsynced": "AGENDA NÃO SINCRONIZADA" in today,
    "event page includes missing-feed state": "SEM FONTE DE EVENTOS" in today,
    "agenda includes empty state": "Sem calendário oficial sincronizado" in today,
    "catalogue has accessible action": "onOpenCollection = onSearch" in today,
    "event and agenda can be opened from home": "onExploreEvent =" in main and "onOpenAgenda =" in main,
    "versionCode 52": "versionCode = 52" in gradle,
    "versionName V1.52": 'versionName = "1.52.0-dev"' in gradle,
    "old invented live event removed": "Harvest Festival" not in today,
    "old invented countdown removed": "3h 42m" not in today,
    "fixed Spotlight Hour removed": "Spotlight Hour" not in today,
    "fixed Raid Hour removed": "Raid Hour" not in today,
    "personal targets labelled as references": "ESPÉCIES DE REFERÊNCIA" in today,
}
for description, passed in checks.items():
    print(("PASS" if passed else "FAIL") + " " + description)
assert all(checks.values()), "V1.52 dashboard data-integrity policy failed"
print(f"{sum(checks.values())}/{len(checks)} PASS")
