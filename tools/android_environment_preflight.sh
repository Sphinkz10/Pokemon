#!/usr/bin/env bash
# Offline-only Android build prerequisite report; no installation, downloads or credential access.
set -u
fail=0
line(){ printf '%s\n' "$*"; }
if command -v java >/dev/null 2>&1; then
  line 'JAVA: present'; java -version 2>&1 | head -1
else
  line 'JAVA: MISSING'; fail=1
fi
url=$(sed -n 's/^distributionUrl=//p' gradle/wrapper/gradle-wrapper.properties | sed 's/\\:/:/g')
line "GRADLE_WRAPPER: ${url:-unknown}"
ghome="${GRADLE_USER_HOME:-$HOME/.gradle}"
if find "$ghome/wrapper/dists" -type f -name 'gradle' 2>/dev/null | grep -q .; then
  line 'GRADLE_DISTRIBUTION: cached binary found'
else
  line 'GRADLE_DISTRIBUTION: MISSING (download/cache required)'; fail=1
fi
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ]; then
  for p in "$HOME/Android/Sdk" /opt/android-sdk /usr/local/lib/android/sdk; do
    if [ -d "$p" ]; then sdk="$p"; break; fi
  done
fi
if [ -n "$sdk" ] && [ -f "$sdk/platforms/android-35/android.jar" ]; then
  line "ANDROID_SDK_API35: found at $sdk"
else
  line 'ANDROID_SDK_API35: MISSING (compileSdk 35 required)'; fail=1
fi
if [ "$fail" -eq 0 ]; then
  line 'PREFLIGHT: prerequisites visible; try bash ./gradlew :app:assembleDebug'
else
  line 'PREFLIGHT: BLOCKED-ENV; no APK claim until build and device tests succeed'
fi
exit "$fail"
