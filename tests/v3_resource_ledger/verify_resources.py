#!/usr/bin/env python3
"""Resource Ledger V3: preserve UNKNOWN and keep shared family/global balances distinct."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
policy=(base/"UserResourcePolicy.kt").read_text(encoding="utf-8")
storage=(base/"UserResourceStore.kt").read_text(encoding="utf-8")
ui=(base/"UserResourcePanel.kt").read_text(encoding="utf-8")
panel=(base/"InvestmentTargetPanel.kt").read_text(encoding="utf-8")
tests=(root/"app/src/test/java/com/rui/pvpgo/UserResourcePolicyTest.kt").read_text(encoding="utf-8")
android=(root/"app/src/androidTest/java/com/rui/pvpgo/UserResourceStoreAndroidTest.kt").read_text(encoding="utf-8")
db=(base/"CollectionDatabase.kt").read_text(encoding="utf-8")
checks={
 "No implicit resource default": 'val stardust: Int? = null' in policy and 'val candy: Int? = null' in policy,
 "known zero is distinct from unknown": '"0"' in policy and 'normalized.isEmpty()) return null' in policy,
 "balances parsed explicitly": 'fun balancesFrom(inputs:' in policy and 'isValidInput(inputs[it].orEmpty())' in policy,
 "all seven balances required for complete ledger": 'val eliteCharged = balances.eliteChargedTm ?: return null' in policy,
 "family ID required before ledger": 'if (familyId.isBlank()' in policy,
 "resource scope is one family only": 'candyByFamily = mapOf(familyId to candy)' in policy and 'xlCandyByFamily = mapOf(familyId to xl)' in policy,
 "only app private preferences": 'Context.MODE_PRIVATE' in storage and 'pvp_user_resources_v1' in storage,
 "global and family budgets saved with separate keys": '"global.stardust"' in storage and '"family.' in storage,
 "blank means remove persisted field": 'if (amount == null) edit.remove(key)' in storage,
 "storage commit checked": 'check(edit.commit())' in storage,
 "UI explicit save action": 'Text("Guardar recursos")' in ui and 'store.save(familyId, parsed)' in ui,
 "user is warned missing does not mean zero": 'desconhecidos, não são considerados zero' in ui,
 "UI uses confirmed on-device inventory": 'val resourceStore = remember(context)' in panel and 'resourceStore::load' in panel,
 "invalid catalog species does not get a family resource bucket": 'familyId = species?.takeIf { it.speciesId == owned.speciesId }' in panel,
 "engine only receives complete known ledger": 'UserResourcePolicy.completeLedgerOrNull' in panel and 'ledger = knownLedger' in panel,
 "cost pack remains absent until actual validation": 'pack = null' in panel,
 "existing Room user data unaffected": 'version = 2' in db and 'MIGRATION_1_2' in db,
 "policy JVM scenarios required": tests.count('@Test fun ') >= 18,
 "Android persistence tests added not claimed executed": android.count('@Test fun ') >= 4,
}
for name,ok in checks.items(): print(("PASS" if ok else "FAIL")+" "+name)
print(f"RESOURCE_LEDGER {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()), "Resource ledger trust regression"
