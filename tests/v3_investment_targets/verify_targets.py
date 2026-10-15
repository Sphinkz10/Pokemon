#!/usr/bin/env python3
"""Source wiring contract: simulator is descriptive, cost gate requires real sources."""
from pathlib import Path
import re

root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
sim=(base/"InvestmentTargetSimulator.kt").read_text(encoding="utf-8")
gate=(base/"InvestmentCostQuoteGate.kt").read_text(encoding="utf-8")
panel=(base/"InvestmentTargetPanel.kt").read_text(encoding="utf-8")
core=(base/"CoreScreens.kt").read_text(encoding="utf-8")
tests1=(root/"app/src/test/java/com/rui/pvpgo/InvestmentTargetSimulatorTest.kt").read_text(encoding="utf-8")
tests2=(root/"app/src/test/java/com/rui/pvpgo/InvestmentCostQuoteGateTest.kt").read_text(encoding="utf-8")
start=core.index("private fun OwnedPokemonDetailScreen(")
end=core.index("fun RadarRootScreen(",start)
detail=core[start:end]
checks={
"360 contains target panel": "InvestmentTargetPanel(" in detail,
"pending build edits cannot be treated as saved": "hasPendingBuildChanges = hasUnsavedBuild" in detail and "if (hasPendingBuildChanges)" in panel,
"target choice starts blank, not invented": 'mutableStateOf("")' in panel and "rememberSaveable(owned.id, league.name)" in panel,
"target supports 0.5 levels 1 to 50": "1.0..50.0" in sim and "targetLevel * 2" in sim,
"target below current is refused": "targetLevel < currentLevel" in sim,
"recorded CP is required and checked": "recordedCp == null" in sim and "currentCalculated != recordedCp" in sim,
"CP for chosen level is from real math": "PokemonMath.cp(species, owned.iv, targetLevel)" in sim,
"league gate is fail closed": "OwnedEligibilityGate.evaluate(" in sim and "OwnedEligibilityStatus.CONFIRMED" in sim,
"generic cup limitation is clear": "copas especiais" in sim,
"resource prices are never fabricated by simulator": "Stardust/Candy" in sim and "InvestmentPlanner.plan(" not in sim,
"real cost engine is behind validated pack": "InvestmentCostPackValidator.validate(pack)" in gate and "InvestmentPlanner.plan(" in gate,
"null pack blocks exact price": "if (pack == null)" in gate,
"null inventory never becomes zero": "if (ledger == null)" in gate and not re.search(r"(?m)^\\s*(?:val|var)\\s+\\w+\\s*=\\s*ResourceLedger\\(\\)", gate),
"incomplete cost cannot be shown as exact": "if (!plan.exactCostKnown)" in gate and "CostQuoteGateStatus.INCOMPLETE_COST" in gate,
"the current app passes null pack and ledger": "pack = null" in panel and "ledger = null" in panel,
"GUI only shows cost status for valid target": "if (simulation.canRequestCosting)" in panel,
"verified exact amount requires plan gate": "status == CostQuoteGateStatus.VERIFIED_COST" in gate,
"unit coverage target": tests1.count("@Test fun ")>=16,
"unit coverage cost": tests2.count("@Test fun ")>=15,
}
for label,ok in checks.items(): print(("PASS" if ok else "FAIL")+" "+label)
print(f"TARGET_GATE {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()), "Target projection or costing guard failed"
