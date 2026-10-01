#!/usr/bin/env python3
"""Offline F46 self-test: expanded exact-pin contract + semantic/final-stage diff."""
from __future__ import annotations
import copy, hashlib, json, pathlib, re, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import diff_same_revision_trace as differ

ROOT=pathlib.Path(__file__).resolve().parents[1]
LOCAL=ROOT/"evidence/local-v1.29-expanded-traces.json"
MANIFEST=ROOT/"tools/pvpoke_expanded_parity_cases.json"
RUNNER=ROOT/"tools/pvpoke_expanded_parity_runner.js"

def synthetic_external(local):
    results=[]
    for r in local["results"]:
        timeline=[]
        for e in r["timeline"]:
            typ=e["type"]
            if typ=="FAST_MOVE": ext_type,name="fast test",e["moveId"]
            elif typ=="CHARGED_MOVE": ext_type,name="charged test",e["moveId"]
            elif typ=="SHIELD": ext_type,name="shield","Shield"
            elif typ=="FAINT": ext_type,name="faint","Faint"
            else: continue
            vals=[] if typ in ("SHIELD","FAINT") else [e.get("damage"),0,0]
            timeline.append({"i":len(timeline),"turn":e["turn"],"time":e["turn"]*500,"actor":e["actor"],"type":ext_type,"name":name,"values":vals,"editable":True})
        h=hashlib.sha256(json.dumps(timeline,separators=(",",":"),ensure_ascii=False).encode()).hexdigest()
        results.append({k:r[k] for k in ("fixtureId","shieldsA","shieldsB","battleRatingA","battleRatingB","winner","turns","final")} | {"turnsToWin":[0,0],"traceSha256":h,"timeline":timeline})
    return {"schemaVersion":2,"source":{"repository":"https://github.com/pvpoke/pvpoke","commitSha":differ.EXPECTED_SHA,"siteVersion":differ.EXPECTED_VERSION,"verifiedBy":"offline-f46-self-test"},"results":results}

def main():
    local=json.loads(LOCAL.read_text())
    assert local.get("engineVersion")=="1.29", f"wrong local engineVersion: {local.get('engineVersion')}"
    manifest=json.loads(MANIFEST.read_text())
    runner=RUNNER.read_text()
    ids=[r["fixtureId"] for r in local["results"]]
    assert ids==[c["fixtureId"] for c in manifest["cases"]]
    runner_ids=re.findall(r'fixtureId:"([A-Z0-9-]+)"',runner)
    assert runner_ids==ids, (runner_ids,ids)
    ext=synthetic_external(local); differ.validate_external(ext)
    E,L=differ.by_id(ext),differ.by_id(local)
    for fid in ids:
        idx,turn,*_=differ.first_diff(E[fid],L[fid]); assert idx is None and turn is None
        assert differ.final_summary(E[fid])==differ.final_summary(L[fid])
    mutated=copy.deepcopy(ext)
    row=next(r for r in mutated["results"] if r["fixtureId"]=="EXP-TALONFLAME-GALVANTULA-5TURN-BUFF")
    row["final"]["a"]["attackStage"]=0
    assert differ.final_summary(row)!=differ.final_summary(L[row["fixtureId"]])
    print("EXPANDED_PARITY_PIPELINE_V1_29_OK")
    print(f"cases={len(ids)} syntheticEvents=MATCH stageMutation=DETECTED runnerManifestSync=PASS")
    return 0
if __name__=="__main__": raise SystemExit(main())
