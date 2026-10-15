#!/usr/bin/env python3
"""V3 candidate IV decision contract: pure explanatory evidence, never transfer automation."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
base = root / "app/src/main/java/com/rui/pvpgo"
core = (base / "CoreScreens.kt").read_text(encoding="utf-8")
policy = (base / "CandidateReviewPolicy.kt").read_text(encoding="utf-8")
test = (root / "app/src/test/java/com/rui/pvpgo/CandidateDecisionPolicyTest.kt").read_text(encoding="utf-8")
start = core.index("private fun CandidateRankPreview(")
end = core.index("private fun OwnedPokemonDetailScreen(", start)
preview = core[start:end]
checks = {
    "comparison is shown only when candidate preview is expanded":
        "if (expandedCandidateId == item.id)" in core,
    "Room collection loading is supplied to preview":
        "collectionLoaded = collectionLoaded" in core,
    "exact owned specimen is used for candidate IV comparison":
        "candidate = c" in core and "candidate.iv" in preview,
    "only same species and form can be compared":
        "it.speciesId == species.speciesId && it.id != candidate.id" in preview,
    "preview computes comparisons on background dispatcher":
        "withContext(Dispatchers.Default)" in preview,
    "saved owned rank is computed using the same league":
        "RankRepository.find(species, league, saved.iv, RankSettings())" in preview,
    "candidate rank is computed using real RankRepository":
        "RankRepository.find(" in preview and "species, league, candidate.iv, RankSettings()" in preview,
    "UI uses a policy for explaining comparisons":
        "CandidateReviewPolicy.explainIvComparison(" in preview,
    "loading cannot claim an empty collection":
        "if (!collectionLoaded || !computed)" in policy,
    "unknown current best cannot claim new candidate is better":
        "val best = evidence.bestOwnedRank ?: return CandidateDecisionText(" in policy,
    "lower rank number is treated as better":
        "evidence.candidateRank < best" in policy,
    "meta, costs, evolution, transfer and eligibility remain explicitly unknown":
        all(token in policy for token in ["Força no meta", "custos", "evoluções", "elegibilidade", "transferências"]),
    "manual confirmation still controls persistent saves":
        "repository.confirmNew(item.id)" in core,
    "JVM regression scenarios are present":
        test.count("@Test fun ") >= 11,
}
for name, valid in checks.items():
    print(("PASS" if valid else "FAIL") + " " + name)
assert all(checks.values()), "Candidate comparison decision contract failed"
print(f"CANDIDATE_DECISION {sum(checks.values())}/{len(checks)} PASS")
