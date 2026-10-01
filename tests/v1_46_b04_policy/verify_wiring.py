from pathlib import Path
R=Path(__file__).resolve().parents[2]
screens=(R/'app/src/main/java/com/rui/pvpgo/BattleTeamCoverageScreen.kt').read_text()
policy=(R/'app/src/main/java/com/rui/pvpgo/BattleTeamCoveragePolicy.kt').read_text()
root=(R/'app/src/main/java/com/rui/pvpgo/CoreScreens.kt').read_text()
result=(R/'app/src/main/java/com/rui/pvpgo/BattlesGoldenScreens.kt').read_text()
conditions={
 'B04 entry from B03':'onTeamCoverage = { showingTeamCoverage = true }' in root,
 'B04 return to B03':'onBack = { showingTeamCoverage = false }' in root,
 'B04 edit returns B02':'onEditScenario = { showingTeamCoverage = false; result = null }' in root,
 'B04 receives Room teams':'repository.savedTeams.collectLatest { savedTeams = it }' in root,
 'B04 respects selected league':'BattlesGoldenPolicy.eligibleTeams(savedTeams, analysis.scenario.league)' in screens,
 'B04 ratings only if READY':'analysis.status == MatchupAdvisorStatus.READY' in policy,
 'B04 ratings only same league':'team.league == analysis.scenario.league' in policy,
 'B04 no arbitrary scores':'rating = rec.evaluation.battleRating' in policy and 'Math.random' not in policy,
 'B04 exclusion reasons preserved':'detail = excluded.detail' in policy,
 'B04 guard same shields/energy':'rec.evaluation.scenario == analysis.scenario' in policy,
 'B04 guard removed specimen':'specimen == null ->' in policy,
 'B04 explicit not 3v3 label':'Não é batalha 3v3' in policy and 'não é probabilidade de vitória' in screens,
 'B04 advisor cap raised 50':'limit = 50' in root,
 'B04 collection snapshot analyzed':'collection = requestCollection' in root,
 'B04 stale Room projection invalidated':'analyzedCollectionSnapshot != collection' in root,
 'B02 dark golden scaffold':'containerColor = PvpColors.CanvasStart' in root,
 'B02 two rows IV+level':'label = { Text("Ataque") }' in root and 'label = { Text("Nível") }' in root,
 'B03 B04 CTA only on available results':'if (BattlesGoldenPolicy.canShowRatings(analysis))' in result and 'onClick = onTeamCoverage' in result,
 'Version 46': 'versionCode = 46' in (R/'app/build.gradle.kts').read_text(),
 'Source prevents 3v3 forecast': 'Não prevê trocas, alinhamentos' in screens,
}
for name,ok in conditions.items(): print(('PASS' if ok else 'FAIL')+' '+name)
print('WIRING_PASS',sum(conditions.values()),'/',len(conditions))
assert all(conditions.values())
