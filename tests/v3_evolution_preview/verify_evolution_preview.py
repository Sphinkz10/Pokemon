#!/usr/bin/env python3
"""Auditable static wiring gate; not a substitute for running Pokémon GO on a device."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
core=(base/"CoreScreens.kt").read_text(encoding="utf-8")
advisor=(base/"CandidateEvolutionAdvisor.kt").read_text(encoding="utf-8")
evolution=(base/"EvolutionRepository.kt").read_text(encoding="utf-8")
test1=(root/"app/src/test/java/com/rui/pvpgo/CandidateEvolutionAdvisorTest.kt").read_text(encoding="utf-8")
test2=(root/"app/src/test/java/com/rui/pvpgo/CandidateEvolutionExplanationTest.kt").read_text(encoding="utf-8")
start=core.index("private fun CandidateRankPreview(")
end=core.index("private fun OwnedPokemonDetailScreen(",start)
preview=core[start:end]
checks={
  "Inbox passes real catalog to candidate preview": "catalog = catalog" in core and "CandidateRankPreview(" in core,
  "catalog explicit edges are required": "EvolutionRepository.descendants(source, catalog)" in advisor and "evolutionSpeciesIds.forEach" in evolution,
  "unreleased, Mega and Primal forms excluded": "it.released && !it.isMega" in advisor and '"primal" !in it.tags' in advisor,
  "candidate source form checked": "require(candidate.speciesId == source.speciesId)" in advisor,
  "comparison uses target ranks not unrelated species ranks": "RankRepository.find(target, league, candidate.iv, settings)" in advisor and "RankRepository.find(target, league, saved.iv, settings)" in advisor,
  "comparable individuals must have reachable catalog paths": "target.speciesId in reachableIds(it.speciesId)" in advisor,
  "candidate is excluded by individual id": "it.id != candidate.id" in advisor,
  "preview has bounded evaluation count": "targets.take(MAX_TARGETS_PER_PREVIEW)" in advisor and "omittedCount" in advisor,
  "heavy evaluation runs in background": "withContext(Dispatchers.Default)" in preview and "CandidateEvolutionAdvisor.evaluate(" in preview,
  "preview recomputes when league or Room collection changes": "LaunchedEffect(species, candidate.id, candidate.iv, league, collection, collectionLoaded, catalog)" in preview,
  "preview exposes unknown and unloaded states": "!collectionLoaded || !evolutionsComputed" in preview and "evolutionOverview == null" in preview,
  "real costs and PvP meta remain explicitly unknown": all(q in advisor for q in ["Candy", "elegibilidade", "custos", "força no meta", "transferências"]),
  "explicit manual confirmation unchanged": "repository.confirmNew(item.id)" in core,
  "graph and bounds have JVM coverage": test1.count("@Test fun ") >= 12 and test2.count("@Test fun ") >= 8,
}
for k,v in checks.items(): print(("PASS" if v else "FAIL")+" "+k)
assert all(checks.values()),"Evolution candidate preview integrity regression"
print(f"EVOLUTION_PREVIEW {sum(checks.values())}/{len(checks)} PASS")
