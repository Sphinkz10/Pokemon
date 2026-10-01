#!/usr/bin/env python3
"""Strictly import a same-revision PvPoke browser export into the Kotlin parity baseline."""
from __future__ import annotations
import argparse, hashlib, json, pathlib, re

EXPECTED_SHA = "a93147bf1f2e829758958bfb5b37e56bbadc9678"
EXPECTED_VERSION = "1.40.1.3"
REQUIRED_IDS = [
    "PINNED-AZU-FERALIGATR",
    "PINNED-AZU-FERALIGATR-SHADOW",
    "PINNED-AZU-WALREIN",
]
START = "        // GENERATED_PINNED_RESULTS_START"
END = "        // GENERATED_PINNED_RESULTS_END"


def validate(payload: dict) -> list[dict]:
    if payload.get("schemaVersion") != 2:
        raise ValueError("unsupported schemaVersion")
    src = payload.get("source") or {}
    if src.get("commitSha") != EXPECTED_SHA or src.get("siteVersion") != EXPECTED_VERSION:
        raise ValueError("REFUSED: export is not from the exact pinned revision")
    scenario = payload.get("scenario") or {}
    required_scenario = {"cp":1500, "levelCap":50, "shieldsA":1, "shieldsB":1, "decisionMethod":"default", "buffChanceModifier":-1}
    for k, v in required_scenario.items():
        if scenario.get(k) != v:
            raise ValueError(f"REFUSED: scenario {k}={scenario.get(k)!r}, expected {v!r}")
    rows = payload.get("results")
    if not isinstance(rows, list) or len(rows) != len(REQUIRED_IDS):
        raise ValueError("exactly three pinned results are required")
    by_id = {r.get("fixtureId"): r for r in rows}
    if set(by_id) != set(REQUIRED_IDS):
        raise ValueError(f"fixture set mismatch: {sorted(by_id)}")
    for fid in REQUIRED_IDS:
        r = by_id[fid]
        for key in ("battleRatingA", "battleRatingB", "turns", "traceSha256"):
            if key not in r:
                raise ValueError(f"{fid}: missing {key}")
        if not (0 <= int(r["battleRatingA"]) <= 1000 and 0 <= int(r["battleRatingB"]) <= 1000):
            raise ValueError(f"{fid}: invalid rating")
        if int(r["turns"]) <= 0:
            raise ValueError(f"{fid}: invalid turns")
        if not re.fullmatch(r"[0-9a-f]{64}", str(r["traceSha256"])):
            raise ValueError(f"{fid}: invalid traceSha256")
        if r.get("winner") not in (None, 0, 1):
            raise ValueError(f"{fid}: invalid winner")
        timeline = r.get("timeline")
        if not isinstance(timeline, list) or not timeline:
            raise ValueError(f"{fid}: timeline must be present for F45 event diff")
        for i, event in enumerate(timeline):
            if not isinstance(event, dict):
                raise ValueError(f"{fid}: timeline[{i}] must be an object")
            for key in ("turn", "time", "actor", "type", "name", "values"):
                if key not in event:
                    raise ValueError(f"{fid}: timeline[{i}] missing {key}")
            if not isinstance(event["values"], list):
                raise ValueError(f"{fid}: timeline[{i}].values must be an array")
        canonical = json.dumps(timeline, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        actual_trace_sha = hashlib.sha256(canonical).hexdigest()
        if actual_trace_sha != r["traceSha256"]:
            raise ValueError(f"{fid}: traceSha256 does not match timeline content")
    return [by_id[fid] for fid in REQUIRED_IDS]


def kotlin_row(r: dict) -> str:
    winner = "null" if r.get("winner") is None else str(int(r["winner"]))
    return (
        "        PinnedExternalBattleResult(\n"
        f"            fixtureId = \"{r['fixtureId']}\",\n"
        f"            battleRatingA = {int(r['battleRatingA'])}, battleRatingB = {int(r['battleRatingB'])},\n"
        f"            winner = {winner}, turns = {int(r['turns'])},\n"
        f"            sourceCommitSha = \"{EXPECTED_SHA}\",\n"
        f"            sourceSiteVersion = \"{EXPECTED_VERSION}\",\n"
        f"            traceSha256 = \"{r['traceSha256']}\"\n"
        "        ),"
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("export_json", type=pathlib.Path)
    ap.add_argument("--kotlin", required=True, type=pathlib.Path)
    ap.add_argument("--evidence-dir", required=True, type=pathlib.Path)
    args = ap.parse_args()

    raw = args.export_json.read_bytes()
    payload = json.loads(raw)
    rows = validate(payload)

    src = args.kotlin.read_text(encoding="utf-8")
    if START not in src or END not in src:
        raise ValueError("Kotlin generation markers missing")
    generated = "\n".join(kotlin_row(r) for r in rows)
    before, rest = src.split(START, 1)
    _, after = rest.split(END, 1)
    args.kotlin.write_text(before + START + "\n" + generated + "\n" + END + after, encoding="utf-8")

    args.evidence_dir.mkdir(parents=True, exist_ok=True)
    canonical_path = args.evidence_dir / "pvpoke-1.40.1.3-a93147b-results.json"
    canonical_path.write_bytes(raw)
    sha = hashlib.sha256(raw).hexdigest()
    (args.evidence_dir / "pvpoke-1.40.1.3-a93147b-results.sha256").write_text(f"{sha}  {canonical_path.name}\n", encoding="utf-8")
    print("PINNED_RESULTS_IMPORTED")
    for r in rows:
        print(f"{r['fixtureId']} rating={r['battleRatingA']}/{r['battleRatingB']} turns={r['turns']} trace={r['traceSha256'][:12]}")
    print(f"evidence_sha256={sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
