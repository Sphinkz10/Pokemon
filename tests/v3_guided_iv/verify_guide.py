#!/usr/bin/env python3
"""Guard the collection's honest, three-phase IV evaluation journey."""
from pathlib import Path

root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
policy=(base/"CollectionGuidePolicy.kt").read_text()
screen=(base/"CollectionOverviewScreen.kt").read_text()
jvm=(root/"app/src/test/java/com/rui/pvpgo/CollectionGuidePolicyTest.kt").read_text()
instrumented=(root/"app/src/androidTest/java/com/rui/pvpgo/CollectionGuidedIvAndroidTest.kt").read_text()
checks={
    "three explicit progression steps plus all": all(x in policy for x in [
        "REVIEW(", "COMPARE(", "PLAN(", "ALL("
    ]),
    "explicit unknown data routing": all(x in policy for x in [
        "owned.cp == null", "owned.level == null", "owned.uncertainFields.isNotEmpty()"
    ]),
    "comparison requires catalog knowledge": "speciesExistsInCatalog &&" in policy,
    "comparison never judges 0/15/15 as universally good":
        "não é garantia" in policy and "rank" in policy,
    "saved plans are not made up": "CollectionGuideStep.PLAN -> hasSavedPlan" in policy,
    "step persisted by Compose": "var guidedStep by rememberSaveable" in screen,
    "prior search and other existing filters unchanged": all(x in screen for x in [
        "var query by rememberSaveable", "var filter by rememberSaveable",
        "CollectionFilterRow(filter", "NationalDexPolicy.filter("
    ]),
    "selected step is part of actual list predicate":
        "CollectionGuidePolicy.matches(" in screen and
        "guidedStep, owned" in screen,
    "compare opens the EXISTING IV target tool":
        "onOpenIvTargets(owned.speciesId)" in screen and
        "CollectionGuidePolicy.canOpenIvComparison(" in screen,
    "guide offers discoverable selected controls": all(x in screen for x in [
        'testTag("collection-guide-${step.name.lowercase()}")',
        'testTag("collection-guide-explanation")',
        'testTag("collection-guide-count")',
    ]),
    "unit cases include unknowns and non-universal IV rules":
        jvm.count("@Test fun ") >= 9 and
        "zeroAttackDoesNotBecomeAnAutomaticQualityDecision" in jvm,
    "real Android tests navigate filters and use IV target callback":
        instrumented.count("@Test fun ") >= 3 and
        'collection-guide-open-iv-iv-ready' in instrumented and
        'assertEquals(species.speciesId, openedSpeciesId)' in instrumented,
}
for key,ok in checks.items():
    print(("PASS " if ok else "FAIL ")+key)
print(f"GUIDED_IV_SOURCE {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()),"Invalid user-guided collection IV flow"
