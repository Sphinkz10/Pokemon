#!/usr/bin/env python3
"""Static regression gate for skin preferences and the event feed. Not an on-device test."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[2]
def file(path):
    return (root / path).read_text(encoding="utf-8")
skin = file("app/src/main/java/com/rui/pvpgo/ui/theme/PvpSkins.kt")
theme = file("app/src/main/java/com/rui/pvpgo/ui/theme/PvpTheme.kt")
main = file("app/src/main/java/com/rui/pvpgo/MainActivity.kt")
more = file("app/src/main/java/com/rui/pvpgo/CoreScreens.kt")
today = file("app/src/main/java/com/rui/pvpgo/TodayScreen.kt")
events = file("app/src/main/java/com/rui/pvpgo/events/EventCalendarRepository.kt")
screens = file("app/src/main/java/com/rui/pvpgo/events/EventCalendarScreens.kt")
build = file("app/build.gradle.kts")
checks = {
    "four distinct skin identifiers": all(x in skin for x in ['DEEP("deep"', 'AMOLED("amoled"', 'MYSTIC("mystic"', 'CLASSIC("classic"']),
    "all four skin palettes provided": all(x in skin for x in ["val deep =", "val amoled =", "val mystic =", "val classic ="]),
    "stored choice has safe fallback": "?: DEEP" in skin,
    "stored choice is persisted": "getSharedPreferences" in skin and "putString(SELECTED_SKIN" in skin,
    "material scheme uses active skin": "pvpColorScheme(palette, skin.isLight)" in theme,
    "reactive semantic colors": "mutableStateOf(PvpSkin.DEEP)" in theme and "palette.canvasStart" in theme,
    "skin loaded on launch": "PvpColors.useSkin(PvpSkinPreferences.load(this))" in main,
    "skin saved on user choice": "PvpSkinPreferences.save(context, chosen)" in main,
    "More tab opens Appearance": "MoreRoute.APPEARANCE -> AppearanceScreen" in more,
    "four theme choices listed": "items(PvpSkin.entries" in more,
    "Today uses semantic skin tokens": "private val TodayBlue: Color get() = PvpColors.AccentSky" in today,
    "source explicitly community": "third-party Leek Duck" in events,
    "feed fetches structured events": "events.json" in events and "JSONObject(json)" in events,
    "mixed local and absolute timestamps": "LocalDateTime.parse(value)" in events and "epochMillis(value.toLong())" in events,
    "feed discards wrong url": 'link.startsWith("https://leekduck.com/")' in events,
    "feed excludes expired events": "end < now" in events,
    "cache stale threshold": "STALE_AFTER_MS" in events,
    "offline fallback provided": "a mostrar cache" in events,
    "periodic worker scheduled": "enqueueUniquePeriodicWork" in events and "EventCalendarRepository.schedule(this)" in main,
    "Home reads actual feed": "calendar = eventCalendar" in main and "calendar.active().isNotEmpty()" in today,
    "calendar screens show community attribution": "fonte comunitária NÃO oficial" in screens,
    "user can refresh": "onRefresh" in screens and "onRefreshCalendar" in more,
    "version updated": re.search(r'versionCode\s*=\s*(\d+)', build) is not None and int(re.search(r'versionCode\s*=\s*(\d+)', build).group(1)) >= 53,
}
for title, ok in checks.items(): print(("PASS" if ok else "FAIL") + " - " + title)
total=sum(checks.values());print(f"SKIN_EVENTS {total}/{len(checks)} PASS")
assert total == len(checks), "Regression gate failed"
