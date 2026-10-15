#!/usr/bin/env python3
"""Source contract for the explicit, league-limited auto-target UX."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
base = root / "app/src/main/java/com/rui/pvpgo"
policy = (base / "LeagueCapTargetPolicy.kt").read_text(encoding="utf-8")
panel = (base / "InvestmentTargetPanel.kt").read_text(encoding="utf-8")
simulator = (base / "InvestmentTargetSimulator.kt").read_text(encoding="utf-8")
quote = (base / "InvestmentCostQuoteGate.kt").read_text(encoding="utf-8")
test = (root / "app/src/test/java/com/rui/pvpgo/LeagueCapTargetPolicyTest.kt").read_text(encoding="utf-8")
checks = {
    "uses actual specimen not suggested IVs": "PokemonMath.cp(species, owned.iv, currentLevel)" in policy,
    "requires observed CP and level": "currentLevel == null || recordedCp == null" in policy,
    "rejects wrong form": "species.speciesId != owned.speciesId" in policy,
    "rejects inconsistent CP": "derivedCp != recordedCp" in policy,
    "does not suggest de-leveling for over-limit Pokemon": "recordedCp > cap" in policy and "ALREADY_OVER_CAP" in policy,
    "Best Buddy level range is outside this helper": "currentLevel > 50.0" in policy,
    "core mathematical engine determines cap": "PokemonMath.highestLevelAtOrBelowCap(" in policy,
    "current level is mathematical lower bound": "minLevel = currentLevel" in policy,
    "50 is hard max level": "maxLevel = 50.0" in policy,
    "Master has no invented CP cap": "if (cap == null) 50.0" in policy,
    "existing strict simulator validates current specimen": "InvestmentTargetSimulator.simulate(" in policy and "current.canRequestCosting" in policy,
    "final manual validation cannot be bypassed": "final.canRequestCosting" in policy,
    "UI shows explicit use target button": "Usar nível máximo para esta liga" in panel,
    "manual target still possible": "Nível objetivo (1–50)" in panel,
    "UI cannot apply target while build edits unsaved": "enabled = !hasPendingBuildChanges && suggestedCapTarget.canApply" in panel,
    "UI writes only local targetText": "targetText = LeagueCapTargetPolicy.displayLevel(it)" in panel,
    "UI keeps cost opt-in off until user consents": 'mutableStateOf(false)' in panel and "allowCommunityEstimate" in panel,
    "UI only displays resource quote after gate": "quote.canDisplayExactAmounts" in panel and "InvestmentCostQuoteGate.evaluate(" in panel,
    "no new ResourceLedger or new cost pack guessed": "ResourceLedger(" not in policy and "InvestmentPlanner.plan(" not in policy,
    "explicit caveat no verified meta": "não por força em PvP" in policy and "copas especiais" in policy,
    "JVM edge cases executed in CI": test.count("@Test fun ") >= 20,
}
for desc, success in checks.items():
    print(("PASS " if success else "FAIL ") + desc)
print(f"LEAGUE_CAP_TARGET {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()), "League-cap target regression"
