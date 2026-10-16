#!/usr/bin/env python3
"""PR25: fail CI unless same-signature APK replacement genuinely ran."""
from pathlib import Path
import re
import sys

root=Path(__file__).resolve().parents[2]
script=(root/"tests/v3_apk_upgrade/verify_upgrade.sh").read_text()
workflow=(root/".github/workflows/android-apk-upgrade.yml").read_text()
emulator=(root/".github/workflows/android-room-instrumentation.yml").read_text()
test=(root/"app/src/androidTest/java/com/rui/pvpgo/ApkUpgradePersistenceAndroidTest.kt").read_text()
gradle=(root/"app/build.gradle.kts").read_text()
proof=Path(__import__("os").environ.get("RUNNER_TEMP","/tmp"))/"pokemon-pr25-upgrade/upgrade-proof.txt"
BASELINE="4d1dbeb739253fbaf0a5625958285328be9ab5a6"

def verify_source():
    checks={
        "real version bump 58 -> 59": "versionCode = 59" in gradle and 'versionName = "1.59.0-dev"' in gradle,
        "same diagnostic package suffix": 'applicationIdSuffix = ".installtestv190"' in gradle and 'PKG="com.rui.pvpgo.installtestv190"' in script,
        "pr24 source SHA immutable baseline": BASELINE in script,
        "baseline is actually rebuilt from commit source": "worktree add --detach" in script and "baseline-worktree" in script,
        "both app versions built on same runner": script.count(":app:assembleDebug")>=2 and ":app:assembleDebugAndroidTest" in script,
        "identical signature must be checked": "cert_digest" in script and '$(cert_digest "$EVIDENCE/baseline.apk")' in script,
        "strict version comparisons": "versionCode='58'" in script and "versionCode='59'" in script,
        "two actual install-r calls": script.count('adb install -r "$EVIDENCE/')>=3,
        "real process forced to stop": script.count('adb shell am force-stop "$PKG"')>=3,
        "no app data wipe in implementation": not any(re.search(pattern, script) for pattern in [
            r"adb\s+uninstall",r"(?m)^\s*pm\s+clear\b",r"adb\s+shell\s+rm\b",r"deleteDatabase\s*\("
        ]),
        "explicit three instrumented test executions": "require_success seedExistingApp" in script and script.count("require_success verifyPreservedCollectionResources") == 2,
        "JUnit result enforced not merely logged": '"OK (1 test)"' in script and "return 1" in script,
        "seed and verify real user.db": all(word in test for word in [
            '"user.db"', "fun seedExistingApp()", "fun verifyPreservedCollectionResources()",
            "dao.upsertOwned(owned())", "dao.upsertSavedTeam(team())",
            "dao.upsertBuildPlan(plan())", "dao.upsertInbox(candidate())",
            "dao.upsertBattleRecord(battle())"
        ]),
        "source verifies unknown vs zero": 'assertNull(balances.eliteChargedTm)' in test and 'assertEquals(0, balances.candy)' in test,
        "dedicated PR gate mandatory": "pull_request:" in workflow and "reactivecircus/android-emulator-runner@v2" in workflow and "verify_contract.py --evidence" in workflow,
        "regular suite excludes two-stage fixture": "android.testInstrumentationRunnerArguments.notClass=com.rui.pvpgo.ApkUpgradePersistenceAndroidTest" in emulator,
    }
    for label,ok in checks.items(): print(("PASS " if ok else "FAIL ")+label)
    assert all(checks.values()),"APK upgrade proof contract failed"
    print(f"UPGRADE_SOURCE_CONTRACT {len(checks)}/{len(checks)} PASS")

def verify_evidence():
    assert proof.is_file(), f"Missing device report: {proof}"
    s=proof.read_text(errors="replace")
    required=[
        "PASS same package, same signer, strictly increasing 58 -> 59 versionCode",
        "PASS installed versionCode=58",
        "PASS installed versionCode=59",
        "PASS seedExistingApp — Android instrumentation: exactly 1 test",
        "PASS verifyPreservedCollectionResources — Android instrumentation: exactly 1 test",
        "UPGRADE_END_TO_END_PASS baseline 58 -> candidate 59;"
    ]
    for token in required:
        if token not in s: raise AssertionError(f"Missing emulator evidence: {token}")
    if s.count("PASS verifyPreservedCollectionResources — Android instrumentation: exactly 1 test")!=2:
        raise AssertionError("Expected both separate before/after upgrade verification invocations")
    assert "FAILED " not in s, "An upgrade stage reported failure"
    sigs=[line.split()[-1] for line in s.splitlines() if line.startswith(("BASELINE_SIGNATURE ","CANDIDATE_SIGNATURE "))]
    assert len(sigs)==2 and all(len(sig)==64 for sig in sigs) and sigs[0]==sigs[1], "Signer identity mismatch"
    assert "BASELINE package: name='com.rui.pvpgo.installtestv190' versionCode='58'" in s
    assert "CANDIDATE package: name='com.rui.pvpgo.installtestv190' versionCode='59'" in s
    assert s.count("OK (1 test)")>=3, "Three instrument invocations not supported by actual JUnit output"
    print("UPGRADE_DEVICE_EVIDENCE PASS | two versions | identical signer | force-stop | no data wipe | 3 actual instrumented executions")

if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1] not in ("--source","--evidence"):
        raise SystemExit("Usage: verify_contract.py --source|--evidence")
    if sys.argv[1]=="--source": verify_source()
    else: verify_evidence()
