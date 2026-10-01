#!/usr/bin/env python3
import json, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
manifest=json.loads((ROOT/'tools/independent_certification_manifest.json').read_text())
source=(ROOT/'engine/src/main/kotlin/com/rui/pvpgo/engine/EngineCertification.kt').read_text()
assert manifest['release']=='1.29'
assert manifest['status']=='CERTIFIED'
req={r['id']:r for r in manifest['requirements']}
assert sum(1 for r in req.values() if r['passed'])==9
assert manifest['currentBlockers']==[]
assert req['baseline-policy-second-implementation']['actual']==24 and req['baseline-policy-second-implementation']['required']==24 and req['baseline-policy-second-implementation']['passed']
assert req['queued-action-second-implementation']['actual']==12 and req['queued-action-second-implementation']['passed']
# Fail on manifest/source drift for the release evidence counts.
for key, kotlin_name in [
 ('standardAssertions','standardAssertions'),('specialAssertions','specialAssertions'),
 ('randomizedAssertions','randomizedAssertions'),('randomizedCases','randomizedCases'),
 ('mutationSensitivityChecks','mutationSensitivityChecks'),('fastOnlyOutcomeBattles','fastOnlyOutcomeBattles'),
 ('chargedOutcomeScenarios','chargedOutcomeScenarios'),('chargedOutcomeAssertions','chargedOutcomeAssertions'),
 ('independentBaselinePolicyScenarios','independentBaselinePolicyScenarios'),('independentQueuedActionScenarios','independentQueuedActionScenarios')]:
    value=manifest['currentEvidence'][key]
    assert re.search(rf'{kotlin_name}\s*=\s*{value}\b',source), f'drift {key}={value}'
print('INDEPENDENT_CERTIFICATION_MANIFEST_V1_29_OK')
print('requirements=9/9 blockers=none policy=24/24 queue=12/12')
