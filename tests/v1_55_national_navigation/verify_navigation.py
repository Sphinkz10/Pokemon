#!/usr/bin/env python3
"""Structural policy checks for the Android Collection/Pokédex wiring.
The policy rules themselves are covered by JUnit NationalDexPolicyTest.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
src = root/"app/src/main/java/com/rui/pvpgo"
read = lambda name: (src/name).read_text(encoding="utf-8")
index = read("CollectionOverviewScreen.kt")
detail = read("NationalDexDetailScreen.kt")
core = read("CoreScreens.kt")
species = read("SpeciesCollectionScreens.kt")
policy = read("NationalDexPolicy.kt")
workflow = (root/".github/workflows/android-debug-apk.yml").read_text(encoding="utf-8")
build = (root/"app/build.gradle.kts").read_text(encoding="utf-8")

checks = {
 "all national entries openable": "onClick = { selectedDex = entry.dex }" in index,
 "list includes dedicated national route": "selectedNational?.let { entry ->" in index and "national?.entries?.firstOrNull { it.dex == selectedDex }" in index and "NationalDexDetailScreen(" in index,
 "national detail has route back": "onBack = { selectedDex = null }" in index,
 "entry does not require battle record": "battleAvailable = battleSpecies != null" in index and "onClick = battleSpecies?.let" not in index,
 "PvP actions only if verified": "onOpenIvTargets = battleSpecies?.let" in index,
 "owned actions require actual owned record": "onOpenOwned = firstOwnedId?.let" in index,
 "profile shows real national identifier": 'entry.dex.toString().padStart(4' in detail,
 "profile discloses source": "indexSource" in detail and "FONTE E LIMITES" in detail,
 "no fake PvP details": "Não inventamos esses dados" in detail,
 "detail does not auto-mutate collection": "CollectionRepository" not in detail and "delete" not in detail,
 "return routes declared": all(x in core for x in ("detailReturnRoute", "compareReturnRoute", "targetsReturnRoute")),
 "owned detail remembers actual origin": "onBack = { route = detailReturnRoute }" in core,
 "compares return to actual origin": "onBack = { route = compareReturnRoute }" in core,
 "targets return to actual origin": "onBack = { route = targetsReturnRoute }" in core,
 "national Dex targets return to list": "targetsReturnRoute = CollectionRoute.LIST" in core,
 "search handles zero-padded numeric identifiers": "q.toIntOrNull()" in policy and "entry.dex == exactDex" in policy,
 "small screen filters scroll horizontally": "Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())" in index,
 "all Collection semantic colors": "Color(0xFF" not in index,
 "all Species360 semantic colors": "Color(0xFF" not in species,
 "real JVM policy unit tests": ":app:testDebugUnitTest" in workflow and "testImplementation(\"junit:junit:4.13.2\")" in build,
 "collection stats derive from actual owned records": "CollectionStatsStrip(collection)" in index and "owned.map { it.speciesId }.distinct().size" in index and "owned.count { it.isShiny }" in index and "it.iv.total == 45" in index,
 "independent APK suffix remains present": re.search(r'applicationIdSuffix\s*=\s*"\.installtestv\d+"', build) is not None,
}
for name, yes in checks.items():
    print(("PASS" if yes else "FAIL"), name)
print(f"NATIONAL_NAVIGATION {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()), "Pokédex navigation or skin tokens regressed"
