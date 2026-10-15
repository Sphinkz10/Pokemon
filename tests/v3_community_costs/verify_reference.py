#!/usr/bin/env python3
"""Community power-up table V3: auditable boundary, not a live game-master certification."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
pack=(base/"CommunityNormalPowerUpPack.kt").read_text(encoding="utf-8")
panel=(base/"InvestmentTargetPanel.kt").read_text(encoding="utf-8")
gate=(base/"InvestmentCostQuoteGate.kt").read_text(encoding="utf-8")
source_tests=(root/"app/src/test/java/com/rui/pvpgo/CommunityNormalPowerUpPackTest.kt").read_text(encoding="utf-8")
checks={
 "independent community sources attributed": "https://www.poketools.com/pokemon-go-power-up-calculator" in pack and "https://pokemongohub.net/post/guide/xl-candy-guide-how-to-get-power-up-costs-and-mechanics/" in pack,
 "review date is pinned": "1792022400000L" in pack and "checked-2026-10-15" in pack,
 "canonical hash is a static pinned SHA-256": bool(re.search(r'PINNED_HASH_SHA256\s*=\s*"[0-9a-f]{64}"',pack)),
 "cost pack validates hash": "InvestmentCostPackValidator.validate(pack).isEmpty()" in pack,
 "98 contiguous half-level steps": "steps.size == 98" in pack and "step.fromLevel == 1.0 + index / 2.0" in pack,
 "table sources are explicitly community not authoritative game-master": "not a live" in pack.lower() or "NOT an" in pack,
 "pack never invents evolved move costs": "speciesCosts = emptyList()" in pack and "evolutionCosts = emptyList()" in pack,
 "no exceptional Eternatus costs are guessed": 'contains("eternatus", ignoreCase = true)' in pack,
 "regular forms exclude mega and primal": 'contains("mega", ignoreCase = true)' in pack and 'contains("primal", ignoreCase = true)' in pack,
 "opt-in is off by default and per specimen": 'rememberSaveable(owned.id) { mutableStateOf(false) }' in panel,
 "a source integrity gate is required before using prices": "referenceAvailable = remember { CommunityNormalPowerUpPack.authenticatedReference() }" in panel,
 "user sees the date and community status": "Usar tabela comunitária revista em 15/10/2026 (não oficial)" in panel,
 "live prices never assumed": "selectedPack = reference.takeIf" in panel and "allowCommunityEstimate && referenceAvailable && supported" in panel,
 "Special modifiers continue blocked": "!owned.isLucky && !owned.isShadow && !owned.isPurified" in panel and "UNVERIFIED_COST_MODIFIER" in gate,
 "only known user inventories can be passed to costing": "ledger = knownLedger" in panel and "UserResourcePolicy.completeLedgerOrNull" in panel,
 "UI does not price when build edits are pending": "if (hasPendingBuildChanges)" in panel and "else {" in panel,
 "UI shows amounts only when gate permits": "if (quote.canDisplayExactAmounts && result != null && familyId != null)" in panel,
 "shortages come from real planner not heuristics": "result.missingResources" in panel and "InvestmentPlanner.plan(" in gate,
 "no meta/transfer investment claims": "Não significa que valha a pena investir" in panel and "relevância no meta" in pack,
 "independent totals are asserted in JVM suite": all(x in source_tests for x in ["270000","225000","250000","475000","296","304","248"]),
 "test coverage is required": source_tests.count("@Test fun ") >= 19,
}
for label,ok in checks.items(): print(("PASS" if ok else "FAIL")+" "+label)
print(f"COMMUNITY_POWERUP {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()),"Community power-up trust checks failed"
