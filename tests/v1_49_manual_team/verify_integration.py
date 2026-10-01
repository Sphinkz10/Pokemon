from pathlib import Path
root=Path(__file__).resolve().parents[2]
u=(root/'app/src/main/java/com/rui/pvpgo/live/LiveCompanionScreen.kt').read_text()
p=(root/'app/src/main/java/com/rui/pvpgo/live/CompanionManualTeamPolicy.kt').read_text()
checks={
 'Room team flow':'collectionRepo.savedTeams.collectLatest' in u,
 'Room owned flow':'collectionRepo.collection.collectLatest' in u,
 'real team invalidation':'teamSelection = teamSelection.validated(options)' in u,
 'manual active selection':'teamSelection.chooseActive(member.ownedPokemonId, options)' in u,
 'explicit opponent text':'manualOpponent.enter(opponentDraft)' in u,
 'new session reset':'manualOpponent = manualOpponent.forSession(runtime.sessionId)' in u,
 'art by species ID':'PokemonArtwork(member.speciesId' in u,
 'no reducer in UI':'reducer.reduce(' not in u,
 'no Room write in UI':'upsertBattleRecord(' not in u and 'upsertSavedTeam(' not in u,
}
for k,v in checks.items():print(('PASS' if v else 'FAIL'),k)
assert all(checks.values())
print('COMPANION_V149_INTEGRATION_STATIC_PASS checks='+str(len(checks)))
