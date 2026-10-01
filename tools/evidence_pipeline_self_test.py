#!/usr/bin/env python3
"""Offline self-test for V1.29 evidence validation and F45 first-divergence logic."""
from __future__ import annotations
import copy, hashlib, json, pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import import_pinned_pvpoke_results as importer
import diff_same_revision_trace as differ

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOCAL = ROOT / "evidence/local-v1.29-pinned-traces.json"


def synthetic_external(local: dict) -> dict:
    results=[]
    for r in local["results"]:
        timeline=[]
        for e in r["timeline"]:
            typ=e["type"]
            if typ == "FAST_MOVE": ext_type, name = "fast test", e["moveId"]
            elif typ == "CHARGED_MOVE": ext_type, name = "charged test", e["moveId"]
            elif typ == "SHIELD": ext_type, name = "shield", "Shield"
            elif typ == "FAINT": ext_type, name = "faint", "Faint"
            else: continue
            values=[] if typ in ("SHIELD","FAINT") else [e.get("damage"),0,0]
            timeline.append({"i":len(timeline),"turn":e["turn"],"time":e["turn"]*500,"actor":e["actor"],"type":ext_type,"name":name,"values":values,"editable":True})
        trace_hash=hashlib.sha256(json.dumps(timeline,separators=(",",":"),ensure_ascii=False).encode()).hexdigest()
        results.append({k:r[k] for k in ("fixtureId","battleRatingA","battleRatingB","winner","turns")} | {"turnsToWin":[0,0],"final":r["final"],"traceSha256":trace_hash,"timeline":timeline})
    return {
        "schemaVersion":2,
        "source":{"repository":"https://github.com/pvpoke/pvpoke","commitSha":importer.EXPECTED_SHA,"siteVersion":importer.EXPECTED_VERSION,"verifiedBy":"offline-self-test"},
        "scenario":{"cp":1500,"levelCap":50,"shieldsA":1,"shieldsB":1,"decisionMethod":"default","buffChanceModifier":-1},
        "results":results,
    }


def main() -> int:
    local=json.loads(LOCAL.read_text())
    assert local.get("engineVersion") == "1.29", f"wrong local engineVersion: {local.get('engineVersion')}"
    ext=synthetic_external(local)
    importer.validate(ext)
    E=differ.by_id(ext); L=differ.by_id(local)
    for fid in differ.REQUIRED_IDS:
        idx, turn, *_ = differ.first_diff(E[fid],L[fid])
        assert idx is None and turn is None, (fid,idx,turn)

    mutated=copy.deepcopy(ext)
    first_move=next(e for e in mutated["results"][0]["timeline"] if e["type"].startswith("fast "))
    first_move["values"][0] += 1
    idx, turn, x, y, *_ = differ.first_diff(mutated["results"][0],L[differ.REQUIRED_IDS[0]])
    assert idx == 0 and turn == 2 and x["damage"] == y["damage"] + 1

    # The stale trace hash must cause the evidence importer to refuse the mutation.
    try:
        importer.validate(mutated)
        raise AssertionError("tampered timeline unexpectedly accepted")
    except ValueError as exc:
        assert "traceSha256 does not match" in str(exc)

    print("EVIDENCE_PIPELINE_V1_29_OK")
    print("positive=3/3 MATCH negative=first-divergence-T2 tamper=REFUSED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
