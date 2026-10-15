# Pokémon Companion v3 — PR #25: actual Android APK update retaining saved data

## Objective and threat model
The PR #24 named-file SQLite tests proved that data survives database close/reopen. They did **not** prove that data survives upgrading the Android app, nor killing and relaunching the process. This PR adds a **required, separately visible GitHub Actions workflow** that actually performs the two operations on an Android 35 emulator.

## Exact baseline and candidate
- Baseline: immutable merged PR #24 commit `4d1dbeb739253fbaf0a5625958285328be9ab5a6`, app `versionCode 58`, `1.58.0-dev`.
- Candidate: current branch, **`versionCode 59`**, `1.59.0-dev`.
- Both use **the same** diagnostic application ID `com.rui.pvpgo.installtestv190`. They are compiled on the **same GitHub runner**, with the **same generated debug signing keystore**.
- `apksigner verify --print-certs` calculates both signatures; mismatch **fails** CI. The two APK file hashes must **differ**; a nominal same-file install cannot pass.

## Three distinct instrumented stages
1. **Before update (v58):** install previous app APK via ADB; install the v59 test-only APK against the compatible v58 app. On the real production-named `user.db` Room database, record a favorite shiny with IV 0/15/15, CP 499 and level 20, save a team and a PvP plan. Save real `pvp_user_resources_v1` preferences with Stardust 54321, 12 family Candy, explicitly zero XL, and UNKNOWN fast TM. Verify all persisted values and native package versionCode 58.
2. **After process stop/restart (still v58):** `adb shell am force-stop` the real app, `adb shell am start -W` the real `MainActivity`, and then execute a fresh instrumented check with package version 58. Every original database row and resource balance must still match. This is actual process termination/relaunch, **not** `ActivityScenario.recreate`.
3. **After APK replacement (v59):** force-stop again, `adb install -r` the new APK **without clearing or uninstalling** the app, confirm native installed `versionCode 59`, start the `MainActivity`, and execute the third independent instrumented check. All data must still match.

## CI truthfulness
- The existing emulator workflow requires **52** executed instrumented tests: the previously validated 51 cases plus the new test's standalone-safe run.
- The **separate upgrade workflow** requires THREE successful AndroidJUnitRunner invocations across two physically installed APK versions. It checks the signed APK fingerprints, hash distinctness, `dumpsys package` version, ADB launch outcomes, `OK (1 test)`, `INSTRUMENTATION_CODE: -1`, plus saved JUnit/test logs. Source checks alone are not accepted.
- The normal debug APK workflow must also be green. Any red workflow blocks merge.
- The CI-generated artifacts include `apk-hashes.txt`, signing fingerprints, installed version dumps, activity launch logs and all three instrumented reports.
- The upgrade test only writes inside the **isolated diagnostic application ID**; it does not access other installed app IDs or any existing phone.

## Limits
An Android 35 x86_64 emulated upgrade proves update behavior for **these two versions, the diagnostic package, the selected fixture and signing job**. It does not prove release signing compatibility, restoring backups, switching package IDs, every possible historical database or Android 13–16 physical devices. It also does not certify all 110 user-defined UX/E2E scenarios. These remain to be mapped and executed individually.

Next: add actual legacy-release signing migration plan where possible, release-channel key handling (never commit a private keystore), repeat upgrade on multiple API levels, then performance/a11y/UI flow tests for the collection of ~1000 species.
