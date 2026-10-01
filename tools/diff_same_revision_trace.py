#!/usr/bin/env python3
"""Semantic first-divergence finder for exact-pin PvPoke vs local V1.29 traces.

Default mode requires the three F44 baseline fixtures. --all compares every common fixture and is
used by the F46 expanded matrix. Final HP/energy/shields AND attack/defense stages are compared.
"""
from __future__ import annotations
import argparse, json, pathlib, re

EXPECTED_SHA = "a93147bf1f2e829758958bfb5b37e56bbadc9678"
EXPECTED_VERSION = "1.40.1.3"
REQUIRED_IDS = ["PINNED-AZU-FERALIGATR", "PINNED-AZU-FERALIGATR-SHADOW", "PINNED-AZU-WALREIN"]


def move_id(name):
    if not name:
        return None
    return re.sub(r"[^A-Z0-9]+", "_", str(name).upper()).strip("_")


def external_semantic(event):
    typ = str(event.get("type", ""))
    low = typ.lower()
    if low.startswith("tap") or low == "switchavailable":
        return None
    if low.startswith("fast "):
        kind = "FAST_MOVE"
    elif low.startswith("charged "):
        kind = "CHARGED_MOVE"
    elif low in ("shield", "shieldspecial"):
        kind = "SHIELD"
    elif low == "faint":
        kind = "FAINT"
    else:
        kind = "UPSTREAM:" + typ
    vals = event.get("values") if isinstance(event.get("values"), list) else []
    damage = vals[0] if kind in ("FAST_MOVE", "CHARGED_MOVE") and vals and isinstance(vals[0], (int, float)) else None
    return {"turn": int(event.get("turn", 0)), "actor": event.get("actor"), "type": kind,
            "moveId": move_id(event.get("name")) if kind in ("FAST_MOVE", "CHARGED_MOVE") else None,
            "damage": int(damage) if damage is not None else None, "sourceIndex": event.get("i")}


def local_semantic(event):
    typ = str(event.get("type", ""))
    if typ in ("INFO", "CMP", "STAT_CHANGE"):
        return None
    if typ not in ("FAST_MOVE", "CHARGED_MOVE", "SHIELD", "FAINT"):
        return {"turn": int(event.get("turn", 0)), "actor": event.get("actor"), "type": "LOCAL:" + typ,
                "moveId": event.get("moveId"), "damage": event.get("damage"), "sourceIndex": event.get("i")}
    return {"turn": int(event.get("turn", 0)), "actor": event.get("actor"), "type": typ,
            "moveId": event.get("moveId") if typ in ("FAST_MOVE", "CHARGED_MOVE") else None,
            "damage": event.get("damage") if typ in ("FAST_MOVE", "CHARGED_MOVE") else None,
            "sourceIndex": event.get("i")}


def comparable(e):
    return {k: e.get(k) for k in ("turn", "actor", "type", "moveId", "damage")}


def validate_external(p):
    if p.get("schemaVersion") != 2:
        raise ValueError("external schemaVersion must be 2")
    src = p.get("source") or {}
    if src.get("commitSha") != EXPECTED_SHA or src.get("siteVersion") != EXPECTED_VERSION:
        raise ValueError("REFUSED: external trace is not exact-pin PvPoke evidence")


def by_id(payload):
    return {r.get("fixtureId"): r for r in (payload.get("results") or [])}


def first_diff(ext, loc):
    a = [x for e in ext.get("timeline", []) if (x := external_semantic(e)) is not None]
    b = [x for e in loc.get("timeline", []) if (x := local_semantic(e)) is not None]
    for i in range(max(len(a), len(b))):
        x = a[i] if i < len(a) else None
        y = b[i] if i < len(b) else None
        if x is None or y is None or comparable(x) != comparable(y):
            turn = min([z["turn"] for z in (x, y) if z is not None], default=None)
            return i, turn, x, y, a, b
    return None, None, None, None, a, b


def final_summary(row):
    final = row.get("final") or {}
    def side(k):
        s = final.get(k) or {}
        return {q: s.get(q) for q in ("hp", "energy", "shields", "attackStage", "defenseStage")}
    return {"a": side("a"), "b": side("b")}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("external_json", type=pathlib.Path)
    ap.add_argument("local_json", type=pathlib.Path)
    ap.add_argument("--out", type=pathlib.Path)
    ap.add_argument("--all", action="store_true", help="compare all common fixtures (F46 expanded mode)")
    args = ap.parse_args()
    ext = json.loads(args.external_json.read_text()); loc = json.loads(args.local_json.read_text())
    validate_external(ext); E, L = by_id(ext), by_id(loc)
    if args.all:
        ids = [r.get("fixtureId") for r in ext.get("results", []) if r.get("fixtureId") in L]
        missing_local = set(E) - set(L)
        if missing_local: raise ValueError(f"local trace missing external fixtures: {sorted(missing_local)}")
    else:
        if set(REQUIRED_IDS) - E.keys() or set(REQUIRED_IDS) - L.keys(): raise ValueError("missing required pinned fixture")
        ids = REQUIRED_IDS
    report={"schemaVersion":2,"reference":{"commitSha":EXPECTED_SHA,"siteVersion":EXPECTED_VERSION},"mode":"all" if args.all else "baseline","results":[]}
    for fid in ids:
        idx, turn, x, y, a, b = first_diff(E[fid], L[fid])
        summary_keys=("battleRatingA","battleRatingB","winner","turns")
        summary_equal=all(E[fid].get(k)==L[fid].get(k) for k in summary_keys)
        final_equal=final_summary(E[fid])==final_summary(L[fid])
        row={"fixtureId":fid,"summaryEqual":summary_equal,"finalStateEqual":final_equal,
             "externalSummary":{k:E[fid].get(k) for k in summary_keys},"localSummary":{k:L[fid].get(k) for k in summary_keys},
             "externalFinal":final_summary(E[fid]),"localFinal":final_summary(L[fid]),
             "semanticEventsExternal":len(a),"semanticEventsLocal":len(b),"firstDivergenceIndex":idx,"firstDivergenceTurn":turn,
             "externalEvent":x,"localEvent":y}
        report["results"].append(row)
        event_status="MATCH" if idx is None else f"DIFF@T{turn}"
        print(f"{fid}: events={event_status} summary={'MATCH' if summary_equal else 'DIFF'} final={'MATCH' if final_equal else 'DIFF'} count={len(a)}/{len(b)}")
    report["allMatch"] = all(r["summaryEqual"] and r["finalStateEqual"] and r["firstDivergenceIndex"] is None for r in report["results"])
    text=json.dumps(report,indent=2)+"\n"
    if args.out:
        args.out.parent.mkdir(parents=True,exist_ok=True); args.out.write_text(text); print(f"REPORT {args.out}")
    else: print(text)

if __name__ == "__main__": main()
