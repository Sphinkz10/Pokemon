#!/usr/bin/env python3
"""Create a compact PvPoke-compatible seed JSON for optional offline bundling.

Usage:
  python tools/import_pvpoke.py pokemon.json app/src/main/assets/pokemon_seed.json

The output keeps the same field names the Android parser expects, including
PvP movepools and second-move Stardust cost so offline move analysis still works.
"""
import json
import sys
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: import_pvpoke.py INPUT_POKEMON_JSON OUTPUT_JSON")

src, dst = map(Path, sys.argv[1:])
data = json.loads(src.read_text(encoding="utf-8"))
rows = []
for p in data:
    bs = p.get("baseStats") or {}
    if not all(k in bs for k in ("atk", "def", "hp")) or not p.get("released", True):
        continue
    row = {
        "dex": p.get("dex", 0),
        "speciesName": p.get("speciesName", p.get("speciesId", "Unknown")),
        "speciesId": p.get("speciesId", ""),
        "baseStats": {"atk": bs["atk"], "def": bs["def"], "hp": bs["hp"]},
        "types": p.get("types", []),
        "tags": p.get("tags", []),
        "fastMoves": p.get("fastMoves", []),
        "chargedMoves": p.get("chargedMoves", []),
        "eliteMoves": p.get("eliteMoves", []),
        "thirdMoveCost": p.get("thirdMoveCost"),
        "released": True,
    }
    if p.get("nicknames"):
        row["nicknames"] = p["nicknames"]
    if p.get("searchPriority") is not None:
        row["searchPriority"] = p["searchPriority"]
    if p.get("family"):
        row["family"] = p["family"]
    rows.append(row)

dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text(json.dumps(rows, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"wrote {len(rows)} released species/forms to {dst}")
