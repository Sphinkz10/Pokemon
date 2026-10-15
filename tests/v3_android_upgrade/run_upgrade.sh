#!/usr/bin/env bash
# Two different actual APKs, one unchanged debug signing identity, one
# persistent Android sandbox. Any failed action/assertion stops the gate.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
REPORT="$ROOT/app/build/reports/pr25-apk-upgrade"
mkdir -p "$REPORT"
BASELINE_SHA="4d1dbeb739253fbaf0a5625958285328be9ab5a6"
PACKAGE="com.rui.pvpgo.installtestv190"
TEST_PACKAGE="${PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"
CLASS="com.rui.pvpgo.ActualApkUpgradeAndroidTest#actualSameSignatureApkUpgradeRetainsUserData"
BASELINE_DIR="${RUNNER_TEMP:-/tmp}/pokemon-pr24-baseline"
APK_CANDIDATE="${RUNNER_TEMP:-/tmp}/pokemon-pr25-candidate.apk"
APK_TEST="${RUNNER_TEMP:-/tmp}/pokemon-pr25-androidTest.apk"

echo "UPGRADE_GATE baseline=$BASELINE_SHA"
echo "UPGRADE_GATE candidate=$(git rev-parse HEAD)"
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
cp app/build/outputs/apk/debug/app-debug.apk "$APK_CANDIDATE"
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk "$APK_TEST"
git worktree add --detach "$BASELINE_DIR" "$BASELINE_SHA"
# Both APKs are built on THIS SAME job/Android HOME with the SAME auto-generated
# debug keystore; do not use an independent artifact or regenerated signing key.
gradle --no-daemon -p "$BASELINE_DIR" :app:assembleDebug
APK_BASELINE="$BASELINE_DIR/app/build/outputs/apk/debug/app-debug.apk"

SIGNTOOL="$ANDROID_HOME/build-tools/35.0.0/apksigner"
"$SIGNTOOL" verify --print-certs "$APK_BASELINE" > "$REPORT/baseline-signature.txt"
"$SIGNTOOL" verify --print-certs "$APK_CANDIDATE" > "$REPORT/candidate-signature.txt"
CERT_BASE="$(grep -m1 'Signer #1 certificate SHA-256 digest:' "$REPORT/baseline-signature.txt" | awk '{print $NF}')"
CERT_NEW="$(grep -m1 'Signer #1 certificate SHA-256 digest:' "$REPORT/candidate-signature.txt" | awk '{print $NF}')"
test -n "$CERT_BASE" && test "$CERT_BASE" = "$CERT_NEW"
echo "UPGRADE_SIGNING_MATCH $CERT_NEW" | tee "$REPORT/signing-match.txt"
sha256sum "$APK_BASELINE" "$APK_CANDIDATE" > "$REPORT/apk-hashes.txt"
test "$(cut -d' ' -f1 "$REPORT/apk-hashes.txt" | sort -u | wc -l)" -eq 2

assert_version() {
  local expected="$1"
  adb shell dumpsys package "$PACKAGE" > "$REPORT/installed-$expected.txt"
  grep -q "versionCode=$expected " "$REPORT/installed-$expected.txt"
  echo "UPGRADE_INSTALLED_VERSION $expected"
}
run_stage() {
  local stage="$1"
  adb shell am instrument -w -r \
    -e upgrade_stage "$stage" \
    -e class "$CLASS" \
    "$TEST_PACKAGE" | tee "$REPORT/stage-$stage.txt"
  grep -q '^OK (1 test)' "$REPORT/stage-$stage.txt"
  grep -q '^INSTRUMENTATION_CODE: -1' "$REPORT/stage-$stage.txt"
  echo "UPGRADE_STAGE_PASS $stage"
}

adb wait-for-device
adb install "$APK_BASELINE"
assert_version 58
# Instrumentation compiled from PR25 runs against compatible pre-upgrade
# runtime classes from the PR24 APK; tests all use shared v2 Room schema.
adb install "$APK_TEST"
run_stage seed_baseline
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MainActivity" | tee "$REPORT/relaunch-before-upgrade.txt"
run_stage after_force_stop
adb shell am force-stop "$PACKAGE"
# "-r" expressly PRESERVES userdata and the installed package sandbox.
adb install -r "$APK_CANDIDATE" | tee "$REPORT/install-replace.txt"
assert_version 59
adb shell am start -W -n "$PACKAGE/.MainActivity" | tee "$REPORT/relaunch-after-upgrade.txt"
run_stage after_upgrade
echo "UPGRADE_GATE_PASS baseline=58 candidate=59 seed=1 process_relaunch=1 upgrade=1"
