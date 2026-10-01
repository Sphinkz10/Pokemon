#!/usr/bin/env python3
"""Verify an exact local PvPoke checkout and generate a proof-bound browser runner."""
from __future__ import annotations
import argparse, json, pathlib, re, subprocess, sys

EXPECTED_SHA = "a93147bf1f2e829758958bfb5b37e56bbadc9678"
EXPECTED_VERSION = "1.40.1.3"


def run_git(root: pathlib.Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def detect_site_version(root: pathlib.Path) -> str:
    # Use git grep so ignored/build artifacts cannot spoof the proof.
    proc = subprocess.run(
        ["git", "-C", str(root), "grep", "-n", EXPECTED_VERSION, "--", "*.php", "*.html", "*.js"],
        text=True, capture_output=True
    )
    if proc.returncode not in (0, 1):
        raise RuntimeError(proc.stderr.strip() or "git grep failed")
    if EXPECTED_VERSION not in proc.stdout:
        raise RuntimeError(f"site version {EXPECTED_VERSION} not found in tracked source")
    return EXPECTED_VERSION


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--checkout", required=True, type=pathlib.Path)
    ap.add_argument("--runner-template", default=pathlib.Path(__file__).with_name("pvpoke_same_revision_runner.js"), type=pathlib.Path)
    ap.add_argument("--out-dir", required=True, type=pathlib.Path)
    args = ap.parse_args()

    root = args.checkout.resolve()
    sha = run_git(root, "rev-parse", "HEAD")
    if sha != EXPECTED_SHA:
        raise SystemExit(f"REFUSED: checkout SHA {sha} != pinned {EXPECTED_SHA}")
    dirty = run_git(root, "status", "--porcelain", "--untracked-files=no")
    if dirty:
        raise SystemExit("REFUSED: tracked files in PvPoke checkout are modified")
    version = detect_site_version(root)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    proof = {
        "schemaVersion": 1,
        "repository": "https://github.com/pvpoke/pvpoke",
        "commitSha": sha,
        "siteVersion": version,
        "verifiedBy": "verify_pvpoke_checkout.py"
    }
    proof_path = args.out_dir / "pvpoke_pin_proof.json"
    proof_path.write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")

    template = args.runner_template.read_text(encoding="utf-8")
    prefix = "window.__POKEMON_PVP_PIN_PROOF__ = " + json.dumps(proof, separators=(",", ":")) + ";\n"
    runner_path = args.out_dir / "pvpoke_same_revision_runner.verified.js"
    runner_path.write_text(prefix + template, encoding="utf-8")

    print(f"PVPOKE_PIN_OK {version}@{sha}")
    print(proof_path)
    print(runner_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
