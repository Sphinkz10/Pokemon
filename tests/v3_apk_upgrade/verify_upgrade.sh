#!/usr/bin/env bash
# PR25: real in-place APK replacement on Android 35 without data clearing.
# Usage: verify_upgrade.sh prepare | execute
set -euo pipefail
ROOT="${GITHUB_WORKSPACE:-$(pwd)}"
EVIDENCE="${RUNNER_TEMP:-/tmp}/pokemon-pr25-upgrade"
BASELINE_SHA="4d1dbeb739253fbaf0a5625958285328be9ab5a6"
PKG="com.rui.pvpgo.installtestv190"
TEST_PKG="${PKG}.test"
TEST_CLASS="com.rui.pvpgo.ApkUpgradePersistenceAndroidTest"
mkdir -p "$EVIDENCE"
LOG="$EVIDENCE/upgrade-proof.txt"

log() { echo "$*" | tee -a "$LOG"; }
apk_meta() {
  "$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$1" | grep '^package:' | head -n1
}
cert_digest() {
  "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$1" \
    | grep 'Signer #1 certificate SHA-256 digest:' | head -n1 | cut -d ':' -f2 | tr -d '[:space:]'
}
require_success() {
  local stage="$1" result
  log "RUNNING $stage"
  result=$(adb shell am instrument -w -r -e class "$TEST_CLASS#$stage" \
    "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" 2>&1) || {
      log "$result"; log "FAILED $stage (instrument returned error)"; return 1;
    }
  log "$result"
  if [[ "$result" != *"OK (1 test)"* ]] || [[ "$result" == *"FAILURES!!!"* ]]; then
    log "FAILED $stage (missing genuine JUnit success marker)"
    return 1
  fi
  log "PASS $stage — Android instrumentation: exactly 1 test"
}
assert_version() {
  local expected="$1" output
  output="$(adb shell dumpsys package "$PKG")"
  if ! grep -Eq "versionCode=${expected}([[:space:]]|$)" <<< "$output"; then
    log "FAILED installed versionCode expected $expected"
    return 1
  fi
  log "PASS installed versionCode=$expected"
}

case "${1:-}" in
prepare)
  : > "$LOG"
  log "PR25 baseline commit $BASELINE_SHA"
  git -C "$ROOT" cat-file -e "${BASELINE_SHA}^{commit}"
  git -C "$ROOT" worktree add --detach "$EVIDENCE/baseline-worktree" "$BASELINE_SHA"
  log "Building baseline version 58 with the runner's debug keystore"
  gradle --no-daemon --project-dir "$EVIDENCE/baseline-worktree" :app:assembleDebug
  cp "$EVIDENCE/baseline-worktree/app/build/outputs/apk/debug/app-debug.apk" "$EVIDENCE/baseline.apk"
  log "Building candidate version 59 and Android test APK with SAME signing key"
  gradle --no-daemon --project-dir "$ROOT" :app:assembleDebug :app:assembleDebugAndroidTest
  cp "$ROOT/app/build/outputs/apk/debug/app-debug.apk" "$EVIDENCE/candidate.apk"
  cp "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "$EVIDENCE/instrumentation.apk"
  log "BASELINE $(apk_meta "$EVIDENCE/baseline.apk")"
  log "CANDIDATE $(apk_meta "$EVIDENCE/candidate.apk")"
  log "BASELINE_SIGNATURE $(cert_digest "$EVIDENCE/baseline.apk")"
  log "CANDIDATE_SIGNATURE $(cert_digest "$EVIDENCE/candidate.apk")"
  for item in baseline candidate; do
    if [[ "$(apk_meta "$EVIDENCE/$item.apk")" != *"name='$PKG'"* ]]; then
      log "FAILED package mismatch: $item"; exit 1
    fi
  done
  [[ "$(apk_meta "$EVIDENCE/baseline.apk")" == *"versionCode='58'"* ]]
  [[ "$(apk_meta "$EVIDENCE/candidate.apk")" == *"versionCode='59'"* ]]
  [[ "$(cert_digest "$EVIDENCE/baseline.apk")" == "$(cert_digest "$EVIDENCE/candidate.apk")" ]]
  test -n "$(cert_digest "$EVIDENCE/baseline.apk")"
  log "PASS same package, same signer, strictly increasing 58 -> 59 versionCode"
  ;;
execute)
  test -s "$EVIDENCE/baseline.apk"
  test -s "$EVIDENCE/candidate.apk"
  test -s "$EVIDENCE/instrumentation.apk"
  adb wait-for-device
  adb install -r "$EVIDENCE/baseline.apk" | tee -a "$LOG"
  assert_version 58
  adb install -r "$EVIDENCE/instrumentation.apk" | tee -a "$LOG"
  require_success seedExistingApp
  log "Force-stopping old app process and relaunching before in-place upgrade"
  adb shell am force-stop "$PKG"
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 | tee -a "$LOG"
  adb shell am force-stop "$PKG"
  require_success verifyPreservedCollectionResources
  log "Replacing existing app IN PLACE (no pm clear, uninstall, or data deletion)"
  adb install -r "$EVIDENCE/candidate.apk" | tee -a "$LOG"
  assert_version 59
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 | tee -a "$LOG"
  adb shell am force-stop "$PKG"
  require_success verifyPreservedCollectionResources
  log "UPGRADE_END_TO_END_PASS baseline 58 -> candidate 59; package/signature preserved; SQLite+SharedPreferences unchanged; 3/3 instrumentation invocations successful"
  ;;
*)
  echo "Usage: $0 prepare|execute" >&2; exit 2
  ;;
esac
