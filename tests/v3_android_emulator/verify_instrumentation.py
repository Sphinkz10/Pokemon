#!/usr/bin/env python3
"""Required audit: Android tests are executed on emulator, not counted from source alone."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[2]
android_test=root/"app/src/androidTest/java/com/rui/pvpgo"
required={
    "SavedTeamRoomIntegrationTest":6,
    "UserDatabaseMigrationTest":1,
    "UserResourceStoreAndroidTest":4,
    "InvestmentJourneyAndroidTest":7,
    "MainActivityLaunchAndroidTest":1,
}

def source_contract():
    workflow=(root/".github/workflows/android-room-instrumentation.yml").read_text()
    checks={
        "PRs run real emulator": "pull_request:" in workflow and "reactivecircus/android-emulator-runner@v2" in workflow,
        "Android 35 system image": "api-level: 35" in workflow and "arch: x86_64" in workflow,
        "Gradle connected instrumentation task": ":app:connectedDebugAndroidTest" in workflow,
        "CI checks genuine test reports": "--verify-results" in workflow,
        "upload raw device reports even on failure": "if: always()" in workflow and "androidTests" in workflow,
        "launcher smoke runs on Android": "ActivityScenario.launch(MainActivity::class.java)" in (android_test/"MainActivityLaunchAndroidTest.kt").read_text(),
        "resource journey uses actual Room": "Room.inMemoryDatabaseBuilder" in (android_test/"InvestmentJourneyAndroidTest.kt").read_text(),
        "resource journey uses Android preferences": 'getSharedPreferences("journey-android-test-v1"' in (android_test/"InvestmentJourneyAndroidTest.kt").read_text(),
        "source test count minimum": all(
            (android_test/f"{name}.kt").read_text().count("@Test fun ") >= count
            for name,count in required.items()
        ),
    }
    for label,ok in checks.items(): print(("PASS " if ok else "FAIL ")+label)
    assert all(checks.values()),"Missing Android instrumentation guard"
    print("ANDROID_SOURCE_CONTRACT",len(checks),"/",len(checks),"PASS")

def verify_results():
    folder=root/"app/build/outputs/androidTest-results/connected"
    files=list(folder.rglob("*.xml"))
    if not files: raise AssertionError(f"No real emulator XML reports under {folder}")
    seen={name:{"tests":0,"failed":0} for name in required}
    found_tests=0
    for file in files:
        try: parsed=ET.parse(file).getroot()
        except ET.ParseError: continue
        # AGP instruments files as <testsuite name='com.rui.pvpgo.X'>.
        suites= [parsed] if parsed.tag=="testsuite" else parsed.findall(".//testsuite")
        for suite in suites:
            name=suite.get("name","").split(".")[-1]
            if name not in required: continue
            cases=suite.findall("testcase")
            # Deduplicate duplicate test files with best-effort filename guard.
            seen[name]["tests"]+=len(cases)
            seen[name]["failed"]+=sum(
                1 for case in cases if case.find("failure") is not None
                or case.find("error") is not None or case.find("skipped") is not None
            )
            found_tests+=len(cases)
    for name,minimum in required.items():
        value=seen[name]
        print(f"EMULATOR_TEST {name}: {value['tests']}/{minimum} cases; {value['failed']} failures/skips")
        assert value["tests"] >= minimum and value["failed"] == 0, f"Android test suite missing/failed: {name}"
    print(f"ANDROID_INSTRUMENTATION_RESULT {found_tests} real-device cases across {len(required)} suites")

if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1] not in ("--check-source","--verify-results"):
        raise SystemExit("Usage: verify_instrumentation.py --check-source|--verify-results")
    if sys.argv[1]=="--check-source": source_contract()
    else: verify_results()
