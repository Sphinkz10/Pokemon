#!/usr/bin/env bash
# Execute with an already-running Android emulator. Never uninstall / clear data.
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
ROOT="$PWD"
OLD_APK="$ROOT/../previous/app/build/outputs/apk/debug/app-debug.apk"
NEW_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
REPORT="$ROOT/app/build/upgrade-reports"
mkdir -p "$REPORT"
ADB="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK is required}}/platform-tools/adb"
AAPT="${ANDROID_HOME:-${ANDROID_SDK_ROOT}}/build-tools/35.0.0/aapt"
APKSIGNER="${ANDROID_HOME:-${ANDROID_SDK_ROOT}}/build-tools/35.0.0/apksigner"
PACKAGE="com.rui.pvpgo.installtestv190"
TEST_RUNNER="${PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.rui.pvpgo.UpgradePersistenceAndroidTest"

for f in "$OLD_APK" "$NEW_APK" "$TEST_APK"; do
  [[ -s "$f" ]] || { echo "MISSING APK $f"; exit 1; }
done
get_version() {
  "$AAPT" dump badging "$1" | sed -n "s/^package: name='[^']*' versionCode='\\([0-9]*\\)'.*/\\1/p" | head -1
}
get_package() {
  "$AAPT" dump badging "$1" | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p" | head -1
}
get_cert() {
  "$APKSIGNER" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1
}
OLD_VERSION="$(get_version "$OLD_APK")"
NEW_VERSION="$(get_version "$NEW_APK")"
OLD_CERT="$(get_cert "$OLD_APK")"
NEW_CERT="$(get_cert "$NEW_APK")"
TEST_CERT="$(get_cert "$TEST_APK")"
[[ -n "$OLD_VERSION" && -n "$NEW_VERSION" && -n "$OLD_CERT" ]] || exit 1
[[ "$(get_package "$OLD_APK")" == "$PACKAGE" ]] || exit 1
[[ "$(get_package "$NEW_APK")" == "$PACKAGE" ]] || exit 1
[[ "$OLD_CERT" == "$NEW_CERT" && "$NEW_CERT" == "$TEST_CERT" ]] || {
  echo "Mismatched debug signing certificates; cannot verify in-place upgrade"
  exit 1
}
(( NEW_VERSION >= OLD_VERSION )) || {
  echo "New APK version is LOWER: $OLD_VERSION -> $NEW_VERSION"
  exit 1
}
if cmp -s "$OLD_APK" "$NEW_APK"; then
  echo "Old and new APK are byte-identical; no replacement under test"
  exit 1
fi
{
  echo "PACKAGE=$PACKAGE"
  echo "OLD_VERSION=$OLD_VERSION"
  echo "NEW_VERSION=$NEW_VERSION"
  echo "SAME_SIGNER_SHA256=$OLD_CERT"
  sha256sum "$OLD_APK" "$NEW_APK" "$TEST_APK"
} | tee "$REPORT/apk-identity.txt"

run_phase() {
  local method="$1" expected_version="$2" label="$3"
  echo "PHASE $label; expected version=$expected_version"
  local output
  output=$("$ADB" shell am instrument -w \
    -e class "${TEST_CLASS}#${method}" \
    -e expectedVersionCode "$expected_version" \
    "$TEST_RUNNER" | tr -d '\r') || {
       echo "$output" | tee "$REPORT/$label.txt"
       exit 1
    }
  printf '%s\n' "$output" | tee "$REPORT/$label.txt"
  if ! grep -Fq "OK (1 test)" "$REPORT/$label.txt"; then
    echo "No successful Android instrumentation proof for $label"
    exit 1
  fi
}

"$ADB" wait-for-device
echo "PHASE 1: install OLD APK and NEW instrumentation (same signing cert)"
"$ADB" install -r "$OLD_APK" | tee "$REPORT/install-old.txt"
"$ADB" install -r "$TEST_APK" | tee "$REPORT/install-test.txt"
run_phase seedPriorVersionAndConfirmDiskWrites "$OLD_VERSION" seed-before-upgrade

echo "PHASE 2: open genuine old MainActivity, then kill process and re-check"
"$ADB" shell am start -n "$PACKAGE/com.rui.pvpgo.MainActivity" | tee "$REPORT/launch-old.txt"
"$ADB" shell pidof "$PACKAGE" | tee "$REPORT/pid-before-kill.txt"
grep -Eq '[0-9]' "$REPORT/pid-before-kill.txt" || {
  echo "Activity did not start a process; cannot claim process kill"
  exit 1
}
"$ADB" shell am force-stop "$PACKAGE"
if "$ADB" shell pidof "$PACKAGE" | grep -Eq '[0-9]'; then
  echo "Old Android process still running after force-stop"
  exit 1
fi
echo "Verified previous app process was killed" | tee "$REPORT/process-killed.txt"
run_phase verifyAfterRealForceStopOnPreviousVersion "$OLD_VERSION" verify-after-process-death

echo "PHASE 3: update SAME installed package without uninstall or data clear"
"$ADB" shell am force-stop "$PACKAGE"
"$ADB" install -r "$NEW_APK" | tee "$REPORT/install-upgrade.txt"
grep -Fq "Success" "$REPORT/install-upgrade.txt" || {
   echo "adb did not confirm APK replacement"
   exit 1
}
run_phase verifyAfterSignedApkReplacement "$NEW_VERSION" verify-after-upgrade
echo "APK_UPGRADE_VALIDATED old=$OLD_VERSION new=$NEW_VERSION; seeded data and resources survived" | tee "$REPORT/success.txt"
