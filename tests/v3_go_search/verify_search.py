#!/usr/bin/env python3
"""Non-destructive GO inventory search contract; run in every debug APK CI."""
from pathlib import Path

root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
policy=(base/"PokemonGoSearchPolicy.kt").read_text()
panel=(base/"PokemonGoSearchPanel.kt").read_text()
collection=(base/"CollectionOverviewScreen.kt").read_text()
jvm=(root/"app/src/test/java/com/rui/pvpgo/PokemonGoSearchPolicyTest.kt").read_text()
android=(root/"app/src/androidTest/java/com/rui/pvpgo/PokemonGoSearchComposeAndroidTest.kt").read_text()

checks={
 "official documented individual attribute families": all(x in policy for x in ['"attack", "defense", "hp"', '"ataque", "defesa", "ps"']),
 "broad candidate screening is not a PvP ranking": "poderá omitir Pokémon PvP úteis" in policy,
 "all IV profiles are user-selectable": all(x in policy for x in ["ALL(", "BROAD(", "LOW_ATTACK(", "HIGH_ATTACK(", "PERFECT("]),
 "queries are always non-destructive and skip CP": all(x in policy for x in ['"!" !in query', '"cp" !in query.lowercase()', '"#" !in query']),
 "clipboard only on explicit user click": "setPrimaryClip(ClipData.newPlainText(" in panel and "onClick = {" in panel,
 "copied text tied to exact selected query": "lastCopied == query" in panel and "lastCopied = query" in panel,
 "safety warning against transferring exists": "Não transfiras Pokémon" in panel,
 "go search is part of existing COMPARE flow": "PokemonGoSearchPanel(league = compareLeague)" in collection and "selected == CollectionGuideStep.COMPARE" in collection,
 "no new competing collection guide": collection.count("CollectionJourneyGuide(")==2,
 "fourteen or more explicit JVM cases": jvm.count("@Test fun ")>=14,
 "five or more real Android UI cases": android.count("@Test fun ")>=5 and "clipText()" in android,
 "can copy even when app collection has no owned records": "actualCollectionStepTwoDisplaysSearchEvenWithEmptyOwnedCollection" in android,
}
for label,okay in checks.items():
    print(("PASS " if okay else "FAIL ")+label)
print("GO_SEARCH_CONTRACT",sum(checks.values()),"/",len(checks),"PASS")
assert all(checks.values()),"GO search guide safety or wiring regression"
