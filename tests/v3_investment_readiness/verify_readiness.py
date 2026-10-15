#!/usr/bin/env python3
"""V3 build-readiness guard. Pure source contract, not a device test."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
policy=(base/"InvestmentReadinessPolicy.kt").read_text(encoding="utf-8")
panel=(base/"InvestmentReadinessPanel.kt").read_text(encoding="utf-8")
core=(base/"CoreScreens.kt").read_text(encoding="utf-8")
test=(root/"app/src/test/java/com/rui/pvpgo/InvestmentReadinessPolicyTest.kt").read_text(encoding="utf-8")
store=(base/"CollectionDatabase.kt").read_text(encoding="utf-8")
checks={
  "real specimen in Inbox is checked without saving it": "owned = candidate" in core and "compact = true" in core,
  "confirmed Pokemon 360 shows full readiness detail": "owned = owned" in core and "compact = false" in core,
  "policy computes actual CP from species and IV and recorded level": "PokemonMath.cp(species, owned.iv, recordedLevel)" in policy,
  "missing CP remains unknown": "owned.cp != null" in policy and "CP atual" in policy,
  "missing level remains unknown": "owned.level != null" in policy and "Nível atual" in policy,
  "mismatched CP is flagged for review": "consistency != owned.cp" in policy and "ReadinessCheckStatus.RECHECK" in policy,
  "base league uses fail-closed owned gate": "OwnedEligibilityGate.evaluate(" in policy and "StandardRulesets.forLeague(league)" in policy,
  "base league is never advertised as cup certification": "as regras do evento e a preparação para batalha não estão certificadas" in policy,
  "actual moves not fabricated": "owned.fastMoveId != null" in policy and "owned.chargedMoveIds.isNotEmpty()" in policy,
  "costs explicitly blocked without authentic pack": "pack de custos validado" in policy and "build alvo" in policy,
  "no investment numbers generated from a guessed ledger": "InvestmentPlanner.plan(" not in policy and "ResourceLedger(" not in policy,
  "costing cannot hide basic data health": 'filterNot { it.name == "Custo do investimento" }' in policy,
  "full UI explains missing and recheck states": "ReadinessCheckStatus.BASE_INELIGIBLE" in panel and "ReadinessCheckStatus.MISSING" in panel,
  "save remains explicit Room confirmation": "repository.confirmNew(item.id)" in core and "db.withTransaction" in store,
  "JVM coverage is mandatory": test.count("@Test fun ") >= 16,
}
for name,ok in checks.items(): print(("PASS" if ok else "FAIL")+" "+name)
print(f"INVESTMENT_READINESS {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()),"Investment readiness integrity regression"
