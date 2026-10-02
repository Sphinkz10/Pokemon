#!/usr/bin/env python3
"""Static lifecycle guard for Android Compose UI routing (device test still required)."""
from pathlib import Path
import re

root=Path(__file__).resolve().parents[2]
src=root/"app/src/main/java/com/rui/pvpgo"
get=lambda f:(src/f).read_text(encoding="utf-8")
main=get("MainActivity.kt")
core=get("CoreScreens.kt")
policy=get("CollectionNavigationPolicy.kt")
overview=get("CollectionOverviewScreen.kt")
test=(root/"app/src/test/java/com/rui/pvpgo/CollectionNavigationPolicyTest.kt").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

checks={
  "saveable top-level active tab": "var tab by rememberSaveable" in main,
  "saveable return tab": "var selectedReturnTab by rememberSaveable" in main,
  "tab state holder wraps routes": "tabsState.SaveableStateProvider(tab.name)" in main,
  "collection route saves across rotation": "var route by rememberSaveable" in core,
  "collection list has dedicated saveable holder": 'SaveableStateProvider("collection-list")' in core,
  "selected species ID saved": "var selectedSpeciesId by rememberSaveable" in core,
  "selected exemplar ID saved": "var selectedOwnedId by rememberSaveable" in core,
  "first comparison ID saved": "var compareFirstId by rememberSaveable" in core,
  "second comparison ID saved": "var compareSecondId by rememberSaveable" in core,
  "league selection saved": "var speciesLeague by rememberSaveable" in core,
  "owned origin saved": "var detailReturnRoute by rememberSaveable" in core,
  "comparison origin saved": "var compareReturnRoute by rememberSaveable" in core,
  "IV targets origin saved": "var targetsReturnRoute by rememberSaveable" in core,
  "first Room emission tracked": "collectionLoaded = true" in core,
  "record existence checked only after Room emission": "if (collectionLoaded) route = CollectionRoute.LIST" in core,
  "comparison waits for Room and catalog": "if (collectionLoaded && catalog.isNotEmpty())" in core,
  "species route waits for catalog": core.count("CollectionNavigationPolicy.shouldFallbackMissingSpecies(catalog.isNotEmpty())") >= 3,
  "fallback readiness is tested in JVM": "missingSpeciesWaitsForCatalogBeforeFallback" in test and "fun shouldFallbackMissingSpecies(catalogLoaded: Boolean)" in policy,
  "system back wired to typed navigation policy": "CollectionNavigationPolicy.backTarget(" in core and "BackHandler(enabled = route" in core,
  "unsaved edit back handler remains authoritative": "BackHandler { exitDetail() }" in core,
  "single typed route declaration": core.count("enum class CollectionRoute") == 0 and "internal enum class CollectionRoute" in policy,
  "collection query still saved": "var query by rememberSaveable" in overview,
  "National Dex scroll retained": "nationalScroll = rememberLazyListState()" in overview,
  "seven pure navigation unit cases": test.count("@Test fun ") >= 7,
  "v158 version": 'versionCode = 58' in gradle and 'versionName = "1.58.0-dev"' in gradle,
  "isolated diagnostic test package": 'applicationIdSuffix = ".installtestv190"' in gradle,
}
for name, ok in checks.items():
  print(("PASS" if ok else "FAIL"), name)
print(f"LIFECYCLE {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()), "Lifecycle/navigation regression"
