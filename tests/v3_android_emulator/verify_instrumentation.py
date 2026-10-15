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
    "InvestmentComposeUiAndroidTest":12,
    "CollectionNavigationJourneyAndroidTest":5,
    "MainActivityLifecycleA11yAndroidTest":4,
    "PersistentCollectionReopenAndroidTest":8,
    "ResourceResponsiveConstraintsAndroidTest":3,
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
        "Compose UI harness included": 'androidTestImplementation("androidx.compose.ui:ui-test-junit4")' in (root/"app/build.gradle.kts").read_text(),
        "resource form exposed with stable UI semantics": all(tag in (root/"app/src/main/java/com/rui/pvpgo/UserResourcePanel.kt").read_text() for tag in [
            'testTag("resource-toggle")','testTag("resource-save")','testTag("resource-input-${field.name.lowercase()}")'
        ]),
        "investment target keeps meaningful UI test anchors": all(tag in (root/"app/src/main/java/com/rui/pvpgo/InvestmentTargetPanel.kt").read_text() for tag in [
            'testTag("community-estimate-consent")','testTag("cap-target-button")','testTag("affordable-target-button")'
        ]),
        "UI tests perform real clicks typing clearing": all(token in (android_test/"InvestmentComposeUiAndroidTest.kt").read_text() for token in [
            'createComposeRule()', 'performClick()', 'performTextInput(', 'performTextClearance()', 'assertIsNotEnabled()'
        ]),
        "root app routes are actually exercised": all(token in (android_test/"CollectionNavigationJourneyAndroidTest.kt").read_text() for token in [
            "PvPGoApp(", 'app-tab-collection', 'owned-row-nav-owned-one',
            'species-owned-nav-owned-one', 'pokemon-360-back',
            'Descartar alterações', 'Continuar a editar'
        ]),
        "isolation is an explicit real Room repository": "CollectionRepository.forTesting(database)" in (android_test/"CollectionNavigationJourneyAndroidTest.kt").read_text() and "Room.inMemoryDatabaseBuilder" in (android_test/"CollectionNavigationJourneyAndroidTest.kt").read_text(),
        "app defaults to production catalog": "catalogOverride ?: emptyList()" in (root/"app/src/main/java/com/rui/pvpgo/MainActivity.kt").read_text(),
        "app defaults to production repository": "repositoryOverride ?: CollectionRepository.get(context)" in (root/"app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text(),
        "lifecycle instrumentation runs on real activity": all(
            token in (android_test/"MainActivityLifecycleA11yAndroidTest.kt").read_text()
            for token in [
                "createAndroidComposeRule<MainActivity>()",
                ".scenario.recreate()", "moveToState(Lifecycle.State.CREATED)",
                "SCREEN_ORIENTATION_LANDSCAPE", "assertContentDescriptionEquals"
            ]
        ),
        "navigation exposes accessible selected tab state": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/MainActivity.kt").read_text()
            for token in [".selectable(", "selected = selected", "role = Role.Tab",
                          "contentDescription = tab.label"]
        ),
        "unsaved resources use explicit Android Bundle saver": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/UserResourcePanel.kt").read_text()
            for token in ["resourceDraftSaver:", "Saver(", "rememberSaveable(familyId, saved, saver = resourceDraftSaver)"]
        ),
        "resource UI tests restore state and exercise 200 percent fonts": all(
            token in (android_test/"InvestmentComposeUiAndroidTest.kt").read_text()
            for token in ["StateRestorationTester(rule)", "emulateSavedInstanceStateRestore()",
                          "fontScale = 2f"]
        ),
        "named on-disk Room fixture must survive actual close and reopen": all(
            token in (android_test/"PersistentCollectionReopenAndroidTest.kt").read_text()
            for token in ["Room.databaseBuilder(", "context.deleteDatabase(filename)",
                          ".addMigrations(PvpUserDatabase.MIGRATION_1_2)",
                          "fun wholeOwnedSpecimenSurvivesActualFileCloseAndReopen",
                          "fun largeCollectionCanBeReopenedWithStableIdsAndNoDataLoss"]
        ),
        "responsive form exercises narrow and wide actual Compose constraints": all(
            token in (android_test/"ResourceResponsiveConstraintsAndroidTest.kt").read_text()
            for token in ["widthDp = 320, fontScale = 2f", "widthDp = 390, fontScale = 1.5f",
                          "widthDp = 600, fontScale = 1f", "performScrollTo().performClick()"]
        ),
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
    if not files:
        raise AssertionError(f"No real emulator XML reports under {folder}")
    seen={name:{"tests":0,"failed":0} for name in required}
    unique_cases=set()
    unknown=[]
    for file in files:
        try:
            parsed=ET.parse(file).getroot()
        except ET.ParseError as error:
            raise AssertionError(f"Unreadable real Android XML report: {file}: {error}")
        # Android Gradle Plugin may combine multiple classes under ONE
        # <testsuite name='first suite'>. Always use each individual
        # <testcase classname='com.rui.pvpgo.ActualSuite'> instead.
        for case in parsed.iter("testcase"):
            classname=case.get("classname","")
            name=classname.split(".")[-1]
            if name not in required:
                unknown.append(classname)
                continue
            case_id=(classname,case.get("name",""))
            if case_id in unique_cases:
                raise AssertionError(f"Duplicate Android test case in reports: {case_id}")
            unique_cases.add(case_id)
            seen[name]["tests"]+=1
            if (case.find("failure") is not None or
                case.find("error") is not None or
                case.find("skipped") is not None):
                seen[name]["failed"]+=1
    for name,minimum in required.items():
        value=seen[name]
        print(f"EMULATOR_TEST {name}: {value['tests']}/{minimum} cases; {value['failed']} failures/skips")
        assert value["tests"] >= minimum and value["failed"] == 0, (
            f"Android test suite missing/failed: {name}"
        )
    assert not unknown, f"Unknown instrumented test classes require review: {unknown}"
    assert len(unique_cases)>=sum(required.values()), "Insufficient executed Android cases"
    print(f"ANDROID_INSTRUMENTATION_RESULT {len(unique_cases)} real emulator cases across {len(required)} suites")

if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1] not in ("--check-source","--verify-results"):
        raise SystemExit("Usage: verify_instrumentation.py --check-source|--verify-results")
    if sys.argv[1]=="--check-source": source_contract()
    else: verify_results()
