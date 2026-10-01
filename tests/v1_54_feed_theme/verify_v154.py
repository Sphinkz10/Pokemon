#!/usr/bin/env python3
"""V1.54 proof gates: local source guards + optional live provider schema probe."""
import json
import re
import sys
from pathlib import Path
from datetime import datetime
from urllib.request import Request, urlopen
from urllib.error import URLError

ROOT = Path(__file__).resolve().parents[2]
def read(path):
    return (ROOT / path).read_text(encoding="utf-8")
today = read("app/src/main/java/com/rui/pvpgo/TodayScreen.kt")
repo = read("app/src/main/java/com/rui/pvpgo/events/EventCalendarRepository.kt")
skins = read("app/src/main/java/com/rui/pvpgo/ui/theme/PvpSkins.kt")
gradle = read("app/build.gradle.kts")
checks = {
  "V1.54 Android version": 'versionCode = 54' in gradle and 'versionName = "1.54.0-dev"' in gradle,
  "today hero uses skin background": "colors = listOf(PvpColors.SurfaceRaised, PvpColors.CanvasMiddle, PvpColors.CanvasStart)" in today,
  "today key colors are semantic": "PvpColors.BorderDefault" in today and "PvpColors.AccentDeep" in today,
  "event title capped to two lines": "maxLines = 2" in today and "TextOverflow.Ellipsis" in today,
  "event CTA touch area >=48 dp": ".heightIn(min = 48.dp)" in today,
  "invalid calendar rejects provider schema": 'require(recognizedEntries > 0)' in repo,
  "URL and title validated before count": repo.index('if (!link.startsWith("https://leekduck.com/")) continue') < repo.index("recognizedEntries++"),
  "failed refresh retains previously known cache": 'snapshot(cached!!, last, now).copy(' in repo,
  "seconds, millis, numeric strings accepted": all(t in repo for t in ['epochMillis(value.toLong())', 'value > 100_000_000_000L']),
  "local and timezone-aware datetimes": all(t in repo for t in ["LocalDateTime.parse(value)", "OffsetDateTime.parse(value)"]),
  "four theme skins still supported": all(x in skins for x in ('DEEP("deep"', 'AMOLED("amoled"', 'MYSTIC("mystic"', 'CLASSIC("classic"')),
}
for label, passed in checks.items():
    print(("PASS" if passed else "FAIL"), label)
print(f"{sum(checks.values())}/{len(checks)} V1.54 static checks passed")
assert all(checks.values()), "A V1.54 tem uma regressão na fonte dos eventos ou nas skins."

if "--live" in sys.argv:
    url = "https://raw.githubusercontent.com/zhenga8533/leak-duck/data/events.json"
    try:
        request = Request(url, headers={"Accept": "application/json", "User-Agent": "PokemonPvP-V154-CI/1.0"})
        with urlopen(request, timeout=15) as response:
            data = response.read(2_000_001)
        if len(data) > 2_000_000:
            raise ValueError("Response exceeds current app limit")
        root = json.loads(data.decode("utf-8"))
        if not isinstance(root, dict):
            raise ValueError("Expected top-level dict")
        valid = []
        for category, records in root.items():
            if not isinstance(records, list):
                continue
            for event in records:
                if not isinstance(event, dict):
                    continue
                start, end = event.get("start_time"), event.get("end_time")
                times_ok = isinstance(start, (int, float)) and isinstance(end, (int,float))
                times_ok = times_ok or (isinstance(start,str) and isinstance(end,str))
                if (times_ok and str(event.get("article_url","")).startswith("https://leekduck.com/")
                    and event.get("title") and isinstance(event.get("is_local_time"), bool)):
                    valid.append((category,event.get("title")))
        if not valid:
            raise ValueError("No valid structured events in provider payload")
        print(f"LIVE VERIFIED: {len(valid)} structured events in {len(root)} categories.")
    except (URLError, TimeoutError, OSError) as err:
        print(f"LIVE UNKNOWN (network unavailable): {type(err).__name__}; local checks passed.")
        # A temporary provider outage cannot block a locally installable APK.
