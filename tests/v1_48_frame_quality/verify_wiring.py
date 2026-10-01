from pathlib import Path
root=Path(__file__).resolve().parents[2]/'app/src/main/java/com/rui/pvpgo/live'
service=(root/'MediaProjectionCaptureService.kt').read_text()
ui=(root/'LiveCompanionScreen.kt').read_text()
contract=(root/'LiveCaptureContracts.kt').read_text()
coordinator=(root/'LiveBattleCoordinator.kt').read_text()
policy=(root/'LiveSemanticAdmissionPolicy.kt').read_text()
checks={
  'CaptureRuntimeState carries quality': 'val frameQuality: FrameQualitySnapshot' in service,
  'session reset during start': 'frameQualityGate.reset(newSessionId)' in service,
  'frame is scored in service': 'frameQualityGate.onFrame(' in service,
  'captured frame binds sessionId': 'sessionId = newSessionId' in service,
  'UI observes capture quality': 'FrameQualityReadiness.assess(runtime.frameQuality' in ui,
  'UI includes staleness clock': 'qualityClock = System.currentTimeMillis()' in ui,
  'quality is never semantic identity': 'NÃO É DETEÇÃO DE BATALHA' in ui,
  'analyzer returns candidates, not raw events': 'List<SemanticObservationCandidate<LiveBattleEvent>>' in contract,
  'coordinator default-denies detectors': 'LiveSemanticAdmissionGate()' in coordinator,
  'coordinator gates before reducer': coordinator.index('admission.consider(') < coordinator.index('reducer.reduce(state, trusted)'),
  'coordinator refuses session-less frames': 'if (session.isNullOrBlank())' in coordinator,
  'gate requires >=2 independent frames': 'require(confirmationsRequired >= 2)' in policy,
  'gate rejects low confidence': 'UNTRUSTED_CANDIDATE' in policy,
  'no default calibrated detector': 'approvedDetectorIds: Set<String> = emptySet()' in policy,
  'privacy preserved (no bitmap serialization)': 'Bitmap' not in policy and 'Pixel' not in policy,
  'manual log not fed to reducer': 'reducer.reduce(' not in ui,
}
for name,ok in checks.items():print(('PASS' if ok else 'FAIL')+' '+name)
assert all(checks.values())
print(f'COMPANION_V148_WIRING_PASS checks={len(checks)}')
