#!/usr/bin/env python3
"""PR #25: guard against false APK-upgrade claims and destructive 'tests'."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
workflow = (root / ".github/workflows/android-upgrade-in-place.yml").read_text()
bash = (root / ".github/scripts/verify-android-upgrade.sh").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
android = (root / "app/src/upgradeTest/java/com/rui/pvpgo/UpgradePersistenceAndroidTest.kt").read_text()
legacy = (root / "tests/v1_58_navigation_lifecycle/verify_navigation.py").read_text()
independent_suite = (root / "tests/v3_android_emulator/verify_instrumentation.py").read_text()

checks = {
    "PR checks out separate previous and candidate": all(
        item in workflow for item in [
            "github.event.pull_request.base.sha",
            "github.event.pull_request.head.sha",
            "path: previous", "path: current"
        ]
    ),
    "both signed in same runner Gradle home": all(item in workflow for item in [
        "working-directory: previous", "working-directory: current",
        "gradle-version: '8.9'", ":app:assembleDebug",
        ":app:assembleDebugAndroidTest -PupgradeSuite=true"
    ]),
    "three-phase Android API35 emulator": all(item in workflow for item in [
        "reactivecircus/android-emulator-runner@v2", "api-level: 35",
        "arch: x86_64", "verify-android-upgrade.sh"
    ]),
    "no deletion or data reset": not any(
        action in bash for action in ["adb uninstall", "pm clear", "rm -rf /data", "adb shell pm uninstall"]
    ),
    "same exact package in both APK": all(
        item in bash for item in [
            '"$OLD_APK"', '"$NEW_APK"', 'get_package',
            'com.rui.pvpgo.installtestv190'
        ]
    ),
    "signing certificate equality checked": all(
        item in bash for item in [
            "get_cert", "OLD_CERT", "NEW_CERT", "TEST_CERT",
            "OLD_CERT\" == \"$NEW_CERT", "NEW_CERT\" == \"$TEST_CERT"
        ]
    ),
    "reinstallation preserves data": bash.count('"$ADB" install -r') >= 3,
    "real running process is killed and absence checked": all(
        item in bash for item in [
            'shell am start -n', 'shell pidof', 'shell am force-stop',
            'process-killed.txt'
        ]
    ),
    "actual outcomes checked by instrumentation": all(item in bash for item in [
        'am instrument -w', 'expectedVersionCode',
        'seedPriorVersionAndConfirmDiskWrites', 'verifyAfterRealForceStopOnPreviousVersion',
        'verifyAfterSignedApkReplacement', 'OK (1 test)'
    ]),
    "all phases have retained CI evidence": all(item in workflow + bash for item in [
        'android-signed-upgrade-preserved-user-data', 'verify-after-upgrade',
        'apk-identity.txt', 'success.txt', 'if: always()'
    ]),
    "old and candidate APK version codes checked": all(item in bash for item in [
        "OLD_VERSION", "NEW_VERSION", "get_version",
        "NEW_VERSION >= OLD_VERSION"
    ]),
    "versioned candidate diagnostic install": all(item in gradle for item in [
        'versionCode = 59', 'versionName = "1.59.0-dev"',
        'applicationIdSuffix = ".installtestv190"'
    ]),
    "upgrade-only instrumented methods excluded from ordinary suite": all(
        item in gradle for item in [
            'gradleProperty("upgradeSuite")', 'java.setSrcDirs',
            'src/upgradeTest/java'
        ]
    ) and "UpgradePersistenceAndroidTest" not in independent_suite,
    "seed and verify SQLite plus resources under target app": all(
        item in android for item in [
            'getInstrumentation().targetContext',
            'PvpUserDatabase.get(context)',
            'UserResourceStore.get(context).save(',
            'UserResourceStore.get(context).load(family)',
            'context.getDatabasePath("user.db")',
            'assertBaselineContents()',
            'assertNull(second.cp)', 'assertNull(resources.eliteFastTm)',
            'dao.observePendingInbox().first()', 'dao.observeBattleRecords().first()'
        ]
    ),
    "three explicit phases never treated as one plain JVM test":
        android.count("@Test fun ") == 3,
    "historical lifecycle code remains checked and version guard not pinned":
        "v158 lifecycle contract survives upgrades" in legacy
        and ">= 58" in legacy,
}
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
print(f"APK_UPGRADE_SOURCE_CONTRACT {sum(checks.values())}/{len(checks)}")
assert all(checks.values()), "Potential unsafe/incomplete signed APK replacement gate"
