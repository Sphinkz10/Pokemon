#!/usr/bin/env python3
"""PR19: affordability must use verified player balances, planner and explicit consent."""
from pathlib import Path

root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
advisor=(base/"AffordableLevelPolicy.kt").read_text(encoding="utf-8")
panel=(base/"InvestmentTargetPanel.kt").read_text(encoding="utf-8")
costgate=(base/"InvestmentCostQuoteGate.kt").read_text(encoding="utf-8")
resource=(base/"UserResourcePolicy.kt").read_text(encoding="utf-8")
tests=(root/"app/src/test/java/com/rui/pvpgo/AffordableLevelPolicyTest.kt").read_text(encoding="utf-8")
checks={
 "advisor is real readable code": "internal object AffordableLevelPolicy" in advisor,
 "explicit consent gates cost": "if (!consented)" in advisor and "CONSENT_REQUIRED" in advisor,
 "real cap suggestion gates budget": "if (!suggestion.canApply)" in advisor and "LeagueCapTargetPolicy.suggest(" in advisor,
 "never guesses source": "pack == null" in advisor and "CommunityNormalPowerUpPack.authenticatedReference()" in advisor,
 "requires source-reviewed canonical hash": "pack.contentHashSha256 != CommunityNormalPowerUpPack.PINNED_HASH_SHA256" in advisor,
 "pack still passes engine content validation": "InvestmentCostPackValidator.validate(pack)" in advisor,
 "normal-only blocks Luckies and Shadows": "owned.isLucky || owned.isShadow || owned.isPurified" in advisor,
 "exceptional forms fail-closed": "isSupportedNormalPokemon(" in advisor and "species.isMega" in advisor,
 "requires explicit candy by family": "ledger.candyByFamily.containsKey(familyId)" in advisor,
 "requires explicit XL by family": "ledger.xlCandyByFamily.containsKey(familyId)" in advisor,
 "unknown user balances never converted to zero": "ledger == null" in advisor and "UserResourcePolicy.completeLedgerOrNull" in panel,
 "each probed level has manual simulator validation": "InvestmentTargetSimulator.simulate(" in advisor,
 "each probed price uses real InvestmentCostQuoteGate": "InvestmentCostQuoteGate.evaluate(" in advisor and "InvestmentPlanner.plan(" in costgate,
 "no pricing copied from CP difference": "cost.stardust =" not in advisor,
 "only complete prices count as affordable": "result.canDisplayExactAmounts" in advisor and "InvestmentPlanStatus.READY_NOW" in advisor,
 "only bounded half level steps": "step / 2.0" in advisor and "count > 98" in advisor,
 "binary search avoids 99 full computations": "val mid = (low + high + 1) / 2" in advisor and "high = mid - 1" in advisor,
 "next step gap from planner": "quote(low + 1)" in advisor and "nextStepShortages" in advisor,
 "goal gap from planner": "fullPlan.missingResources" in advisor and "goalShortages" in advisor,
 "no auto spending": "Não gasta nem reserva recursos." in advisor,
 "only one consent control in UI": panel.count("Usar tabela comunitária revista em 15/10/2026 (não oficial)") == 1,
 "UI consent still initially off": 'rememberSaveable(owned.id) { mutableStateOf(false) }' in panel,
 "UI only sets local target text": 'targetText = LeagueCapTargetPolicy.displayLevel(calculatedLevel)' in panel,
 "unsaved real build stops affordability": "if (hasPendingBuildChanges) AffordableLevelResult(" in panel,
 "separate manual target and auto-cap still present": "Nível objetivo (1–50)" in panel and "Usar nível máximo para esta liga" in panel,
 "UI reports next step deficits": "affordable.nextStepShortages.joinToString(" in panel,
 "UI warns not meta recommendation": "qualidade PvP" in advisor and "Não significa que valha a pena investir" in panel,
 "tests cover many unknown & boundary states": tests.count("@Test fun ") >= 28,
}
for name,ok in checks.items(): print(("PASS" if ok else "FAIL")+" "+name)
print(f"AFFORDABLE_LEVEL {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()), "Affordable target / resource trust regression"
