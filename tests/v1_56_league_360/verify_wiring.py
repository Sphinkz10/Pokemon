#!/usr/bin/env python3
"""V1.56 static integration gate. JUnit tests assert league and input contracts."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
src=root/"app/src/main/java/com/rui/pvpgo"
def read(name):
    return (src/name).read_text(encoding="utf-8")
screen=read("SpeciesCollectionScreens.kt")
core=read("CoreScreens.kt")
league=read("SpeciesLeaguePolicy.kt")
ranking=read("RankRepository.kt")
policy=read("SpeciesCollectionPolicy.kt")
validation=read("OwnedBuildValidation.kt")
build=(root/"app/build.gradle.kts").read_text(encoding="utf-8")
checks={
 "four selectable leagues": all("League."+x in league for x in ["LITTLE","GREAT","ULTRA","MASTER"]),
 "correct CP limits": all(v in league for v in ["500 CP","1 500 CP","2 500 CP","Sem limite CP"]),
 "actual league passed to ranking engine": "RankRepository.find(species, league, item.iv, settings)" in screen,
 "rank calculation reacts to league": "LaunchedEffect(species.speciesId, owned, league)" in screen,
 "ranking state scoped by league": "remember(species.speciesId, owned, league)" in screen,
 "shared league selector": "private fun SpeciesLeagueSelector(" in screen,
 "overview has league selector": 'item { SpeciesLeagueSelector(league, onLeagueChange) }' in screen,
 "league switcher on all three subpages": screen.count("SpeciesLeagueSelector(league, onLeagueChange)") == 3,
 "league selection owned by navigation": "var speciesLeague by remember" in core,
 "league persists across routes": core.count("league = speciesLeague") == 3,
 "league changed in all subroutes": core.count("onLeagueChange = { speciesLeague = it }") == 3,
 "overview reads generic best policy": "SpeciesCollectionPolicy.bestForLeague(owned, ranks)" in screen,
 "rank comparison not hardwired to Great": "SpeciesCollectionPolicy.betterLeagueRank(first, second, ranks)" in screen,
 "compare dynamically labels correct league": "ComparisonMetricRow(SpeciesLeaguePolicy.title(league)" in screen,
 "rank table cache is bounded": "MAX_TABLES = 24" in ranking,
 "legacy rank policy remains compatible": "fun bestForGreat(" in policy,
 "owned detail uses real artwork": "PokemonArtwork(species.name, Modifier.size(90.dp), shiny = owned.isShiny" in core,
 "owned details show IV and uncertain status": "owned.iv.total" in core and "DADOS POR CONFIRMAR" in core,
 "CP invalid typed input is blocked": "OwnedBuildValidation.cpError(cpText)" in core,
 "level invalid typed input blocked": "OwnedBuildValidation.levelError(levelText)" in core,
 "charged move invalid input blocked": "OwnedBuildValidation.chargedMovesError(chargedMoveIds)" in core,
 "invalid form does not save": "return@Button" in core and "validationError" in core,
 "empty fields explicitly unknown": "if (raw.isBlank()) return null" in validation,
 "version 1.56": 'versionCode = 56' in build and 'versionName = "1.56.0-dev"' in build,
 "independent install id": 'applicationIdSuffix = ".installtestv156"' in build,
}
for label,yes in checks.items():
    print(("PASS" if yes else "FAIL"),label)
print(f"LEAGUE_360 {sum(checks.values())}/{len(checks)} PASS")
assert all(checks.values()), "Broken four-league 360 or owned-data validation"
