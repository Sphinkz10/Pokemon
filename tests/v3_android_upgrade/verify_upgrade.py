#!/usr/bin/env python3
"""Fails closed unless signed APK v58->v59 upgrade really ran on Android."""
from pathlib import Path
import sys
import re

root=Path(__file__).resolve().parents[2]
workflow=root/".github/workflows/android-upgrade-emulator.yml"
script=root/"tests/v3_android_upgrade/run_upgrade.sh"
test_file=root/"app/src/androidTest/java/com/rui/pvpgo/ActualApkUpgradeAndroidTest.kt"
gradle=root/"app/build.gradle.kts"
legacy=root/"tests/v1_58_navigation_lifecycle/verify_navigation.py"
reports=root/"app/build/reports/pr25-apk-upgrade"

def check_source():
    wf=workflow.read_text()
    sh=script.read_text()
    kt=test_file.read_text()
    g=gradle.read_text()
    checks={
        "PR gate actually starts emulator": "pull_request:" in wf and "reactivecircus/android-emulator-runner@v2" in wf,
        "version code upgraded to 59": "versionCode = 59" in g and 'versionName = "1.59.0-dev"' in g,
        "stable debug installation identity": 'applicationIdSuffix = ".installtestv190"' in g,
        "signed v58 pinned by immutable commit": "4d1dbeb739253fbaf0a5625958285328be9ab5a6" in sh,
        "two distinct real APKs built": "git worktree add --detach" in sh and "assembleDebugAndroidTest" in sh,
        "same signing digest is enforced": 'test "$CERT_BASE" = "$CERT_NEW"' in sh and "apksigner" in sh,
        "APK bytes must actually differ": "sha256sum" in sh and "sort -u" in sh,
        "old APK installed without wipe": 'adb install "$APK_BASELINE"' in sh,
        "forced process stopped": 'adb shell am force-stop "$PACKAGE"' in sh,
        "explicit MainActivity relaunches": sh.count('adb shell am start -W -n "$PACKAGE/com.rui.pvpgo.MainActivity"') >= 2,
        "new APK replaced without uninstall": 'adb install -r "$APK_CANDIDATE"' in sh,
        "no destructive reinstall": all(q not in sh for q in ["adb uninstall", "pm clear", "adb shell pm clear", "fallbackToDestructiveMigration"]),
        "three test stages exercised": all(q in sh for q in ["run_stage seed_baseline", "run_stage after_force_stop", "run_stage after_upgrade"]),
        "instrumentation must report one passing case": "'^OK (1 test)'" in sh and "'^INSTRUMENTATION_CODE: -1'" in sh,
        "production user.db is checked": 'PvpUserDatabase.get(context).collectionDao()' in kt,
        "production private resources checked": "UserResourceStore.get(context)" in kt,
        "UNKNOWN is not inferred as zero": "assertNull(balances.fastTm)" in kt and "assertNull(row?.lastVerifiedAtEpochMs)" in kt,
        "both version codes asserted in instrumented Kotlin": "verify(58L)" in kt and "verify(59L)" in kt,
        "historical navigation guard not fixed to v58": "versionCode = 58" not in legacy.read_text(),
        "real results must be verified by CI": "--verify-results" in wf and "if: always()" in wf,
    }
    for k,v in checks.items(): print(("PASS" if v else "FAIL"),k)
    assert all(checks.values()), "APK upgrade gate source is incomplete"
    print("APK_UPGRADE_SOURCE_CONTRACT",len(checks),"/",len(checks),"PASS")

def verify_results():
    assert reports.is_dir(),f"No executed Android upgrade reports: {reports}"
    logs={p.name:p.read_text(errors="replace") for p in reports.glob("*.txt")}
    for stage in ("seed_baseline", "after_force_stop", "after_upgrade"):
        data=logs.get(f"stage-{stage}.txt", "")
        assert re.search(r"^OK \(1 test\)",data,re.MULTILINE),f"Stage {stage} did not pass on Android"
        assert re.search(r"^INSTRUMENTATION_CODE: -1",data,re.MULTILINE),f"Stage {stage} has no success code"
        print("APK_UPGRADE_INSTRUMENTATION_PASS",stage)
    for version in ("58","59"):
        data=logs.get(f"installed-{version}.txt","")
        assert re.search(rf"versionCode={version}\s",data),f"Version {version} not installed on emulator"
        print("APK_UPGRADE_INSTALLED_VERSION",version)
    assert "Starting: Intent" in logs.get("relaunch-before-upgrade.txt",""),"No actual app relaunch before upgrade"
    assert "Starting: Intent" in logs.get("relaunch-after-upgrade.txt",""),"No actual app relaunch after upgrade"
    assert "Success" in logs.get("install-replace.txt",""),"adb install -r did not succeed"
    cert=logs.get("signing-match.txt","").strip()
    assert cert.startswith("UPGRADE_SIGNING_MATCH ") and len(cert.split()[-1])>=64,"No verified signing-match proof"
    hashes=logs.get("apk-hashes.txt","").strip().splitlines()
    assert len(hashes)==2 and len(set(x.split()[0] for x in hashes))==2,"APK builds are not distinct"
    print("APK_UPGRADE_GATE_PASS 58->59; same certificate; process force-stop + launch; DB and resources retained")

if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1] not in ("--check-source","--verify-results"):
        raise SystemExit("Use --check-source or --verify-results")
    (check_source if sys.argv[1]=="--check-source" else verify_results)()
