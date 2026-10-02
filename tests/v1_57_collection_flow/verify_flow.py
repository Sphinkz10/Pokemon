#!/usr/bin/env python3
"""V1.57 static flow guards. These do not replace device UX tests."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
read=lambda name:(base/name).read_text(encoding="utf-8")
core=read("CoreScreens.kt")
listui=read("CollectionOverviewScreen.kt")
validation=read("OwnedBuildValidation.kt")
junit=(root/"app/src/test/java/com/rui/pvpgo/OwnedBuildValidationTest.kt").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")
checks={
    "list state saved when navigating": 'rememberSaveableStateHolder()' in core and 'SaveableStateProvider("collection-list")' in core,
    "current route is restorable": "var route by rememberSaveable" in core,
    "query remains after opening details": 'var query by rememberSaveable' in listui,
    "tabs persist": 'var tab by rememberSaveable' in listui,
    "filters persist": 'var filter by rememberSaveable' in listui,
    "generation persists": 'var generation by rememberSaveable' in listui,
    "sorting persists": 'var sortByPriority by rememberSaveable' in listui,
    "selection uses numeric dex only": 'var selectedDex by rememberSaveable' in listui,
    "collection and Dex keep distinct scroll states": "collectionScroll = rememberLazyListState()" in listui and "nationalScroll = rememberLazyListState()" in listui,
    "back from Dex clears selection": "onBack = { selectedDex = null }" in listui,
    "edit changes detected": "val hasUnsavedBuild =" in core and "val hasUnsavedPlan =" in core,
    "toolbar back protected": "onClick = exitDetail" in core,
    "system back protected": "BackHandler { exitDetail() }" in core,
    "discard requires explicit confirmation": "showDiscardConfirm" in core and "Descartar alterações" in core,
    "known moves checked on save": "OwnedBuildValidation.movePoolError(" in core,
    "legacy attacks preserved": "previousChargedIds" in validation and "previousFastId" in validation,
    "unloaded pool not treated authoritative": "allowedChargedIds.isNotEmpty()" in validation and "allowedFastIds.isNotEmpty()" in validation,
    "no fake verification timestamp for unknown fields": "lastVerifiedAtEpochMs = if (uncertain.isEmpty()) now else null" in core,
    "confirmation messaging remains honest": "Campos incompletos continuam por confirmar" in core or "campos incompletos continuam por confirmar" in core,
    "unit tests include all three move pool cases": all(q in junit for q in [
        "fun newMoveMustExistWhenCatalogKnown()",
        "fun legacyEventMovesRemainIntactWithoutBeingOfferedAsNew()",
        "fun unavailableCatalogDoesNotInventMoveRestrictions()"
    ]),
    "version 57 or newer": int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1)) >= 57,
    "independent installer": re.search(r'applicationIdSuffix\s*=\s*"\.installtestv\d+"', gradle) is not None
}
for name,ok in checks.items():
    print(("PASS" if ok else "FAIL"), name)
print(f"COLLECTION_FLOW {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()), "Collection flow or data integrity regression"
